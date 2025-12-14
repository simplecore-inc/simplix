package dev.simplecore.simplix.hibernate.cache.strategy;

import dev.simplecore.simplix.hibernate.cache.core.CacheMode;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Strategy for cache eviction based on cache mode and available providers.
 *
 * <p>This class handles the actual cache eviction after transaction commit.
 * It is called by {@link dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler}
 * to perform both local cache eviction and distributed broadcast.</p>
 *
 * <h3>Cache Modes</h3>
 * <ul>
 *   <li>LOCAL - Only evict local cache</li>
 *   <li>DISTRIBUTED - Evict local + broadcast to other nodes</li>
 *   <li>HYBRID - Same as distributed for eviction</li>
 *   <li>DISABLED - Skip all eviction</li>
 * </ul>
 *
 * @see dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler
 */
@Slf4j
@RequiredArgsConstructor
public class CacheEvictionStrategy implements CacheProvider.CacheEvictionEventListener {

    private final HibernateCacheManager cacheManager;
    private final CacheProviderFactory providerFactory;
    private final HibernateCacheProperties properties;

    private volatile CacheProvider activeProvider;

    /**
     * Shutdown flag to prevent failover and eviction during shutdown.
     * Once set, no new failover attempts will be made (9th review fix).
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    @PostConstruct
    public void initialize() {
        // Auto-select best available provider
        activeProvider = providerFactory.selectBestAvailable();

        if (activeProvider == null) {
            log.error("✖ No cache provider available. Cache eviction will not work.");
            return;
        }

        // Initialize and subscribe to evictions
        activeProvider.initialize();
        activeProvider.subscribeToEvictions(this);

        log.info("✔ Cache eviction strategy initialized with provider: {}", activeProvider.getType());
    }

    @PreDestroy
    public void shutdown() {
        // Set shutdown flag first to prevent failover attempts during shutdown
        if (shutdown.getAndSet(true)) {
            return; // Already shutdown
        }

        CacheProvider provider = activeProvider;
        if (provider != null) {
            try {
                provider.shutdown();
                log.debug("✔ Active provider {} shutdown successfully", provider.getType());
            } catch (Exception e) {
                log.warn("⚠ Error shutting down provider {}: {}", provider.getType(), e.getMessage());
            }
        }
        activeProvider = null;
    }

    /**
     * Handle cache eviction based on configured mode
     */
    public void evict(Class<?> entityClass, Object entityId) {
        // Null safety check for entityClass (10th review fix)
        if (entityClass == null) {
            log.warn("⚠ Cannot evict cache: entity class is null");
            return;
        }

        CacheMode mode = determineCacheMode();

        switch (mode) {
            case LOCAL -> evictLocal(entityClass, entityId);
            case DISTRIBUTED -> evictDistributed(entityClass, entityId);
            case HYBRID -> evictHybrid(entityClass, entityId);
            case DISABLED -> log.debug("Cache disabled, skipping eviction");
            case AUTO -> {
                // AUTO should be resolved by determineCacheMode(), but handle defensively
                // Default to LOCAL if resolution fails (10th review fix)
                log.warn("⚠ AUTO mode was not resolved, defaulting to LOCAL");
                evictLocal(entityClass, entityId);
            }
        }
    }

    private void evictLocal(Class<?> entityClass, Object entityId) {
        if (entityId != null) {
            // Single entity eviction - only evict the specific entity
            cacheManager.evictEntity(entityClass, entityId);
        } else {
            // Bulk eviction - evict entire entity cache
            cacheManager.evictEntityCache(entityClass);
        }
    }

    private void evictDistributed(Class<?> entityClass, Object entityId) {
        // Independent try-catch for local and broadcast to ensure both are attempted
        // Local eviction failure should not prevent broadcast to other nodes
        try {
            evictLocal(entityClass, entityId);
        } catch (Exception e) {
            log.error("✖ Local cache eviction failed for {}: {}",
                    entityClass.getSimpleName(), e.getMessage());
        }

        // Broadcast eviction to other nodes regardless of local eviction result
        try {
            broadcastEviction(entityClass, entityId);
        } catch (Exception e) {
            log.error("✖ Broadcast eviction failed for {}: {}",
                    entityClass.getSimpleName(), e.getMessage());
        }
    }

    private void evictHybrid(Class<?> entityClass, Object entityId) {
        // Same as distributed for now
        evictDistributed(entityClass, entityId);
    }

    private void broadcastEviction(Class<?> entityClass, Object entityId) {
        // Get provider with failover support
        CacheProvider provider = getAvailableProvider();
        if (provider == null) {
            log.warn("⚠ No active provider available, skipping broadcast");
            return;
        }

        CacheEvictionEvent event = CacheEvictionEvent.builder()
                .entityClass(entityClass.getName())
                .entityId(entityId != null ? entityId.toString() : null)
                .nodeId(properties.getNodeId())
                .timestamp(System.currentTimeMillis())
                .build();

        provider.broadcastEviction(event);
    }

    /**
     * Gets an available provider with failover support.
     * If the current provider is unavailable, attempts to select a new one.
     *
     * <p>9th review fix: Shuts down old provider before activating new one to prevent
     * resource leaks and duplicate event processing.</p>
     *
     * @return available provider, or null if none available
     */
    private synchronized CacheProvider getAvailableProvider() {
        // Check shutdown flag first to prevent failover during shutdown
        if (shutdown.get()) {
            return null;
        }

        CacheProvider provider = activeProvider;

        // Check if current provider is still available
        if (provider != null && provider.isAvailable()) {
            return provider;
        }

        // Check shutdown again before attempting failover
        if (shutdown.get()) {
            return null;
        }

        // Current provider unavailable - attempt failover
        if (provider != null) {
            log.warn("⚠ Active provider {} is no longer available, attempting failover",
                    provider.getType());
        }

        // Try to select a new provider
        CacheProvider newProvider = providerFactory.selectBestAvailable();

        if (newProvider == null) {
            log.error("✖ Failover failed: No cache provider available");
            return null;
        }

        // Initialize and subscribe if different from current
        if (newProvider != provider) {
            // CRITICAL FIX (9th review): Shutdown old provider BEFORE activating new one.
            // This prevents resource leaks (connections, threads, listeners) and
            // duplicate event processing from both providers being active simultaneously.
            if (provider != null) {
                try {
                    provider.shutdown();
                    log.debug("✔ Old provider {} shut down during failover", provider.getType());
                } catch (Exception e) {
                    // Log but continue with failover - old provider may be in bad state
                    log.warn("⚠ Error shutting down old provider during failover: {}", e.getMessage());
                }
            }

            // Double-check shutdown before completing failover
            if (shutdown.get()) {
                return null;
            }

            try {
                newProvider.initialize();
                newProvider.subscribeToEvictions(this);
                activeProvider = newProvider;
                log.info("✔ Failover successful: Switched to provider {}", newProvider.getType());
            } catch (Exception e) {
                log.error("✖ Failover failed: Could not initialize provider {}: {}",
                        newProvider.getType(), e.getMessage());
                return null;
            }
        }

        return activeProvider;
    }

    /**
     * Handle remote cache eviction events from other nodes.
     *
     * <p>Uses multiple ClassLoader strategies to handle class loading in
     * distributed environments where nodes may have different versions (8th review fix).</p>
     */
    @Override
    public void onEvictionEvent(CacheEvictionEvent event) {
        // Only process events from other nodes
        if (Objects.equals(properties.getNodeId(), event.getNodeId())) {
            return;
        }

        String entityClassName = event.getEntityClass();
        if (entityClassName == null || entityClassName.isEmpty()) {
            log.warn("⚠ Received eviction event with null or empty entity class");
            return;
        }

        try {
            Class<?> entityClass = loadEntityClass(entityClassName);

            if (entityClass == null) {
                // Class not found - likely version mismatch between cluster nodes (8th review fix)
                log.error("✖ Cannot process eviction: entity class {} not found or not accessible. " +
                         "This may indicate version mismatch between cluster nodes. " +
                         "Performing FULL cache eviction as fallback.",
                         entityClassName);

                // Fallback: full cache eviction to ensure consistency
                cacheManager.evictAll();
                return;
            }

            if (event.getEntityId() != null) {
                cacheManager.evictEntity(entityClass, event.getEntityId());
            } else {
                cacheManager.evictEntityCache(entityClass);
            }

            if (event.getRegion() != null) {
                cacheManager.evictRegion(event.getRegion());
            }

            // Capture for consistent log output
            CacheProvider provider = activeProvider;
            log.debug("✔ Processed remote cache eviction from node {} via {}",
                    event.getNodeId(), provider != null ? provider.getType() : "UNKNOWN");

        } catch (SecurityException e) {
            log.error("✖ Security policy prevents loading class {}: {}. " +
                     "Check SecurityManager permissions. Performing FULL cache eviction as fallback.",
                     entityClassName, e.getMessage());
            cacheManager.evictAll();
        } catch (Exception e) {
            log.error("✖ Failed to process remote cache eviction for {}. " +
                     "Performing FULL cache eviction as fallback.", entityClassName, e);
            cacheManager.evictAll();
        }
    }

    /**
     * Loads entity class using multiple ClassLoader strategies (8th review fix).
     *
     * @param className the fully qualified class name
     * @return the Class if found, null otherwise
     */
    private Class<?> loadEntityClass(String className) {
        // Try context ClassLoader first (web app / app server scenarios)
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        if (contextCL != null) {
            try {
                return Class.forName(className, false, contextCL);
            } catch (ClassNotFoundException | SecurityException e) {
                // Fall through to next ClassLoader
            }
        }

        // Fallback to current class's ClassLoader
        try {
            return Class.forName(className, false, getClass().getClassLoader());
        } catch (ClassNotFoundException | SecurityException e) {
            log.warn("⚠ Cannot load entity class {}: {}", className, e.getMessage());
            return null;
        }
    }


    private CacheMode determineCacheMode() {
        CacheMode mode = properties.getMode();

        if (mode == CacheMode.AUTO) {
            // Capture to local variable for consistent reads
            CacheProvider provider = activeProvider;
            if (provider == null) {
                return CacheMode.LOCAL;
            }
            String providerType = provider.getType();
            if ("LOCAL".equals(providerType)) {
                return CacheMode.LOCAL;
            } else {
                return CacheMode.DISTRIBUTED;
            }
        }

        return mode;
    }

    /**
     * Get current provider information
     */
    public String getActiveProviderInfo() {
        if (activeProvider != null) {
            CacheProvider.CacheProviderStats stats = activeProvider.getStats();
            return String.format("Provider: %s, Connected: %s, Sent: %d, Received: %d",
                    activeProvider.getType(),
                    stats.connected(),
                    stats.evictionsSent(),
                    stats.evictionsReceived());
        }
        return "No active provider";
    }
}