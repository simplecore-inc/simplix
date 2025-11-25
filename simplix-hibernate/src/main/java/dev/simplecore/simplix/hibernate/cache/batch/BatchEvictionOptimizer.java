package dev.simplecore.simplix.hibernate.cache.batch;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimizes batch eviction operations to reduce network overhead
 */
@Slf4j
public class BatchEvictionOptimizer {

    private CacheProvider cacheProvider;
    private final CacheProviderFactory providerFactory;
    private final Queue<CacheEvictionEvent> pendingEvictions = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean batchMode = new AtomicBoolean(false);
    private final AtomicInteger batchSize = new AtomicInteger(0);

    @Value("${hibernate.cache.batch.threshold:10}")
    private int batchThreshold;

    @Value("${hibernate.cache.batch.max-delay:100}")
    private long maxDelayMs;

    @Value("${hibernate.cache.provider.type:AUTO}")
    private String providerType;

    @Autowired
    public BatchEvictionOptimizer(CacheProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // Select the active provider
        if ("AUTO".equalsIgnoreCase(providerType)) {
            this.cacheProvider = providerFactory.selectBestAvailable();
        } else {
            this.cacheProvider = providerFactory.getProvider(providerType);
        }
    }

    /**
     * Enable batch mode for bulk operations
     */
    public BatchContext startBatch() {
        batchMode.set(true);
        log.debug("✔ Batch eviction mode enabled");
        return new BatchContext(this);
    }

    public void addToBatch(CacheEvictionEvent event) {
        if (batchMode.get()) {
            pendingEvictions.offer(event);
            batchSize.incrementAndGet();

            // Auto-flush if threshold reached
            if (batchSize.get() >= batchThreshold) {
                flushBatch();
            }
        } else {
            // Direct broadcast if not in batch mode
            cacheProvider.broadcastEviction(event);
        }
    }

    @Scheduled(fixedDelayString = "${hibernate.cache.batch.max-delay:100}")
    public void autoFlush() {
        if (!pendingEvictions.isEmpty()) {
            flushBatch();
        }
    }

    public void flushBatch() {
        if (pendingEvictions.isEmpty()) {
            return;
        }

        List<CacheEvictionEvent> batch = new ArrayList<>();
        CacheEvictionEvent event;
        while ((event = pendingEvictions.poll()) != null) {
            batch.add(event);
        }

        if (!batch.isEmpty()) {
            // Merge similar evictions
            Map<String, CacheEvictionEvent> merged = mergeSimilarEvictions(batch);

            log.debug("✔ Flushing batch: {} events merged to {}",
                    batch.size(), merged.size());

            // Broadcast merged events
            merged.values().forEach(cacheProvider::broadcastEviction);

            batchSize.set(0);
        }
    }

    private Map<String, CacheEvictionEvent> mergeSimilarEvictions(List<CacheEvictionEvent> events) {
        Map<String, CacheEvictionEvent> merged = new LinkedHashMap<>();

        for (CacheEvictionEvent event : events) {
            String key = event.getEntityClass() + ":" + event.getRegion();

            if (merged.containsKey(key)) {
                // Merge with existing
                CacheEvictionEvent existing = merged.get(key);
                if (event.getEntityId() == null || existing.getEntityId() == null) {
                    // If either is a full eviction, keep full eviction
                    existing.setEntityId(null);
                }
            } else {
                merged.put(key, event);
            }
        }

        return merged;
    }

    public void endBatch() {
        flushBatch();
        batchMode.set(false);
        log.debug("✔ Batch eviction mode disabled");
    }

    /**
     * Context for batch operations with auto-close
     */
    public static class BatchContext implements AutoCloseable {
        private final BatchEvictionOptimizer optimizer;

        BatchContext(BatchEvictionOptimizer optimizer) {
            this.optimizer = optimizer;
        }

        @Override
        public void close() {
            optimizer.endBatch();
        }
    }

    /**
     * Statistics for batch operations
     */
    public Map<String, Object> getBatchStatistics() {
        return Map.of(
                "batchMode", batchMode.get(),
                "pendingEvictions", pendingEvictions.size(),
                "batchThreshold", batchThreshold,
                "maxDelayMs", maxDelayMs
        );
    }
}