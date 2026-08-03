package dev.simplecore.simplix.messaging.broker.nats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NATS JetStream-backed delayed message scheduler.
 *
 * <p>Stores pending deliveries in a NATS KV bucket. Keys are formatted as
 * {@code <20-digit-zero-padded-epochMillis>.<scheduleId>} so natural string
 * sort yields time order. A leader-elected single-instance poller picks up
 * due entries and republishes them via the {@link BrokerStrategy}.
 *
 * <p>The {@link MessageHeaders#MESSAGE_ID} header is set to {@code scheduleId}
 * on each delivery so the stream's native deduplication window protects against
 * double-delivery if {@code KV.delete} fails after a successful publish.
 *
 * @deprecated since 1.1.1, see {@link MessageScheduler}. This implementation will
 *             be removed in a future major release.
 */
@Slf4j
@Deprecated(since = "1.1.1", forRemoval = true)
@SuppressWarnings("removal")
public class NatsScheduledMessagePublisher implements MessageScheduler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BrokerStrategy broker;
    private final KeyValue kvBucket;
    private final NatsLeaderElection leader;
    private final Duration pollInterval;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService poller;

    public NatsScheduledMessagePublisher(BrokerStrategy broker,
                                         KeyValue kvBucket,
                                         NatsLeaderElection leader,
                                         Duration pollInterval) {
        this.broker = broker;
        this.kvBucket = kvBucket;
        this.leader = leader;
        this.pollInterval = pollInterval;
    }

    @Override
    public String publishDelayed(Message<?> message, Duration delay) {
        String scheduleId = UUID.randomUUID().toString();
        long deliveryAt = Instant.now().plus(delay).toEpochMilli();
        ScheduledRecord record = new ScheduledRecord(
                scheduleId,
                message.getChannel(),
                deliveryAt,
                Base64.getEncoder().encodeToString(toBytes(message)),
                serializeHeaders(message.getHeaders()));
        String key = String.format("%020d.%s", deliveryAt, scheduleId);
        try {
            kvBucket.put(key, JSON.writeValueAsBytes(record));
            return scheduleId;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to schedule message in NATS KV bucket", e);
        }
    }

    @Override
    public boolean cancel(String scheduleId) {
        try {
            List<String> keys = kvBucket.keys();
            if (keys == null) return false;
            String suffix = "." + scheduleId;
            for (String k : keys) {
                if (k.endsWith(suffix)) {
                    kvBucket.delete(k);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to cancel scheduled message '{}': {}", scheduleId, e.getMessage());
            return false;
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        leader.tryAcquireLeadership();
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nats-scheduler");
            t.setDaemon(true);
            return t;
        });
        poller.scheduleAtFixedRate(this::pollAndRenew,
                pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("NATS scheduled message publisher started [pollInterval={}]", pollInterval);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (poller != null) {
            poller.shutdown();
            try {
                if (!poller.awaitTermination(10, TimeUnit.SECONDS)) {
                    poller.shutdownNow();
                }
            } catch (InterruptedException e) {
                poller.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        leader.releaseLeadership();
        log.info("NATS scheduled message publisher stopped");
    }

    private void pollAndRenew() {
        if (!leader.isLeader()) {
            // Try to take over leadership opportunistically.
            leader.tryAcquireLeadership();
            if (!leader.isLeader()) return;
        } else {
            leader.renew();
        }
        poll();
    }

    /** Package-private for testing. */
    void poll() {
        if (!leader.isLeader()) return;
        try {
            List<String> keys = kvBucket.keys();
            if (keys == null || keys.isEmpty()) return;
            long now = Instant.now().toEpochMilli();
            for (String key : keys) {
                long deliveryAt = parseEpochMillis(key);
                if (deliveryAt > now) break; // keys are sorted by prefix; no earlier entries remain
                deliverOne(key);
            }
        } catch (Exception e) {
            log.warn("NATS scheduler poll failed: {}", e.getMessage());
        }
    }

    private void deliverOne(String key) {
        try {
            KeyValueEntry entry = kvBucket.get(key);
            if (entry == null) return;
            ScheduledRecord r = JSON.readValue(entry.getValue(), ScheduledRecord.class);
            byte[] payload = Base64.getDecoder().decode(r.payloadB64());
            MessageHeaders headers = deserializeHeaders(r.headers())
                    .with(MessageHeaders.MESSAGE_ID, r.scheduleId());
            broker.send(r.channel(), payload, headers);
            kvBucket.delete(key);
        } catch (Exception e) {
            log.warn("Scheduled delivery failed for key '{}', leaving for retry: {}", key, e.getMessage());
        }
    }

    private static long parseEpochMillis(String key) {
        int dot = key.indexOf('.');
        if (dot < 0) return Long.MAX_VALUE;
        try {
            return Long.parseLong(key.substring(0, dot));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static byte[] toBytes(Message<?> m) {
        Object p = m.getPayload();
        if (p == null) return new byte[0];
        if (p instanceof byte[] b) return b;
        if (p instanceof String s) return s.getBytes(StandardCharsets.UTF_8);
        throw new IllegalArgumentException("Unsupported payload type " + p.getClass());
    }

    private static String serializeHeaders(MessageHeaders h) {
        if (h == null || h.isEmpty()) return "";
        try {
            return JSON.writeValueAsString(h.toMap());
        } catch (Exception e) {
            log.warn("Failed to serialize headers for scheduled delivery, dropping headers: {}", e.getMessage());
            return "";
        }
    }

    private static MessageHeaders deserializeHeaders(String s) {
        if (s == null || s.isBlank()) return MessageHeaders.empty();
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = JSON.readValue(s, Map.class);
            return MessageHeaders.of(map);
        } catch (Exception e) {
            log.warn("Failed to deserialize headers for scheduled delivery, using empty headers: {}", e.getMessage());
            return MessageHeaders.empty();
        }
    }

    /** Persisted scheduled-message record; serialized as JSON in the KV bucket value. */
    public record ScheduledRecord(
            @JsonProperty("scheduleId") String scheduleId,
            @JsonProperty("channel") String channel,
            @JsonProperty("deliveryAt") long deliveryAt,
            @JsonProperty("payloadB64") String payloadB64,
            @JsonProperty("headers") String headers
    ) {
        @JsonCreator
        public ScheduledRecord {
        }
    }
}
