package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-backed implementation of {@link MessageScheduler}.
 *
 * <p>Messages are stored in a Redis sorted set with the delivery timestamp as the score.
 * A background poller checks for due messages and forwards them to the broker.
 *
 * <p>Usage example:
 * <pre>{@code
 * MessageScheduler scheduler = new RedisScheduledMessagePublisher(
 *     broker, redisTemplate, "pacs:", Duration.ofSeconds(5));
 * scheduler.start();
 *
 * Message<byte[]> message = Message.ofBytes("my-channel", payload);
 * String scheduleId = scheduler.publishDelayed(message, Duration.ofMinutes(5));
 * scheduler.cancel(scheduleId);
 * }</pre>
 *
 * <p>Note: {@link #cancel(String)} performs an O(N) scan of the ZSET. For very large
 * schedule sets, a secondary {@code scheduleId -> memberValue} HSET could be added later.
 *
 * @deprecated since 1.1.1, see {@link MessageScheduler}. This implementation will
 *             be removed in a future major release.
 */
@Slf4j
@Deprecated(since = "1.1.1", forRemoval = true)
@SuppressWarnings("removal")
public class RedisScheduledMessagePublisher implements MessageScheduler {

    private static final String SCHEDULE_KEY_SUFFIX = ":scheduled";
    private static final String FIELD_SEPARATOR = "||";
    private static final int POLL_BATCH_SIZE = 100;

    /**
     * Lua script for atomic fetch-and-remove of due messages from the sorted set.
     * Atomically fetches items with score &lt;= ARGV[1] (limited to ARGV[2] items)
     * and removes them, preventing double-delivery across multiple instances.
     */
    private static final String ATOMIC_POLL_SCRIPT =
            "local items = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2]) " +
            "for _, v in ipairs(items) do redis.call('ZREM', KEYS[1], v) end " +
            "return items";

    private static final DefaultRedisScript<List> POLL_SCRIPT = new DefaultRedisScript<>(ATOMIC_POLL_SCRIPT, List.class);

    private final BrokerStrategy brokerStrategy;
    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration pollInterval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Create a Redis-backed scheduled message publisher.
     *
     * @param brokerStrategy the broker to publish due messages to
     * @param redisTemplate  Redis template for sorted set operations
     * @param keyPrefix      prefix for schedule keys
     * @param pollInterval   how often to check for due messages
     */
    public RedisScheduledMessagePublisher(BrokerStrategy brokerStrategy,
                                          StringRedisTemplate redisTemplate,
                                          String keyPrefix,
                                          Duration pollInterval) {
        this.brokerStrategy = brokerStrategy;
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.pollInterval = pollInterval;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "messaging-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedule a message for delayed delivery.
     *
     * @param message the message to publish later
     * @param delay   how long to delay before publishing
     * @return a unique schedule ID for tracking
     */
    @Override
    public String publishDelayed(Message<?> message, Duration delay) {
        Instant deliveryTime = Instant.now().plus(delay);
        String scheduleId = UUID.randomUUID().toString();

        String channel = message.getChannel();
        byte[] payload = resolvePayload(message);
        String encodedPayload = Base64.getEncoder().encodeToString(payload);
        String headersString = serializeHeaders(message.getHeaders());

        // Store as: scheduleId||channel||base64payload||headers
        String value = scheduleId + FIELD_SEPARATOR + channel + FIELD_SEPARATOR
                + encodedPayload + FIELD_SEPARATOR + headersString;

        String scheduleKey = keyPrefix + "messaging" + SCHEDULE_KEY_SUFFIX;
        redisTemplate.opsForZSet().add(scheduleKey, value, deliveryTime.toEpochMilli());

        log.debug("Scheduled message '{}' for channel '{}' at {} (delay: {})",
                scheduleId, channel, deliveryTime, delay);

        return scheduleId;
    }

    /**
     * Cancel a pending scheduled message by scanning the ZSET and removing the matching entry.
     *
     * <p>The ZSET value format is {@code scheduleId||channel||base64payload||headers}.
     * The first segment is the scheduleId, so this method scans all members and removes
     * the first entry whose value starts with {@code scheduleId||}.
     *
     * @param scheduleId the unique ID returned by {@link #publishDelayed}
     * @return true if the entry was found and removed before delivery; false otherwise
     */
    @Override
    public boolean cancel(String scheduleId) {
        String scheduleKey = keyPrefix + "messaging" + SCHEDULE_KEY_SUFFIX;
        Set<String> all = redisTemplate.opsForZSet().range(scheduleKey, 0, -1);
        if (all == null) return false;
        String prefix = scheduleId + FIELD_SEPARATOR;
        for (String value : all) {
            if (value.startsWith(prefix)) {
                Long removed = redisTemplate.opsForZSet().remove(scheduleKey, value);
                return removed != null && removed > 0;
            }
        }
        return false;
    }

    /**
     * Start the background poller that checks for due messages.
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.scheduleAtFixedRate(this::pollDueMessages,
                    pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Scheduled message publisher started (poll interval: {})", pollInterval);
        }
    }

    /**
     * Stop the background poller and drain any in-flight deliveries.
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Scheduled message publisher stopped");
        }
    }

    @SuppressWarnings("unchecked")
    private void pollDueMessages() {
        if (!running.get()) return;

        try {
            String scheduleKey = keyPrefix + "messaging" + SCHEDULE_KEY_SUFFIX;
            long now = Instant.now().toEpochMilli();

            // Atomic fetch-and-remove via Lua script to prevent double-delivery
            List<String> dueMessages = (List<String>) redisTemplate.execute(
                    POLL_SCRIPT,
                    Collections.singletonList(scheduleKey),
                    String.valueOf(now),
                    String.valueOf(POLL_BATCH_SIZE));

            if (dueMessages == null || dueMessages.isEmpty()) {
                return;
            }

            for (String value : dueMessages) {
                try {
                    deliverScheduledMessage(value);
                } catch (Exception e) {
                    log.warn("Failed to deliver scheduled message: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Scheduled message poll failed: {}", e.getMessage());
        }
    }

    private void deliverScheduledMessage(String value) {
        String[] parts = value.split("\\|\\|", 4);
        if (parts.length < 3) {
            log.warn("Invalid scheduled message format: {}", value);
            return;
        }

        String scheduleId = parts[0];
        String channel = parts[1];
        byte[] payload = Base64.getDecoder().decode(parts[2]);
        MessageHeaders headers = parts.length > 3
                ? deserializeHeaders(parts[3])
                : MessageHeaders.empty();

        brokerStrategy.send(channel, payload, headers);
        log.debug("Delivered scheduled message '{}' to channel '{}'", scheduleId, channel);
    }

    private byte[] resolvePayload(Message<?> message) {
        Object payload = message.getPayload();
        if (payload == null) return new byte[0];
        if (payload instanceof byte[] bytes) return bytes;
        if (payload instanceof String str) return str.getBytes(StandardCharsets.UTF_8);
        throw new IllegalArgumentException("Unsupported payload type: " + payload.getClass().getName());
    }

    private String serializeHeaders(MessageHeaders headers) {
        if (headers.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        headers.toMap().forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private MessageHeaders deserializeHeaders(String headersString) {
        if (headersString == null || headersString.isBlank()) {
            return MessageHeaders.empty();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : headersString.split("&")) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                map.put(
                        URLDecoder.decode(pair.substring(0, eqIdx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eqIdx + 1), StandardCharsets.UTF_8));
            }
        }
        return MessageHeaders.of(map);
    }
}
