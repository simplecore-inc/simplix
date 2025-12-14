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

    /**
     * Maximum queue size to prevent unbounded memory growth.
     */
    private static final int MAX_QUEUE_SIZE = 10000;

    /**
     * Maximum allowed batch threshold to prevent configuration errors.
     * Higher values could delay evictions excessively.
     */
    private static final int MAX_BATCH_THRESHOLD = 1000;

    /**
     * Maximum allowed delay in milliseconds to prevent configuration errors.
     * Higher values could cause excessive stale cache duration.
     */
    private static final long MAX_DELAY_MS = 60000; // 1 minute

    private volatile CacheProvider cacheProvider;
    private final CacheProviderFactory providerFactory;
    private final Queue<CacheEvictionEvent> pendingEvictions = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean batchMode = new AtomicBoolean(false);
    private final AtomicInteger batchSize = new AtomicInteger(0);

    /**
     * Shutdown flag to prevent scheduled task execution after shutdown (9th review fix).
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Batch depth counter for nested batch context support (9th review fix).
     * Allows multiple startBatch() calls, only ending batch when all contexts close.
     */
    private final AtomicInteger batchDepth = new AtomicInteger(0);

    @Value("${simplix.hibernate.cache.batch.threshold:10}")
    private int batchThreshold;

    @Value("${simplix.hibernate.cache.batch.max-delay:100}")
    private long maxDelayMs;

    @Value("${simplix.hibernate.cache.provider.type:AUTO}")
    private String providerType;

    @Autowired
    public BatchEvictionOptimizer(CacheProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // Validate configuration values with both lower and upper bounds (7th review fix)
        if (batchThreshold <= 0) {
            log.warn("⚠ Invalid batch threshold ({}), using default of 10", batchThreshold);
            batchThreshold = 10;
        } else if (batchThreshold > MAX_BATCH_THRESHOLD) {
            log.warn("⚠ Batch threshold ({}) exceeds maximum ({}), clamping to maximum",
                    batchThreshold, MAX_BATCH_THRESHOLD);
            batchThreshold = MAX_BATCH_THRESHOLD;
        }

        if (maxDelayMs <= 0) {
            log.warn("⚠ Invalid max delay ({}), using default of 100ms", maxDelayMs);
            maxDelayMs = 100;
        } else if (maxDelayMs > MAX_DELAY_MS) {
            log.warn("⚠ Max delay ({}) exceeds maximum ({}), clamping to maximum",
                    maxDelayMs, MAX_DELAY_MS);
            maxDelayMs = MAX_DELAY_MS;
        }

        // Select the active provider
        if ("AUTO".equalsIgnoreCase(providerType)) {
            this.cacheProvider = providerFactory.selectBestAvailable();
        } else {
            this.cacheProvider = providerFactory.getProvider(providerType);
        }

        if (this.cacheProvider == null) {
            log.warn("⚠ No cache provider available for batch eviction optimizer");
        }
    }

    /**
     * Proper shutdown to flush pending evictions and clear resources (8th review fix).
     * Sets shutdown flag first to prevent scheduled task execution (9th review fix).
     */
    @jakarta.annotation.PreDestroy
    public synchronized void shutdown() {
        // Set shutdown flag first to prevent scheduled tasks from running
        if (shutdown.getAndSet(true)) {
            return; // Already shutdown
        }

        log.info("ℹ Shutting down BatchEvictionOptimizer...");

        // Flush any pending evictions before shutdown
        if (!pendingEvictions.isEmpty()) {
            log.info("ℹ Flushing {} pending evictions before shutdown", batchSize.get());
            try {
                flushBatch();
            } catch (Exception e) {
                log.error("✖ Failed to flush pending evictions during shutdown", e);
            }
        }

        // Clear state
        batchMode.set(false);
        batchDepth.set(0);
        cacheProvider = null;

        log.info("✔ BatchEvictionOptimizer shutdown complete");
    }

    /**
     * Enable batch mode for bulk operations.
     * Supports nested batch contexts via depth counter (9th review fix).
     * Synchronized to ensure atomic transition with addToBatch().
     */
    public synchronized BatchContext startBatch() {
        int depth = batchDepth.incrementAndGet();

        // Only enable batch mode on first startBatch call
        if (depth == 1) {
            batchMode.set(true);
            log.debug("✔ Batch eviction mode enabled");
        } else {
            log.debug("ℹ Nested batch context created (depth: {})", depth);
        }

        return new BatchContext(this);
    }

    /**
     * Synchronized with flushBatch() to prevent counter race conditions.
     */
    public synchronized void addToBatch(CacheEvictionEvent event) {
        // Check shutdown flag first
        if (shutdown.get()) {
            log.debug("ℹ Ignoring event during shutdown");
            return;
        }

        // Null check for event
        if (event == null) {
            log.warn("⚠ Null event received, skipping");
            return;
        }

        if (batchMode.get()) {
            // Check queue size limit to prevent OOM
            if (batchSize.get() >= MAX_QUEUE_SIZE) {
                log.warn("⚠ Batch queue full ({}), forcing flush", MAX_QUEUE_SIZE);
                flushBatch();
            }

            pendingEvictions.offer(event);
            int currentSize = batchSize.incrementAndGet();

            // Auto-flush if threshold reached - use the value we just incremented
            if (currentSize >= batchThreshold) {
                flushBatch();
            }
        } else {
            // Direct broadcast if not in batch mode
            CacheProvider provider = cacheProvider;
            if (provider != null) {
                provider.broadcastEviction(event);
            } else {
                log.warn("⚠ No cache provider available, skipping eviction broadcast");
            }
        }
    }

    /**
     * Auto-flush pending evictions periodically.
     * Checks shutdown flag first to prevent execution after shutdown (9th review fix).
     * Synchronized to ensure consistent check-then-act with isEmpty().
     */
    @Scheduled(fixedDelayString = "${simplix.hibernate.cache.batch.max-delay:100}")
    public synchronized void autoFlush() {
        // Check shutdown flag first to prevent scheduled execution after shutdown
        if (shutdown.get()) {
            return;
        }

        if (!pendingEvictions.isEmpty()) {
            try {
                flushBatch();
            } catch (Exception e) {
                // Only log errors if not in shutdown - suppress noise during shutdown
                if (!shutdown.get()) {
                    log.error("✖ Auto-flush failed", e);
                }
            }
        }
    }

    /**
     * Synchronized to prevent race condition with addToBatch().
     * Counter is decremented by actual polled count to prevent lost updates.
     */
    public synchronized void flushBatch() {
        if (pendingEvictions.isEmpty()) {
            return;
        }

        List<CacheEvictionEvent> batch = new ArrayList<>();
        CacheEvictionEvent event;
        while ((event = pendingEvictions.poll()) != null) {
            batch.add(event);
        }

        if (!batch.isEmpty()) {
            // Decrement by actual count polled, not reset to 0
            // This prevents losing count of events added during flush
            batchSize.addAndGet(-batch.size());

            // Merge similar evictions
            Map<String, CacheEvictionEvent> merged = mergeSimilarEvictions(batch);

            log.debug("✔ Flushing batch: {} events merged to {}",
                    batch.size(), merged.size());

            // Capture to local variable for thread-safe iteration
            CacheProvider provider = cacheProvider;
            if (provider != null) {
                merged.values().forEach(provider::broadcastEviction);
            } else {
                log.warn("⚠ No cache provider available, {} evictions skipped", merged.size());
            }
        }
    }

    private Map<String, CacheEvictionEvent> mergeSimilarEvictions(List<CacheEvictionEvent> events) {
        Map<String, CacheEvictionEvent> merged = new LinkedHashMap<>();

        for (CacheEvictionEvent event : events) {
            // Skip events with null entityClass to prevent NPE
            if (event == null || event.getEntityClass() == null) {
                log.warn("⚠ Skipping eviction event with null entityClass");
                continue;
            }

            // Use special marker for null region to avoid key collision (7th review fix)
            // null region becomes "_DEFAULT_REGION_", actual regions use their values
            String regionKey = event.getRegion() != null ? event.getRegion() : "_DEFAULT_REGION_";
            String key = event.getEntityClass() + ":" + regionKey;

            if (merged.containsKey(key)) {
                // Merge with existing - create a copy to avoid modifying original (H7 fix)
                CacheEvictionEvent existing = merged.get(key);
                if (event.getEntityId() == null || existing.getEntityId() == null) {
                    // If either is a full eviction, create bulk eviction event
                    // Use event's region (not existing's) if event has non-null region and existing is null
                    String mergedRegion = existing.getRegion() != null ? existing.getRegion() : event.getRegion();
                    CacheEvictionEvent bulkEvent = CacheEvictionEvent.builder()
                            .entityClass(existing.getEntityClass())
                            .entityId(null) // Bulk eviction
                            .nodeId(existing.getNodeId())
                            .region(mergedRegion)
                            .timestamp(existing.getTimestamp())
                            .operation(existing.getOperation())
                            .build();
                    merged.put(key, bulkEvent);
                }
            } else {
                merged.put(key, event);
            }
        }

        return merged;
    }

    /**
     * End batch mode and flush pending evictions.
     * Only flushes and disables when all nested contexts are closed (9th review fix).
     * Synchronized to ensure atomic transition with addToBatch().
     */
    public synchronized void endBatch() {
        int depth = batchDepth.get();

        if (depth <= 0) {
            log.warn("⚠ endBatch() called without matching startBatch()");
            return;
        }

        int newDepth = batchDepth.decrementAndGet();

        // Only flush and disable batch mode when all contexts are closed
        if (newDepth == 0) {
            flushBatch();
            batchMode.set(false);
            log.debug("✔ Batch eviction mode disabled");
        } else {
            log.debug("ℹ Batch context closed (remaining depth: {})", newDepth);
        }
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
     * Statistics for batch operations.
     * Uses atomic counter instead of queue.size() for O(1) and accuracy.
     */
    public Map<String, Object> getBatchStatistics() {
        return Map.of(
                "batchMode", batchMode.get(),
                "batchDepth", batchDepth.get(),
                "pendingEvictions", batchSize.get(),
                "batchThreshold", batchThreshold,
                "maxDelayMs", maxDelayMs,
                "shutdown", shutdown.get()
        );
    }
}