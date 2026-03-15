package dev.simplecore.simplix.messaging.subscriber;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Message deduplication guard using Redis SET NX with TTL.
 *
 * <p>Ensures that a message is processed at most once per channel by storing
 * a key in Redis with a configurable time-to-live. Subsequent attempts to
 * acquire the same key will fail, preventing duplicate processing.
 *
 * <p>When running without Redis (e.g., with {@code LocalBrokerStrategy}),
 * the guard operates in no-op mode where {@link #tryAcquire} always returns {@code true}.
 */
@Slf4j
public class IdempotentGuard {

    private static final String KEY_PREFIX = "messaging:idempotent:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    /**
     * Create an idempotent guard.
     *
     * @param redisTemplate the Redis template for SET NX operations; {@code null} for no-op mode
     * @param ttl           the time-to-live for deduplication keys
     */
    public IdempotentGuard(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /**
     * Attempt to acquire the deduplication lock for a message.
     *
     * <p>Uses Redis {@code SET NX} with TTL to ensure exactly-once semantics.
     * Returns {@code true} on the first processing attempt and {@code false}
     * for any subsequent duplicate.
     *
     * <p>The key includes the consumer group name to prevent cross-group collisions.
     * When multiple consumer groups subscribe to the same channel (e.g., per-instance
     * groups in local development), each group independently processes every message.
     * Without the group in the key, the first group to process a message would block
     * all other groups via the shared idempotent key.
     *
     * @param channel   the channel name
     * @param group     the consumer group name (empty string if no group)
     * @param messageId the unique message identifier
     * @return {@code true} if this is the first processing attempt; {@code false} if duplicate
     */
    public boolean tryAcquire(String channel, String group, String messageId) {
        if (redisTemplate == null) {
            // No-op mode for local broker or environments without Redis
            return true;
        }

        String key = group.isEmpty()
                ? KEY_PREFIX + channel + ":" + messageId
                : KEY_PREFIX + group + ":" + channel + ":" + messageId;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Idempotent guard acquired for key={}", key);
                return true;
            }
            log.debug("Idempotent guard rejected duplicate for key={}", key);
            return false;
        } catch (Exception e) {
            log.warn("Idempotent guard Redis operation failed for key={}, allowing processing: {}",
                    key, e.getMessage());
            // Fail open: allow processing when Redis is unavailable
            return true;
        }
    }
}
