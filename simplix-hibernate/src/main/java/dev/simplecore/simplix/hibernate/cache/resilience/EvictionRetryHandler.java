package dev.simplecore.simplix.hibernate.cache.resilience;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles retry logic for failed cache evictions
 */
@Slf4j
public class EvictionRetryHandler {

    private CacheProvider cacheProvider;
    private final CacheProviderFactory providerFactory;
    private final Queue<FailedEviction> retryQueue = new ConcurrentLinkedQueue<>();
    private final Queue<FailedEviction> deadLetterQueue = new ConcurrentLinkedQueue<>();

    @Value("${hibernate.cache.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${hibernate.cache.retry.delay:1000}")
    private long retryDelayMs;

    public EvictionRetryHandler(CacheProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.cacheProvider = providerFactory.selectBestAvailable();
    }

    public void scheduleRetry(CacheEvictionEvent event, Exception error) {
        FailedEviction failed = new FailedEviction(event, error.getMessage());
        retryQueue.offer(failed);
        log.warn("⚠ Scheduled retry for failed eviction: {} - {}",
                event.getEntityClass(), error.getMessage());
    }

    @Scheduled(fixedDelayString = "${hibernate.cache.retry.delay:1000}")
    public void processRetries() {
        if (retryQueue.isEmpty()) {
            return;
        }

        Queue<FailedEviction> currentBatch = new ConcurrentLinkedQueue<>();
        FailedEviction failed;

        // Process current batch
        while ((failed = retryQueue.poll()) != null) {
            currentBatch.offer(failed);
        }

        for (FailedEviction eviction : currentBatch) {
            if (eviction.getAttempts() >= maxRetryAttempts) {
                // Move to dead letter queue
                deadLetterQueue.offer(eviction);
                log.error("✖ Max retries exceeded, moved to DLQ: {}",
                        eviction.getEvent().getEntityClass());
                continue;
            }

            try {
                // Retry the eviction
                cacheProvider.broadcastEviction(eviction.getEvent());
                log.info("✔ Retry successful for: {}",
                        eviction.getEvent().getEntityClass());

            } catch (Exception e) {
                // Increment attempts and re-queue
                eviction.incrementAttempts();
                eviction.setLastError(e.getMessage());
                retryQueue.offer(eviction);

                log.warn("⚠ Retry {} failed for {}: {}",
                        eviction.getAttempts(),
                        eviction.getEvent().getEntityClass(),
                        e.getMessage());
            }
        }
    }

    /**
     * Process dead letter queue - could be exposed via admin endpoint
     */
    public void reprocessDeadLetterQueue() {
        log.info("ℹ Reprocessing {} items from DLQ", deadLetterQueue.size());

        Queue<FailedEviction> dlqCopy = new ConcurrentLinkedQueue<>(deadLetterQueue);
        deadLetterQueue.clear();

        for (FailedEviction eviction : dlqCopy) {
            eviction.resetAttempts();
            retryQueue.offer(eviction);
        }
    }

    public Map<String, Object> getRetryStatistics() {
        return Map.of(
                "retryQueueSize", retryQueue.size(),
                "deadLetterQueueSize", deadLetterQueue.size(),
                "maxRetryAttempts", maxRetryAttempts,
                "retryDelayMs", retryDelayMs
        );
    }

    @Data
    private static class FailedEviction {
        private final CacheEvictionEvent event;
        private final Instant failedAt = Instant.now();
        private String lastError;
        private final AtomicInteger attempts = new AtomicInteger(1);

        public FailedEviction(CacheEvictionEvent event, String error) {
            this.event = event;
            this.lastError = error;
        }

        public void incrementAttempts() {
            attempts.incrementAndGet();
        }

        public int getAttempts() {
            return attempts.get();
        }

        public void resetAttempts() {
            attempts.set(0);
        }
    }
}