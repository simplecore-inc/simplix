package dev.simplecore.simplix.cache.strategy;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Cache Strategy Interface
 * Defines the contract for different caching implementations
 */
public interface CacheStrategy {

    /**
     * Get the strategy name
     */
    String getName();

    /**
     * Get a value from cache
     */
    <T> Optional<T> get(String cacheName, Object key, Class<T> type);

    /**
     * Put a value in cache
     */
    <T> void put(String cacheName, Object key, T value);

    /**
     * Put a value in cache with TTL
     */
    <T> void put(String cacheName, Object key, T value, Duration ttl);

    /**
     * Get or compute if absent
     */
    <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type);

    /**
     * Get or compute if absent with TTL
     */
    <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl);

    /**
     * Evict a specific key from cache
     */
    void evict(String cacheName, Object key);

    /**
     * Evict multiple keys from cache
     */
    void evictAll(String cacheName, Collection<?> keys);

    /**
     * Clear entire cache
     */
    void clear(String cacheName);

    /**
     * Clear all caches
     */
    void clearAll();

    /**
     * Check if key exists in cache
     */
    boolean exists(String cacheName, Object key);

    /**
     * Get all keys in a cache
     */
    Collection<Object> getKeys(String cacheName);

    /**
     * Get all entries in a cache
     */
    <T> Map<Object, T> getAll(String cacheName, Class<T> type);

    /**
     * Put multiple entries in cache
     */
    <T> void putAll(String cacheName, Map<Object, T> entries);

    /**
     * Put multiple entries in cache with TTL
     */
    <T> void putAll(String cacheName, Map<Object, T> entries, Duration ttl);

    /**
     * Get cache statistics
     */
    CacheStatistics getStatistics(String cacheName);

    /**
     * Initialize the strategy
     */
    void initialize();

    /**
     * Shutdown the strategy
     */
    void shutdown();

    /**
     * Check if the strategy is available
     */
    boolean isAvailable();

    /**
     * Cache statistics
     */
    record CacheStatistics(
        long hits,
        long misses,
        long evictions,
        long puts,
        long removals,
        double hitRate,
        long size,
        long memoryUsage
    ) {
        public static CacheStatistics empty() {
            return new CacheStatistics(0, 0, 0, 0, 0, 0.0, 0, 0);
        }
    }
}