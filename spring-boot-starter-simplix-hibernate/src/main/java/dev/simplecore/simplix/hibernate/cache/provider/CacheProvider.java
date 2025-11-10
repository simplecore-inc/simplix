package dev.simplecore.simplix.hibernate.cache.provider;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;

/**
 * Interface for distributed cache providers
 */
public interface CacheProvider {

    /**
     * Provider type identifier
     */
    String getType();

    /**
     * Check if provider is available and connected
     */
    boolean isAvailable();

    /**
     * Broadcast cache eviction event to other nodes
     */
    void broadcastEviction(CacheEvictionEvent event);

    /**
     * Subscribe to cache eviction events from other nodes
     */
    void subscribeToEvictions(CacheEvictionEventListener listener);

    /**
     * Initialize the cache provider
     */
    void initialize();

    /**
     * Shutdown the cache provider
     */
    void shutdown();

    /**
     * Get provider-specific statistics
     */
    CacheProviderStats getStats();

    interface CacheEvictionEventListener {
        void onEvictionEvent(CacheEvictionEvent event);
    }

    record CacheProviderStats(
            long evictionsSent,
            long evictionsReceived,
            boolean connected,
            String nodeId
    ) {}
}