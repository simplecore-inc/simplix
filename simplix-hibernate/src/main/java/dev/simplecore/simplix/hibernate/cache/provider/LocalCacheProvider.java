package dev.simplecore.simplix.hibernate.cache.provider;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Local-only cache provider (no distribution)
 */
@Slf4j
public class LocalCacheProvider implements CacheProvider {

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
    public void initialize() {
        log.info("✔ Local cache provider initialized");
    }

    @Override
    public void shutdown() {
        log.info("✔ Local cache provider shutdown");
    }

    @Override
    public CacheProviderStats getStats() {
        return new CacheProviderStats(0, 0, true, "local");
    }
}