package dev.simplecore.simplix.hibernate.cache.admin;

import dev.simplecore.simplix.hibernate.cache.batch.BatchEvictionOptimizer;
import dev.simplecore.simplix.hibernate.cache.cluster.ClusterSyncMonitor;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.monitoring.EvictionMetrics;
import dev.simplecore.simplix.hibernate.cache.resilience.EvictionRetryHandler;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.Map;

/**
 * Admin endpoints for cache management
 */
@Slf4j
@Endpoint(id = "cache-admin")
@RequiredArgsConstructor
public class CacheAdminController {

    private final HibernateCacheManager cacheManager;
    private final CacheEvictionStrategy evictionStrategy;
    private final EvictionMetrics metrics;
    private final ClusterSyncMonitor clusterMonitor;
    private final BatchEvictionOptimizer batchOptimizer;
    private final EvictionRetryHandler retryHandler;

    /**
     * Get comprehensive cache status
     */
    @ReadOperation
    public Map<String, Object> status() {
        return Map.of(
                "provider", evictionStrategy.getActiveProviderInfo(),
                "metrics", metrics.metrics(),
                "cluster", clusterMonitor.getClusterStatus(),
                "batch", batchOptimizer.getBatchStatistics(),
                "retry", retryHandler.getRetryStatistics(),
                "regions", cacheManager.getActiveRegions()
        );
    }

    /**
     * Force evict specific cache
     */
    @DeleteOperation
    public Map<String, Object> evict(String entityClass, String entityId) {
        if (entityClass == null || entityClass.isBlank()) {
            return Map.of(
                    "success", false,
                    "error", "entityClass parameter is required"
            );
        }

        // Trim whitespace to prevent ClassNotFoundException for " " input (M3 fix)
        String trimmedEntityClass = entityClass.trim();
        if (trimmedEntityClass.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "entityClass cannot be whitespace only"
            );
        }

        try {
            Class<?> clazz = loadClass(trimmedEntityClass);

            if (clazz == null) {
                return Map.of(
                        "success", false,
                        "error", "Entity class not found: " + trimmedEntityClass
                );
            }

            if (entityId != null && !entityId.isEmpty()) {
                cacheManager.evictEntity(clazz, entityId);
                log.info("✔ Admin evicted entity: {} [{}]", entityClass, entityId);
            } else {
                cacheManager.evictEntityCache(clazz);
                log.info("✔ Admin evicted all: {}", entityClass);
            }

            return Map.of(
                    "success", true,
                    "message", "Cache evicted successfully"
            );

        } catch (Exception e) {
            log.error("✖ Admin eviction failed for {}", trimmedEntityClass, e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Loads a class using multiple ClassLoader strategies (8th review fix).
     * Handles app server / web application scenarios where context ClassLoader matters.
     *
     * @param className the fully qualified class name
     * @return the Class if found, null otherwise
     */
    private Class<?> loadClass(String className) {
        // Try multiple ClassLoaders
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                getClass().getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        for (ClassLoader loader : loaders) {
            if (loader != null) {
                try {
                    return Class.forName(className, false, loader);
                } catch (ClassNotFoundException e) {
                    // Continue to next loader
                }
            }
        }

        log.warn("⚠ Cannot load class {} from any ClassLoader", className);
        return null;
    }

    /**
     * Evict all caches.
     * Uses @WriteOperation instead of @DeleteOperation to avoid conflict with evict() method.
     * Call with POST to actuator/cache-admin endpoint with action=evict-all parameter.
     * (10th review fix - Actuator endpoint method signature conflict)
     */
    @WriteOperation
    public Map<String, Object> evictAll(String action) {
        // Validate action parameter for safety (prevent accidental invocation)
        if (!"evict-all".equals(action)) {
            return Map.of(
                    "success", false,
                    "error", "Invalid action. Use action=evict-all to confirm eviction of all caches"
            );
        }
        try {
            cacheManager.evictAll();
            log.warn("⚠ Admin evicted ALL caches");

            return Map.of(
                    "success", true,
                    "message", "All caches evicted"
            );

        } catch (Exception e) {
            log.error("✖ Failed to evict all caches", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Reprocess dead letter queue
     */
    @WriteOperation
    public Map<String, Object> reprocessDLQ() {
        try {
            retryHandler.reprocessDeadLetterQueue();
            log.info("✔ DLQ reprocessing initiated");

            return Map.of(
                    "success", true,
                    "message", "DLQ reprocessing started"
            );

        } catch (Exception e) {
            log.error("✖ Failed to reprocess DLQ", e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Toggle batch mode
     */
    @WriteOperation
    public Map<String, Object> toggleBatchMode(boolean enable) {
        if (enable) {
            batchOptimizer.startBatch();
            log.info("✔ Batch mode enabled by admin");
        } else {
            batchOptimizer.endBatch();
            log.info("✔ Batch mode disabled by admin");
        }

        return Map.of(
                "success", true,
                "batchMode", enable
        );
    }
}