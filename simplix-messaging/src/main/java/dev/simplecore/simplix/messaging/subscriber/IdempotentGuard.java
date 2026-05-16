package dev.simplecore.simplix.messaging.subscriber;

import dev.simplecore.simplix.messaging.broker.redis.RedisIdempotencyStore;
import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * @deprecated since 1.1.1, use {@link IdempotencyStore} (broker-agnostic SPI) instead.
 *             For Redis-backed deduplication, inject {@link RedisIdempotencyStore} directly.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "1.1.1", forRemoval = true)
public class IdempotentGuard implements IdempotencyStore {

    private final RedisIdempotencyStore delegate;

    public IdempotentGuard(StringRedisTemplate redisTemplate, Duration ttl) {
        this.delegate = new RedisIdempotencyStore(redisTemplate, ttl);
    }

    @Override
    public boolean tryAcquire(String channel, String group, String messageId) {
        return delegate.tryAcquire(channel, group, messageId);
    }

    @Override
    public Duration ttl() {
        return delegate.ttl();
    }
}
