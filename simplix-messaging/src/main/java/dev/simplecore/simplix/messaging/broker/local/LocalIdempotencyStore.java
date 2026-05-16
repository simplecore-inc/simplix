package dev.simplecore.simplix.messaging.broker.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;

import java.time.Duration;

public class LocalIdempotencyStore implements IdempotencyStore {

    private final Cache<String, Boolean> seen;
    private final Duration ttl;

    public LocalIdempotencyStore(Duration ttl, long maxSize) {
        this.ttl = ttl;
        this.seen = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public boolean tryAcquire(String channel, String groupName, String messageId) {
        String key = (groupName == null || groupName.isEmpty())
                ? channel + ":" + messageId
                : groupName + ":" + channel + ":" + messageId;
        return seen.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
