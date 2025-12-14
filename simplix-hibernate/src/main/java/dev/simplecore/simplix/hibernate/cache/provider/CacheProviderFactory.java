package dev.simplecore.simplix.hibernate.cache.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory for selecting appropriate cache provider
 */
@Slf4j
public class CacheProviderFactory {

    private final Map<String, CacheProvider> providers;
    private final LocalCacheProvider localProvider;

    @Autowired
    public CacheProviderFactory(List<CacheProvider> providerList) {
        // Make providers map immutable to prevent modification after construction
        this.providers = Collections.unmodifiableMap(
                providerList.stream()
                        .collect(Collectors.toMap(
                                p -> p.getType().toUpperCase(),
                                p -> p
                        )));

        LocalCacheProvider foundLocal = (LocalCacheProvider) providers.get("LOCAL");
        if (foundLocal == null) {
            // LocalCacheProvider is a required dependency - should always be registered by auto-configuration
            // Throwing exception instead of manual creation to ensure proper Spring lifecycle management (C10 fix)
            throw new IllegalStateException(
                    "LocalCacheProvider not found in provider list. " +
                    "Ensure SimpliXHibernateCacheAutoConfiguration is enabled and localCacheProvider bean is registered.");
        }
        this.localProvider = foundLocal;

        log.info("ℹ Available cache providers: {}", providers.keySet());
    }

    /**
     * Get provider by type
     */
    public CacheProvider getProvider(String type) {
        if (type == null || type.isEmpty()) {
            return selectBestAvailable();
        }

        CacheProvider provider = providers.get(type.toUpperCase());
        if (provider != null && provider.isAvailable()) {
            return provider;
        }

        log.warn("⚠ Provider {} not available, falling back to best available", type);
        return selectBestAvailable();
    }

    /**
     * Select best available provider based on priority
     */
    public CacheProvider selectBestAvailable() {
        // Priority order: Redis > Hazelcast > Infinispan > Local
        String[] priority = {"REDIS", "HAZELCAST", "INFINISPAN"};

        for (String type : priority) {
            CacheProvider provider = providers.get(type);
            if (provider != null && provider.isAvailable()) {
                log.info("✔ Selected cache provider: {}", type);
                return provider;
            }
        }

        log.info("ℹ No distributed cache available, using LOCAL provider");
        return localProvider;
    }

    /**
     * Get all available providers
     */
    public List<CacheProvider> getAvailableProviders() {
        return providers.values().stream()
                .filter(CacheProvider::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Check if a specific provider type is available.
     * Added null check for type parameter to prevent NPE.
     */
    public boolean isProviderAvailable(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        CacheProvider provider = providers.get(type.toUpperCase());
        return provider != null && provider.isAvailable();
    }

    /**
     * Get provider statistics
     */
    public Map<String, CacheProvider.CacheProviderStats> getAllStats() {
        return providers.entrySet().stream()
                .filter(e -> e.getValue().isAvailable())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getStats()
                ));
    }
}