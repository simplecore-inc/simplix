package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE implementation of MessageSender.
 * <p>
 * Wraps an SseEmitter to provide message sending capabilities
 * for Server-Sent Events transport.
 * <p>
 * <b>Producer/writer isolation:</b> {@link #send} never touches the socket. It
 * appends to a bounded per-session queue and returns immediately, so a slow
 * client (full TCP send buffer) can never block the calling thread — event
 * handlers, schedulers, and broadcast fan-out loops stay responsive no matter
 * how many sessions are connected. A single-flight drain task on the shared
 * writer executor performs the actual (potentially blocking) {@link SseEmitter}
 * writes, at most one task per session at a time, yielding the writer thread
 * between bounded slices so one busy session cannot monopolize the pool.
 * <p>
 * <b>Slow-client policy:</b> when the queue overflows, the client cannot keep
 * up with the stream and silently dropping messages would desynchronize its
 * incremental state. The session is closed instead — the emitter completes,
 * the browser's EventSource observes the close, reconnects, and re-bootstraps
 * from the snapshot endpoint. A transiently slow client whose queue never
 * fills recovers with no disconnect and no data loss.
 * <p>
 * All emitter interaction (writes and completion) happens on writer tasks;
 * producer threads only enqueue. {@link SseEmitter} methods may block on the
 * container's socket I/O, so keeping them off producer threads is what makes
 * every send path non-blocking.
 */
@Slf4j
public class SseStreamSession implements MessageSender {

    /**
     * Maximum messages written per drain task before it re-submits itself,
     * so concurrent sessions share the writer pool fairly under bursts.
     */
    private static final int DRAIN_SLICE = 64;

    @Getter
    private final StreamSession session;
    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final Executor sendExecutor;
    private final int queueCapacity;

    private final AtomicBoolean active = new AtomicBoolean(true);
    /** Ensures {@link SseEmitter#complete()} is attempted at most once. */
    private final AtomicBoolean emitterFinalized = new AtomicBoolean(false);

    /** Outbound message queue; all access synchronized on the queue itself. */
    private final ArrayDeque<StreamMessage> queue = new ArrayDeque<>();
    /** True while a drain task is scheduled or running; guarded by {@link #queue}. */
    private boolean drainScheduled;
    /** Complete the emitter once the queue is drained; guarded by {@link #queue}. */
    private boolean completeAfterDrain;

    /**
     * Creates a new SSE stream session.
     *
     * @param session       the underlying stream session
     * @param emitter       the SSE emitter
     * @param objectMapper  the object mapper for JSON serialization
     * @param sendExecutor  shared executor that performs the blocking emitter writes
     * @param queueCapacity outbound queue bound; overflow closes the session
     */
    public SseStreamSession(StreamSession session, SseEmitter emitter, ObjectMapper objectMapper,
                            Executor sendExecutor, int queueCapacity) {
        this.session = session;
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.sendExecutor = sendExecutor;
        this.queueCapacity = queueCapacity;

        setupEmitterCallbacks();
    }

    private void setupEmitterCallbacks() {
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for session: {}", session.getId());
            active.set(false);
            emitterFinalized.set(true);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for session: {}", session.getId());
            active.set(false);
            emitterFinalized.set(true);
        });

        emitter.onError(e -> {
            log.debug("SSE connection error for session {}: {}", session.getId(), e.getMessage());
            active.set(false);
            emitterFinalized.set(true);
        });
    }

    /**
     * Enqueue a message for delivery. Non-blocking: returns as soon as the
     * message is queued (or rejected), never waiting on socket I/O.
     *
     * @return true if the message was accepted for delivery; false when the
     *         session is inactive or was just closed due to queue overflow
     */
    @Override
    public boolean send(StreamMessage message) {
        if (!active.get()) {
            return false;
        }

        boolean overflow = false;
        boolean schedule = false;
        synchronized (queue) {
            if (queue.size() >= queueCapacity) {
                overflow = true;
            } else {
                queue.addLast(message);
                if (!drainScheduled) {
                    drainScheduled = true;
                    schedule = true;
                }
            }
        }

        if (overflow) {
            handleOverflow();
            return false;
        }
        if (schedule) {
            scheduleDrain();
        }
        return true;
    }

    /**
     * The client is not draining its connection fast enough to keep up with
     * the stream. Close the session so the client reconnects and re-bootstraps;
     * dropping queued messages silently would leave its incremental state
     * permanently out of sync instead.
     */
    private void handleOverflow() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        log.warn("SSE send queue overflow (capacity={}) for session {} — closing slow connection so the client reconnects",
                queueCapacity, session.getId());

        boolean schedule = false;
        synchronized (queue) {
            queue.clear();
            completeAfterDrain = true;
            if (!drainScheduled) {
                drainScheduled = true;
                schedule = true;
            }
        }
        if (schedule) {
            scheduleDrain();
        }
    }

    private void scheduleDrain() {
        try {
            sendExecutor.execute(this::drain);
        } catch (RejectedExecutionException e) {
            // Executor shut down (application stopping): no writer will run, so
            // finalize inline as a best effort instead of leaving the emitter open.
            synchronized (queue) {
                drainScheduled = false;
                queue.clear();
            }
            active.set(false);
            finalizeEmitter();
        }
    }

    /**
     * Writer task: drains up to {@link #DRAIN_SLICE} messages, then either
     * re-submits itself (queue still non-empty) or clears the single-flight
     * flag. Exactly one drain task exists per session at any time, so writes
     * are strictly FIFO.
     */
    private void drain() {
        for (int i = 0; i < DRAIN_SLICE; i++) {
            StreamMessage message;
            synchronized (queue) {
                message = queue.pollFirst();
                if (message == null) {
                    drainScheduled = false;
                }
            }
            if (message == null) {
                maybeFinalize();
                return;
            }
            if (!writeMessage(message)) {
                failDrain();
                return;
            }
        }

        boolean reschedule;
        synchronized (queue) {
            reschedule = !queue.isEmpty();
            if (!reschedule) {
                drainScheduled = false;
            }
        }
        if (reschedule) {
            scheduleDrain();
        } else {
            maybeFinalize();
        }
    }

    private boolean writeMessage(StreamMessage message) {
        try {
            String jsonData = message.serialize(objectMapper);
            String messageId = message.getSubscriptionKey() != null
                    ? message.getSubscriptionKey() + "-" + message.getTimestamp().toEpochMilli()
                    : String.valueOf(message.getTimestamp().toEpochMilli());

            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .id(messageId)
                    .name(message.getType().name().toLowerCase())
                    .data(jsonData);

            emitter.send(event);
            log.trace("Sent message {} to session {}", messageId, session.getId());
            return true;
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            return false;
        }
    }

    /** A write failed: the connection is broken. Deactivate, drop the backlog, finalize. */
    private void failDrain() {
        active.set(false);
        synchronized (queue) {
            queue.clear();
            drainScheduled = false;
            completeAfterDrain = false;
        }
        finalizeEmitter();
    }

    /**
     * Called by a drain task after it observed an empty queue. Completes the
     * emitter when a close was requested; a send racing in after the queue
     * check re-schedules a drain that will land here again.
     */
    private void maybeFinalize() {
        boolean doComplete;
        synchronized (queue) {
            doComplete = completeAfterDrain && queue.isEmpty() && !drainScheduled;
        }
        if (doComplete) {
            active.set(false);
            finalizeEmitter();
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    /**
     * Close the session gracefully: stop accepting new messages, drain what is
     * already queued, then complete the emitter (on a writer task — emitter
     * methods can block on socket I/O and must stay off caller threads).
     */
    @Override
    public void close() {
        if (!active.compareAndSet(true, false)) {
            // Already inactive. If a drain is still pending it will finalize;
            // otherwise finalize here (no writer can be mid-write anymore).
            boolean drainPending;
            synchronized (queue) {
                drainPending = drainScheduled;
                if (drainPending) {
                    completeAfterDrain = true;
                }
            }
            if (!drainPending) {
                finalizeEmitter();
            }
            return;
        }

        boolean schedule = false;
        synchronized (queue) {
            completeAfterDrain = true;
            if (!drainScheduled) {
                drainScheduled = true;
                schedule = true;
            }
        }
        if (schedule) {
            scheduleDrain();
        }
    }

    private void finalizeEmitter() {
        if (!emitterFinalized.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
            log.debug("SSE session closed: {}", session.getId());
        } catch (RuntimeException e) {
            // complete() typically throws IllegalStateException when the emitter was already
            // finalized (a timeout/disconnect raced this close); the connection is gone either
            // way. Guard against any runtime failure so a single bad session cannot abort a bulk
            // close (e.g. the ContextClosedEvent shutdown sweep). Log at debug, not trace, so
            // shutdown-time closes stay visible.
            log.debug("Error completing SSE emitter for session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Get the session ID.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return session.getId();
    }

    /**
     * Get the user ID.
     *
     * @return the user ID, or null if not authenticated
     */
    public String getUserId() {
        return session.getUserId();
    }

    /**
     * Get the underlying SSE emitter.
     *
     * @return the SSE emitter
     */
    public SseEmitter getEmitter() {
        return emitter;
    }
}
