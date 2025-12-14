package dev.simplecore.simplix.hibernate.cache.resilience;

import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheProperties;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles retry logic for failed cache evictions.
 *
 * <p>Failed evictions are queued for retry up to a maximum number of attempts.
 * After max attempts, they are moved to a Dead Letter Queue (DLQ) for manual review.
 * Both retry queue and DLQ have size limits to prevent unbounded memory growth.</p>
 *
 * <h3>Lock Ordering Convention</h3>
 * <p>To prevent deadlock, locks must always be acquired in this order:</p>
 * <ol>
 *   <li>{@code retryQueueLock} - for retry queue operations</li>
 *   <li>{@code dlqLock} - for dead letter queue operations</li>
 * </ol>
 * <p><strong>Important:</strong> Never hold dlqLock while trying to acquire retryQueueLock.
 * Current code avoids nested locks by releasing one before acquiring the other.</p>
 */
@Slf4j
public class EvictionRetryHandler {

    /**
     * Maximum size of the Dead Letter Queue to prevent memory issues.
     */
    private static final int MAX_DLQ_SIZE = 1000;

    /**
     * Maximum size of the retry queue to prevent OOM.
     */
    private static final int MAX_RETRY_QUEUE_SIZE = 5000;

    /**
     * Maximum allowed retry attempts to prevent infinite retry loops (7th review fix).
     * Reasonable upper bound prevents configuration errors from causing memory issues.
     */
    private static final int MAX_RETRY_ATTEMPTS = 100;

    private volatile CacheProvider cacheProvider;
    private final CacheProviderFactory providerFactory;
    private final HibernateCacheProperties properties;
    private final Queue<FailedEviction> retryQueue = new ConcurrentLinkedQueue<>();
    private final Queue<FailedEviction> deadLetterQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger retryQueueSize = new AtomicInteger(0);
    private final AtomicInteger dlqSize = new AtomicInteger(0);

    /**
     * Lock object for synchronized DLQ operations.
     */
    private final Object dlqLock = new Object();

    /**
     * Lock object for synchronized retry queue operations.
     */
    private final Object retryQueueLock = new Object();

    public EvictionRetryHandler(CacheProviderFactory providerFactory, HibernateCacheProperties properties) {
        this.providerFactory = providerFactory;
        this.properties = properties;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.cacheProvider = providerFactory.selectBestAvailable();
        if (this.cacheProvider == null) {
            log.warn("⚠ No cache provider available for eviction retry handler");
        }
    }

    /**
     * Proper shutdown to clear queues and prevent orphaned retries.
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("ℹ Shutting down EvictionRetryHandler...");

        // Clear retry queue
        int retryCount = retryQueueSize.get();
        retryQueue.clear();
        retryQueueSize.set(0);

        // Clear DLQ
        int dlqCount;
        synchronized (dlqLock) {
            dlqCount = dlqSize.get();
            deadLetterQueue.clear();
            dlqSize.set(0);
        }

        if (retryCount > 0 || dlqCount > 0) {
            log.warn("⚠ EvictionRetryHandler shutdown: {} retry items and {} DLQ items discarded",
                    retryCount, dlqCount);
        }

        cacheProvider = null;
        log.info("✔ EvictionRetryHandler shutdown complete");
    }

    public void scheduleRetry(CacheEvictionEvent event, Exception error) {
        if (event == null) {
            log.warn("⚠ Null event received for retry scheduling");
            return;
        }

        String errorMessage = (error != null) ? error.getClass().getSimpleName() + ": " + error.getMessage() : "Unknown error";
        FailedEviction failed = new FailedEviction(event, errorMessage);

        // Synchronized to prevent TOCTOU race between size check and add
        synchronized (retryQueueLock) {
            if (retryQueueSize.get() >= MAX_RETRY_QUEUE_SIZE) {
                log.error("✖ Retry queue full ({}), discarding eviction for: {}",
                        MAX_RETRY_QUEUE_SIZE, event.getEntityClass());
                return;
            }
            retryQueue.offer(failed);
            retryQueueSize.incrementAndGet();
        }

        log.warn("⚠ Scheduled retry for failed eviction: {} - {}",
                event.getEntityClass(), errorMessage);
    }

    @Scheduled(fixedDelayString = "${simplix.hibernate.cache.retry.delay-ms:1000}")
    public void processRetries() {
        if (retryQueue.isEmpty()) {
            return;
        }

        // Capture to local variable to prevent TOCTOU race condition
        CacheProvider provider = cacheProvider;

        Queue<FailedEviction> currentBatch = new ConcurrentLinkedQueue<>();
        FailedEviction failed;

        // Process current batch and track size
        while ((failed = retryQueue.poll()) != null) {
            retryQueueSize.decrementAndGet();
            currentBatch.offer(failed);
        }

        int successCount = 0;
        int failureCount = 0;
        // Clamp retry attempts to valid range to prevent infinite loops (7th review fix)
        int configuredAttempts = properties.getRetry().getMaxAttempts();
        int maxRetryAttempts = Math.max(1, Math.min(configuredAttempts, MAX_RETRY_ATTEMPTS));

        for (FailedEviction eviction : currentBatch) {
            if (eviction.getAttempts() >= maxRetryAttempts) {
                // Synchronized DLQ operations to prevent race condition
                moveToDlq(eviction);
                failureCount++;
                continue;
            }

            try {
                // Retry the eviction
                if (provider == null) {
                    log.warn("⚠ No cache provider available for retry");
                    requeue(eviction);
                    failureCount++;
                    continue;
                }
                provider.broadcastEviction(eviction.getEvent());
                successCount++;
                log.debug("✔ Retry successful for: {}",
                        eviction.getEvent().getEntityClass());

            } catch (Exception e) {
                // Increment attempts and re-queue
                eviction.incrementAttempts();
                eviction.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
                requeue(eviction);
                failureCount++;

                log.warn("⚠ Retry {} failed for {}: {}",
                        eviction.getAttempts(),
                        eviction.getEvent().getEntityClass(),
                        e.getMessage());
            }
        }

        // Log batch processing summary
        if (successCount > 0 || failureCount > 0) {
            log.info("ℹ Retry batch processed: {} successful, {} failed/requeued", successCount, failureCount);
        }
    }

    /**
     * Synchronized move to DLQ to ensure size limit is respected atomically.
     */
    private void moveToDlq(FailedEviction eviction) {
        synchronized (dlqLock) {
            // Remove oldest entries if DLQ is full
            while (dlqSize.get() >= MAX_DLQ_SIZE) {
                FailedEviction removed = deadLetterQueue.poll();
                if (removed != null) {
                    dlqSize.decrementAndGet();
                    log.warn("⚠ DLQ full, discarding oldest entry: {}",
                            removed.getEvent().getEntityClass());
                } else {
                    // Queue is empty but counter shows full - indicates counter drift
                    // Just break and let the new addition correct the imbalance
                    // Don't force reset as concurrent operations may be in progress
                    log.warn("⚠ DLQ counter mismatch detected (counter: {}, queue empty)",
                            dlqSize.get());
                    break;
                }
            }
            deadLetterQueue.offer(eviction);
            dlqSize.incrementAndGet();
        }
        log.error("✖ Max retries exceeded, moved to DLQ: {}",
                eviction.getEvent().getEntityClass());
    }

    /**
     * Re-queue with size tracking using synchronized block.
     */
    private void requeue(FailedEviction eviction) {
        synchronized (retryQueueLock) {
            if (retryQueueSize.get() < MAX_RETRY_QUEUE_SIZE) {
                retryQueue.offer(eviction);
                retryQueueSize.incrementAndGet();
            } else {
                log.error("✖ Retry queue full, discarding eviction: {}",
                        eviction.getEvent().getEntityClass());
            }
        }
    }

    /**
     * Process dead letter queue - could be exposed via admin endpoint.
     * Synchronized to prevent race with moveToDlq.
     * Uses retryQueueLock for atomic retry queue operations.
     */
    public void reprocessDeadLetterQueue() {
        Queue<FailedEviction> dlqCopy;
        synchronized (dlqLock) {
            log.info("ℹ Reprocessing {} items from DLQ", dlqSize.get());
            dlqCopy = new ConcurrentLinkedQueue<>(deadLetterQueue);
            deadLetterQueue.clear();
            dlqSize.set(0);
        }

        for (FailedEviction eviction : dlqCopy) {
            eviction.resetAttempts();
            // Use synchronized block for atomic check-and-add
            synchronized (retryQueueLock) {
                if (retryQueueSize.get() < MAX_RETRY_QUEUE_SIZE) {
                    retryQueue.offer(eviction);
                    retryQueueSize.incrementAndGet();
                } else {
                    log.warn("⚠ Retry queue full during DLQ reprocess, item discarded: {}",
                            eviction.getEvent().getEntityClass());
                }
            }
        }
    }

    public Map<String, Object> getRetryStatistics() {
        return Map.of(
                "retryQueueSize", retryQueueSize.get(),
                "deadLetterQueueSize", dlqSize.get(),
                "maxRetryQueueSize", MAX_RETRY_QUEUE_SIZE,
                "maxDlqSize", MAX_DLQ_SIZE,
                "maxRetryAttempts", properties.getRetry().getMaxAttempts(),
                "retryDelayMs", properties.getRetry().getDelayMs()
        );
    }

    @Data
    private static class FailedEviction {
        private final CacheEvictionEvent event;
        private final Instant failedAt = Instant.now();
        private String lastError;
        // Start at 0 - represents number of retry attempts made so far
        private final AtomicInteger retryCount = new AtomicInteger(0);

        public FailedEviction(CacheEvictionEvent event, String error) {
            this.event = event;
            this.lastError = error;
        }

        public void incrementAttempts() {
            // Prevent overflow by capping at Integer.MAX_VALUE (H8 fix)
            retryCount.updateAndGet(current ->
                    current < Integer.MAX_VALUE ? current + 1 : Integer.MAX_VALUE);
        }

        public int getAttempts() {
            return retryCount.get();
        }

        public void resetAttempts() {
            retryCount.set(0);
        }
    }
}