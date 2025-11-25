package dev.simplecore.simplix.core.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Core Cache Provider Interface
 * Provides cache abstraction for all modules without depending on cache implementation
 * Implementations should be provided via SPI (Service Provider Interface)
 */
public interface CacheProvider {

    /**
     * Get value from cache
     */
    <T> Optional<T> get(String cacheName, Object key, Class<T> type);

    /**
     * Put value in cache
     */
    <T> void put(String cacheName, Object key, T value);

    /**
     * Put value in cache with TTL
     */
    <T> void put(String cacheName, Object key, T value, Duration ttl);

    /**
     * Get or compute if absent
     */
    <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type);

    /**
     * Evict specific key
     */
    void evict(String cacheName, Object key);

    /**
     * Clear entire cache
     */
    void clear(String cacheName);

    /**
     * Check if key exists
     */
    boolean exists(String cacheName, Object key);

    /**
     * Check if provider is available
     */
    boolean isAvailable();

    /**
     * Get provider name
     */
    String getName();

    /**
     * Get provider priority (higher value = higher priority)
     */
    default int getPriority() {
        return 0;
    }
}