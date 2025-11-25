package dev.simplecore.simplix.cache.service;

import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Cache Service
 * Main entry point for caching operations, delegates to the active strategy
 */
@Slf4j
public class CacheService {

    private final CacheStrategy cacheStrategy;

    @Autowired
    public CacheService(CacheStrategy cacheStrategy) {
        this.cacheStrategy = cacheStrategy;
        log.info("Cache service initialized with strategy: {}", cacheStrategy.getName());
    }

    /**
     * Get value from cache
     */
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        return cacheStrategy.get(cacheName, key, type);
    }

    /**
     * Put value in cache with default TTL
     */
    public <T> void put(String cacheName, Object key, T value) {
        cacheStrategy.put(cacheName, key, value);
    }

    /**
     * Put value in cache with specific TTL
     */
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        cacheStrategy.put(cacheName, key, value, ttl);
    }

    /**
     * Get or compute if absent
     */
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        return cacheStrategy.getOrCompute(cacheName, key, valueLoader, type);
    }

    /**
     * Get or compute if absent with TTL
     */
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl) {
        return cacheStrategy.getOrCompute(cacheName, key, valueLoader, type, ttl);
    }

    /**
     * Evict specific key
     */
    public void evict(String cacheName, Object key) {
        cacheStrategy.evict(cacheName, key);
    }

    /**
     * Evict multiple keys
     */
    public void evictAll(String cacheName, Collection<?> keys) {
        cacheStrategy.evictAll(cacheName, keys);
    }

    /**
     * Clear entire cache
     */
    public void clear(String cacheName) {
        cacheStrategy.clear(cacheName);
    }

    /**
     * Clear all caches
     */
    public void clearAll() {
        cacheStrategy.clearAll();
    }

    /**
     * Check if key exists
     */
    public boolean exists(String cacheName, Object key) {
        return cacheStrategy.exists(cacheName, key);
    }

    /**
     * Get all keys in cache
     */
    public Collection<Object> getKeys(String cacheName) {
        return cacheStrategy.getKeys(cacheName);
    }

    /**
     * Get all entries in cache
     */
    public <T> Map<Object, T> getAll(String cacheName, Class<T> type) {
        return cacheStrategy.getAll(cacheName, type);
    }

    /**
     * Put multiple entries
     */
    public <T> void putAll(String cacheName, Map<Object, T> entries) {
        cacheStrategy.putAll(cacheName, entries);
    }

    /**
     * Put multiple entries with TTL
     */
    public <T> void putAll(String cacheName, Map<Object, T> entries, Duration ttl) {
        cacheStrategy.putAll(cacheName, entries, ttl);
    }

    /**
     * Get cache statistics
     */
    public CacheStrategy.CacheStatistics getStatistics(String cacheName) {
        return cacheStrategy.getStatistics(cacheName);
    }

    /**
     * Get active strategy name
     */
    public String getStrategyName() {
        return cacheStrategy.getName();
    }

    /**
     * Check if cache is available
     */
    public boolean isAvailable() {
        return cacheStrategy.isAvailable();
    }
}