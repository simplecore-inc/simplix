package dev.simplecore.simplix.hibernate.cache.transaction;

import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheHolder;
import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.event.PendingEvictionCompletedEvent;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects pending cache evictions during a transaction and publishes them after commit.
 *
 * <p>This component ensures cache eviction only happens after the database transaction
 * successfully commits. If the transaction rolls back, all collected evictions are
 * discarded, maintaining cache-database consistency.</p>
 *
 * <h3>How It Works</h3>
 * <ol>
 *   <li>Entity changes are detected by HibernateIntegrator (POST_COMMIT events)</li>
 *   <li>Changes are collected via {@link #collect(PendingEviction)} into ThreadLocal storage</li>
 *   <li>A TransactionSynchronization is registered on first collection</li>
 *   <li>On commit: {@link PendingEvictionCompletedEvent} is published</li>
 *   <li>On rollback: Collected evictions are silently discarded</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses ThreadLocal to isolate pending evictions per transaction thread.
 * Each transaction has its own independent list of pending evictions.</p>
 *
 * <h3>Memory Safety</h3>
 * <p>To prevent OOM in large batch operations, pending evictions are limited to
 * {@link #MAX_PENDING_EVICTIONS}. When exceeded, entire entity cache is evicted instead.</p>
 *
 * @see PendingEviction
 * @see PendingEvictionCompletedEvent
 * @see dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler
 */
@Slf4j
@RequiredArgsConstructor
public class TransactionAwareCacheEvictionCollector {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Maximum number of pending evictions per transaction to prevent OOM.
     * When exceeded, switches to bulk eviction strategy.
     */
    private static final int MAX_PENDING_EVICTIONS = 10000;

    /**
     * Maximum retry attempts for event publishing.
     * Single retry for transient failures (network hiccup, temporary thread contention).
     */
    private static final int MAX_PUBLISH_RETRY_ATTEMPTS = 2;

    /**
     * ThreadLocal storage for pending evictions per transaction.
     * Each thread (transaction) has its own list.
     * Uses plain ThreadLocal to avoid auto-initialization on get().
     */
    private static final ThreadLocal<List<PendingEviction>> PENDING_EVICTIONS = new ThreadLocal<>();

    /**
     * Tracks whether TransactionSynchronization has been registered for current transaction.
     * Prevents duplicate registration.
     * Uses plain ThreadLocal to avoid auto-initialization on get().
     */
    private static final ThreadLocal<Boolean> SYNCHRONIZATION_REGISTERED = new ThreadLocal<>();

    /**
     * Collects a pending cache eviction to be executed after transaction commit.
     *
     * <p>If called outside of a transaction, the eviction is executed immediately
     * (fallback for non-transactional operations).</p>
     *
     * @param eviction the pending eviction information
     */
    public void collect(PendingEviction eviction) {
        if (eviction == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // No active transaction - publish immediately as fallback
            // Also ensure ThreadLocal is cleaned up to prevent memory leaks
            log.debug("ℹ No active transaction, publishing eviction immediately for {}",
                    getSimpleClassName(eviction.getEntityClassName()));

            try {
                publishImmediately(eviction);
            } finally {
                // Clean up any stale ThreadLocal data from previous operations
                cleanup();
            }
            return;
        }

        // Check synchronization availability BEFORE collecting eviction
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("⚠ Transaction synchronization not active, publishing eviction immediately for {}",
                    eviction.getEntityClassName());
            try {
                publishImmediately(eviction);
            } finally {
                // Clean up any stale ThreadLocal data to prevent memory leaks
                cleanup();
            }
            return;
        }

        // Initialize ThreadLocal list if not exists
        List<PendingEviction> pendingList = PENDING_EVICTIONS.get();
        if (pendingList == null) {
            pendingList = new ArrayList<>();
            PENDING_EVICTIONS.set(pendingList);
        }

        // Check size limit to prevent OOM in large batch operations
        if (pendingList.size() >= MAX_PENDING_EVICTIONS) {
            log.warn("⚠ Pending evictions limit ({}) reached, switching to bulk eviction for {}",
                    MAX_PENDING_EVICTIONS, eviction.getEntityClassName());
            // Convert to bulk eviction (null entityId means evict entire cache)
            eviction = PendingEviction.of(
                    eviction.getEntityClass(),
                    null,
                    eviction.getRegion(),
                    PendingEviction.EvictionOperation.BULK_UPDATE);
        }

        // Add to pending list
        pendingList.add(eviction);
        log.debug("✔ Collected pending eviction: {} [{}] operation={}",
                getSimpleClassName(eviction.getEntityClassName()),
                eviction.getEntityId(),
                eviction.getOperation());

        // Register synchronization if not already registered
        registerSynchronizationIfNeeded();
    }

    /**
     * Registers TransactionSynchronization to handle commit/rollback.
     * Only registers once per transaction.
     * Re-checks synchronization availability to prevent race condition.
     */
    private void registerSynchronizationIfNeeded() {
        // Safe null check for plain ThreadLocal
        if (Boolean.TRUE.equals(SYNCHRONIZATION_REGISTERED.get())) {
            return; // Already registered for this transaction
        }

        // Re-check synchronization is still active to prevent race condition
        // between initial check in collect() and registration here
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("⚠ Synchronization became inactive before registration, evictions may not be processed");
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                // CRITICAL FIX (9th review): Wrap in try-finally to ensure cleanup() is called
                // even if OutOfMemoryError, StackOverflowError, or other fatal errors occur.
                // Without this, afterCompletion() may not be called, leaving ThreadLocal dirty.
                try {
                    // Null-safe access to ThreadLocal
                    List<PendingEviction> evictions = PENDING_EVICTIONS.get();
                    if (evictions == null || evictions.isEmpty()) {
                        return;
                    }

                    log.debug("✔ Transaction committed, publishing {} pending evictions", evictions.size());

                    // Create immutable copy for the event
                    List<PendingEviction> evictionsCopy = new ArrayList<>(evictions);
                    PendingEvictionCompletedEvent event =
                            new PendingEvictionCompletedEvent(this, evictionsCopy);

                    // Simple retry for transient failures
                    Exception lastException = null;
                    for (int attempt = 1; attempt <= MAX_PUBLISH_RETRY_ATTEMPTS; attempt++) {
                        try {
                            eventPublisher.publishEvent(event);
                            if (attempt > 1) {
                                log.info("✔ Event published successfully on retry attempt {}", attempt);
                            }
                            return; // Success - exit early
                        } catch (Exception e) {
                            lastException = e;
                            if (attempt < MAX_PUBLISH_RETRY_ATTEMPTS) {
                                log.warn("⚠ Failed to publish eviction event (attempt {}/{}): {}",
                                        attempt, MAX_PUBLISH_RETRY_ATTEMPTS, e.getMessage());
                            }
                        }
                    }

                    // All retries exhausted - perform synchronous fallback eviction
                    log.error("✖ Failed to publish PendingEvictionCompletedEvent after {} attempts. " +
                            "Attempting fallback synchronous eviction for {} pending evictions.",
                            MAX_PUBLISH_RETRY_ATTEMPTS, evictionsCopy.size(), lastException);

                    performFallbackEviction(evictionsCopy);
                } finally {
                    // CRITICAL: Always cleanup ThreadLocal even if afterCommit() throws.
                    // This prevents ThreadLocal leaks in thread pools (Tomcat, HikariCP).
                    // afterCompletion() will also call cleanup() but may not be reached
                    // if a fatal error (OOM, SOE) occurs in afterCommit().
                    cleanup();
                }
            }

            @Override
            public void afterCompletion(int status) {
                // Always cleanup ThreadLocal after transaction completes (commit or rollback)
                try {
                    // Null-safe access to ThreadLocal
                    List<PendingEviction> evictions = PENDING_EVICTIONS.get();
                    int evictionCount = (evictions != null) ? evictions.size() : 0;
                    if (status == STATUS_ROLLED_BACK && evictionCount > 0) {
                        log.debug("ℹ Transaction rolled back, discarding {} pending evictions", evictionCount);
                    }
                } finally {
                    cleanup();
                }
            }
        });

        SYNCHRONIZATION_REGISTERED.set(Boolean.TRUE);
        log.debug("✔ TransactionSynchronization registered for cache eviction");
    }

    /**
     * Performs fallback synchronous eviction when event publishing fails.
     * This ensures cache eviction happens even when Spring event mechanism fails.
     *
     * @param evictions the list of pending evictions to execute
     */
    private void performFallbackEviction(List<PendingEviction> evictions) {
        CacheEvictionStrategy strategy = HibernateCacheHolder.getEvictionStrategy();

        if (strategy == null) {
            log.error("✖ Fallback eviction failed: CacheEvictionStrategy not available. " +
                    "{} cache entries may be stale.", evictions.size());
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (PendingEviction pending : evictions) {
            if (pending == null || pending.getEntityClass() == null) {
                continue;
            }

            try {
                strategy.evict(pending.getEntityClass(), pending.getEntityId());
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("✖ Fallback eviction failed for {} [{}]: {}",
                        pending.getEntityClassName(), pending.getEntityId(), e.getMessage());
            }
        }

        if (failureCount > 0) {
            log.warn("⚠ Fallback eviction completed: {} success, {} failures", successCount, failureCount);
        } else {
            log.info("✔ Fallback eviction completed successfully: {} evictions performed", successCount);
        }
    }

    /**
     * Publishes eviction immediately when no transaction is active.
     * This is a fallback for non-transactional operations.
     * Includes simple retry for transient failures.
     */
    private void publishImmediately(PendingEviction eviction) {
        List<PendingEviction> singleEviction = List.of(eviction);
        PendingEvictionCompletedEvent event =
                new PendingEvictionCompletedEvent(this, singleEviction);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_PUBLISH_RETRY_ATTEMPTS; attempt++) {
            try {
                eventPublisher.publishEvent(event);
                if (attempt > 1) {
                    log.info("✔ Immediate event published successfully on retry attempt {}", attempt);
                }
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_PUBLISH_RETRY_ATTEMPTS) {
                    log.warn("⚠ Failed to publish immediate eviction event (attempt {}/{}): {}",
                            attempt, MAX_PUBLISH_RETRY_ATTEMPTS, e.getMessage());
                }
            }
        }

        log.error("✖ Failed to publish immediate eviction event after {} attempts. " +
                "Attempting fallback synchronous eviction.",
                MAX_PUBLISH_RETRY_ATTEMPTS, lastException);

        performFallbackEviction(singleEviction);
    }

    /**
     * Cleans up ThreadLocal storage after transaction completion.
     * Called after both commit and rollback to prevent memory leaks.
     */
    private void cleanup() {
        PENDING_EVICTIONS.remove();
        SYNCHRONIZATION_REGISTERED.remove();
    }

    /**
     * Returns the current number of pending evictions in this thread's transaction.
     * Useful for debugging and metrics.
     * Returns 0 if ThreadLocal is not initialized (no active transaction context).
     *
     * @return count of pending evictions, or 0 if no transaction context
     */
    public int getPendingCount() {
        List<PendingEviction> evictions = PENDING_EVICTIONS.get();
        return (evictions != null) ? evictions.size() : 0;
    }

    /**
     * Checks if there are any pending evictions in this thread's transaction.
     * Returns false if ThreadLocal is not initialized (no active transaction context).
     *
     * @return true if there are pending evictions
     */
    public boolean hasPendingEvictions() {
        List<PendingEviction> evictions = PENDING_EVICTIONS.get();
        return evictions != null && !evictions.isEmpty();
    }

    /**
     * Extracts simple class name from fully qualified class name.
     * Returns "Unknown" for null or empty input.
     *
     * @param className fully qualified class name or simple class name
     * @return simple class name without package prefix
     */
    private static String getSimpleClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "Unknown";
        }
        int lastDotIndex = className.lastIndexOf('.');
        // If no dot found (-1), return entire string (already simple name)
        // Otherwise return substring after the last dot
        return lastDotIndex < 0 ? className : className.substring(lastDotIndex + 1);
    }
}
