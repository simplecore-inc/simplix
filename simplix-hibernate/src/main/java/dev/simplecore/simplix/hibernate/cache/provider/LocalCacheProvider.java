package dev.simplecore.simplix.hibernate.cache.provider;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Local-only cache provider (no distribution).
 * Initialize and shutdown are idempotent - multiple calls have no additional effect.
 */
@Slf4j
public class LocalCacheProvider implements CacheProvider {

    private volatile boolean initialized = false;

    @Override
    public String getType() {
        return "LOCAL";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void broadcastEviction(CacheEvictionEvent event) {
        // No-op for local cache
    }

    @Override
    public void subscribeToEvictions(CacheEvictionEventListener listener) {
        // No-op for local cache
        log.debug("Local cache mode - no subscription needed");
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        log.info("✔ Local cache provider initialized");
    }

    @Override
    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }
        initialized = false;
        log.info("✔ Local cache provider shutdown");
    }

    @Override
    public CacheProviderStats getStats() {
        return new CacheProviderStats(0, 0, true, "local");
    }
}