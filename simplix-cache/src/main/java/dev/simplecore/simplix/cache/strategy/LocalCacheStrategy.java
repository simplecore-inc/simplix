package dev.simplecore.simplix.cache.strategy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Local Cache Strategy using Caffeine
 * Suitable for single-instance deployments or development
 *
 * <p><b>Important TTL Behavior:</b> Caffeine caches are configured per cache name with a single TTL value.
 * Once a cache is created with a specific TTL, that TTL is fixed for all entries in that cache.
 * Calling {@link #put(String, Object, Object, Duration)} with a different TTL after the cache
 * is already created will NOT change the TTL for that cache - the original TTL will continue to be used.
 * This is a limitation of Caffeine's per-cache configuration model.</p>
 *
 * <p>To use different TTLs, create separate caches with different names.</p>
 */
@Slf4j
public class LocalCacheStrategy implements CacheStrategy {

    private final Map<String, Cache<Object, Object>> caches = new ConcurrentHashMap<>();
    private final Duration defaultTtl = Duration.ofHours(1);
    private final long maximumSize = 10000;

    @Override
    public String getName() {
        return "LocalCacheStrategy";
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        Cache<Object, Object> cache = getOrCreateCache(cacheName);
        T value = (T) cache.getIfPresent(key);

        if (value != null) {
            log.trace("Cache hit for key {} in cache {}", key, cacheName);
        } else {
            log.trace("Cache miss for key {} in cache {}", key, cacheName);
        }

        return Optional.ofNullable(value);
    }

    @Override
    public <T> void put(String cacheName, Object key, T value) {
        put(cacheName, key, value, defaultTtl);
    }

    @Override
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        if (value == null) {
            log.debug("Skipping null value for key {} in cache {}", key, cacheName);
            return;
        }

        Cache<Object, Object> cache = getOrCreateCache(cacheName, ttl);
        Cache<Object, Object> existing = caches.get(cacheName);

        if (existing != null && !existing.equals(cache)) {
            log.warn("Cache {} already exists with different TTL. Using existing cache TTL. Requested TTL: {}",
                     cacheName, ttl);
        }

        cache.put(key, value);
        log.trace("Put key {} in cache {}", key, cacheName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        return getOrCompute(cacheName, key, valueLoader, type, defaultTtl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl) {
        Cache<Object, Object> cache = getOrCreateCache(cacheName, ttl);

        try {
            return (T) cache.get(key, k -> {
                try {
                    T value = valueLoader.call();
                    log.trace("Computed value for key {} in cache {}", key, cacheName);
                    return value;
                } catch (Exception e) {
                    log.error("Failed to compute value for key {} in cache {}", key, cacheName, e);
                    throw new RuntimeException("Cache value computation failed", e);
                }
            });
        } catch (Exception e) {
            log.error("Cache get or compute failed for key {} in cache {}", key, cacheName, e);
            return null;
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        Cache<Object, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
            log.trace("Evicted key {} from cache {}", key, cacheName);
        }
    }

    @Override
    public void evictAll(String cacheName, Collection<?> keys) {
        Cache<Object, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll(keys);
            log.trace("Evicted {} keys from cache {}", keys.size(), cacheName);
        }
    }

    @Override
    public void clear(String cacheName) {
        Cache<Object, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
            log.debug("Cleared cache {}", cacheName);
        }
    }

    @Override
    public void clearAll() {
        caches.values().forEach(Cache::invalidateAll);
        log.debug("Cleared all {} caches", caches.size());
    }

    @Override
    public boolean exists(String cacheName, Object key) {
        Cache<Object, Object> cache = caches.get(cacheName);
        return cache != null && cache.getIfPresent(key) != null;
    }

    @Override
    public Collection<Object> getKeys(String cacheName) {
        Cache<Object, Object> cache = caches.get(cacheName);
        if (cache != null) {
            return new HashSet<>(cache.asMap().keySet());
        }
        return Collections.emptySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<Object, T> getAll(String cacheName, Class<T> type) {
        Cache<Object, Object> cache = caches.get(cacheName);
        if (cache != null) {
            Map<Object, T> result = new HashMap<>();
            cache.asMap().forEach((k, v) -> {
                if (type.isInstance(v)) {
                    result.put(k, (T) v);
                }
            });
            return result;
        }
        return Collections.emptyMap();
    }

    @Override
    public <T> void putAll(String cacheName, Map<Object, T> entries) {
        putAll(cacheName, entries, defaultTtl);
    }

    @Override
    public <T> void putAll(String cacheName, Map<Object, T> entries, Duration ttl) {
        Cache<Object, Object> cache = getOrCreateCache(cacheName, ttl);
        cache.putAll(entries);
        log.trace("Put {} entries in cache {}", entries.size(), cacheName);
    }

    @Override
    public CacheStatistics getStatistics(String cacheName) {
        Cache<Object, Object> cache = caches.get(cacheName);
        if (cache != null) {
            CacheStats stats = cache.stats();
            return new CacheStatistics(
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount(),
                stats.loadCount(),
                0, // removals not tracked separately
                stats.hitRate(),
                cache.estimatedSize(),
                0 // memory usage estimation not available
            );
        }
        return CacheStatistics.empty();
    }

    @Override
    public void initialize() {
        log.info("Local cache strategy initialized with Caffeine");
    }

    @Override
    public void shutdown() {
        clearAll();
        caches.clear();
        log.info("Local cache strategy shutdown complete");
    }

    @Override
    public boolean isAvailable() {
        return true; // Local cache is always available
    }

    private Cache<Object, Object> getOrCreateCache(String cacheName) {
        return getOrCreateCache(cacheName, defaultTtl);
    }

    private Cache<Object, Object> getOrCreateCache(String cacheName, Duration ttl) {
        return caches.computeIfAbsent(cacheName, name -> {
            log.debug("Creating local cache: {} with TTL: {}", name, ttl);
            return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
                .recordStats()
                .build();
        });
    }
}