package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Slf4j
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "messaging:idempotent:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public boolean tryAcquire(String channel, String group, String messageId) {
        if (redisTemplate == null) return true;
        String key = (group == null || group.isEmpty())
                ? KEY_PREFIX + channel + ":" + messageId
                : KEY_PREFIX + group + ":" + channel + ":" + messageId;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("RedisIdempotencyStore failure for key={}, allowing processing: {}",
                    key, e.getMessage());
            return true;
        }
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
