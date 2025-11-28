package dev.simplecore.simplix.cache.config;

import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cache Metrics Collector
 * Collects and logs cache metrics periodically
 */
@Slf4j
public class CacheMetricsCollector {

    private final CacheStrategy cacheStrategy;
    private final CacheProperties properties;

    public CacheMetricsCollector(CacheStrategy cacheStrategy, CacheProperties properties) {
        this.cacheStrategy = cacheStrategy;
        this.properties = properties;
    }

    /**
     * Collect cache metrics periodically.
     * In multi-instance deployments, each instance collects its own metrics.
     * For cluster-wide metrics aggregation, use a centralized monitoring solution.
     */
    @Scheduled(fixedDelayString = "#{${simplix.cache.metrics.collectionIntervalSeconds:60} * 1000}")
    public void collectMetrics() {
        if (!properties.getMetrics().isEnabled()) {
            return;
        }

        properties.getCacheConfigs().keySet().forEach(cacheName -> {
            try {
                CacheStrategy.CacheStatistics stats = cacheStrategy.getStatistics(cacheName);

                if (stats.hits() > 0 || stats.misses() > 0) {
					//noinspection LoggingPlaceholderCountMatchesArgumentCount
					log.debug("Cache metrics for {}: hits={}, misses={}, hitRate={:.2f}%, size={}, evictions={}",
                        cacheName,
                        stats.hits(),
                        stats.misses(),
                        stats.hitRate() * 100,
                        stats.size(),
                        stats.evictions()
                    );
                }
            } catch (Exception e) {
                log.trace("Failed to collect metrics for cache {}: {}", cacheName, e.getMessage());
            }
        });
    }
}