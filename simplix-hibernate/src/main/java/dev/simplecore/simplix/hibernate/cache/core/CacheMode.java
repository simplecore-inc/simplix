package dev.simplecore.simplix.hibernate.cache.core;

/**
 * Cache operation mode
 */
public enum CacheMode {
    /**
     * Automatically detect best available mode
     */
    AUTO,

    /**
     * Local cache only (EhCache, Caffeine)
     */
    LOCAL,

    /**
     * Distributed cache (Redis, Hazelcast)
     */
    DISTRIBUTED,

    /**
     * Hybrid mode - local with distributed sync
     */
    HYBRID,

    /**
     * Cache disabled
     */
    DISABLED
}