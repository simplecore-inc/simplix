package dev.simplecore.simplix.messaging.broker.local;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory {@link MessageScheduler} backed by a {@link DelayQueue}.
 *
 * @deprecated since 1.1.1, see {@link MessageScheduler}. This implementation will
 *             be removed in a future major release. Prefer Spring {@code @Scheduled}
 *             or a dedicated scheduling engine for new code.
 */
@Slf4j
@Deprecated(since = "1.1.1", forRemoval = true)
@SuppressWarnings("removal")
public class LocalMessageScheduler implements MessageScheduler {

    private final BrokerStrategy broker;
    private final DelayQueue<DelayedMessage> queue = new DelayQueue<>();
    private final ConcurrentHashMap<String, DelayedMessage> index = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    public LocalMessageScheduler(BrokerStrategy broker) {
        this.broker = broker;
    }

    @Override
    public String publishDelayed(Message<?> message, java.time.Duration delay) {
        String scheduleId = UUID.randomUUID().toString();
        long deliverAtNanos = System.nanoTime() + delay.toNanos();
        byte[] payload = toBytes(message);
        DelayedMessage dm = new DelayedMessage(scheduleId, message.getChannel(),
                payload, message.getHeaders(), deliverAtNanos);
        index.put(scheduleId, dm);
        queue.put(dm);
        return scheduleId;
    }

    @Override
    public boolean cancel(String scheduleId) {
        DelayedMessage dm = index.remove(scheduleId);
        if (dm == null) return false;
        return queue.remove(dm);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = new Thread(this::loop, "local-scheduler");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (worker != null) worker.interrupt();
    }

    private void loop() {
        while (running.get()) {
            try {
                DelayedMessage dm = queue.take();
                if (index.remove(dm.scheduleId) == null) continue;       // already cancelled
                broker.send(dm.channel, dm.payload, dm.headers);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Local scheduler delivery failed: {}", e.getMessage());
            }
        }
    }

    private static byte[] toBytes(Message<?> m) {
        Object p = m.getPayload();
        if (p == null) return new byte[0];
        if (p instanceof byte[] b) return b;
        if (p instanceof String s) return s.getBytes(StandardCharsets.UTF_8);
        throw new IllegalArgumentException("Unsupported payload type " + p.getClass());
    }

    private static final class DelayedMessage implements Delayed {
        final String scheduleId;
        final String channel;
        final byte[] payload;
        final MessageHeaders headers;
        final long deliverAtNanos;

        DelayedMessage(String id, String ch, byte[] p, MessageHeaders h, long t) {
            this.scheduleId = id; this.channel = ch; this.payload = p;
            this.headers = h == null ? MessageHeaders.empty() : h; this.deliverAtNanos = t;
        }

        @Override public long getDelay(TimeUnit unit) {
            return unit.convert(deliverAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }
        @Override public int compareTo(Delayed o) {
            return Long.compare(this.deliverAtNanos, ((DelayedMessage) o).deliverAtNanos);
        }
    }
}
