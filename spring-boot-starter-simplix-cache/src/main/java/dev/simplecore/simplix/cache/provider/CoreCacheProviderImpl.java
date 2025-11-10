package dev.simplecore.simplix.cache.provider;

import dev.simplecore.simplix.cache.service.CacheService;
import dev.simplecore.simplix.core.cache.CacheProvider;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Implementation of Core CacheProvider that bridges to CacheService
 * This is discovered via SPI by the core module and registered as a Spring Bean
 */
@Slf4j
public class CoreCacheProviderImpl implements CacheProvider {

    private final CacheService cacheService;

    public CoreCacheProviderImpl(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("CoreCacheProviderImpl initialized with CacheService using strategy: {}",
            cacheService.getStrategyName());
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        return cacheService.get(cacheName, key, type);
    }

    @Override
    public <T> void put(String cacheName, Object key, T value) {
        cacheService.put(cacheName, key, value);
    }

    @Override
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        cacheService.put(cacheName, key, value, ttl);
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        return cacheService.getOrCompute(cacheName, key, valueLoader, type);
    }

    @Override
    public void evict(String cacheName, Object key) {
        cacheService.evict(cacheName, key);
    }

    @Override
    public void clear(String cacheName) {
        cacheService.clear(cacheName);
    }

    @Override
    public boolean exists(String cacheName, Object key) {
        return cacheService.exists(cacheName, key);
    }

    @Override
    public boolean isAvailable() {
        return cacheService.isAvailable();
    }

    @Override
    public String getName() {
        return "CacheModule-" + cacheService.getStrategyName();
    }

    @Override
    public int getPriority() {
        return 100; // High priority to be selected over other providers
    }
}