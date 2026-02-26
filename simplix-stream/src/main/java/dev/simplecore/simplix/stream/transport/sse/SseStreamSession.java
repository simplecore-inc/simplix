package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE implementation of MessageSender.
 * <p>
 * Wraps an SseEmitter to provide message sending capabilities
 * for Server-Sent Events transport.
 */
@Slf4j
public class SseStreamSession implements MessageSender {

    @Getter
    private final StreamSession session;
    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean active = new AtomicBoolean(true);

    /**
     * Creates a new SSE stream session.
     *
     * @param session      the underlying stream session
     * @param emitter      the SSE emitter
     * @param objectMapper the object mapper for JSON serialization
     */
    public SseStreamSession(StreamSession session, SseEmitter emitter, ObjectMapper objectMapper) {
        this.session = session;
        this.emitter = emitter;
        this.objectMapper = objectMapper;

        setupEmitterCallbacks();
    }

    private void setupEmitterCallbacks() {
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for session: {}", session.getId());
            active.set(false);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for session: {}", session.getId());
            active.set(false);
        });

        emitter.onError(e -> {
            log.debug("SSE connection error for session {}: {}", session.getId(), e.getMessage());
            active.set(false);
        });
    }

    @Override
    public boolean send(StreamMessage message) {
        if (!active.get()) {
            return false;
        }

        try {
            String jsonData = objectMapper.writeValueAsString(message);
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
        } catch (IOException e) {
            log.debug("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            active.set(false);
            return false;
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            try {
                emitter.complete();
                log.debug("SSE session closed: {}", session.getId());
            } catch (Exception e) {
                log.trace("Error completing SSE emitter: {}", e.getMessage());
            }
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
