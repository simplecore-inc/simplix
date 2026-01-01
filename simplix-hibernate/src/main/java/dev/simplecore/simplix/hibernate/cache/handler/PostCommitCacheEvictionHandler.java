package dev.simplecore.simplix.hibernate.cache.handler;

import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.event.PendingEvictionCompletedEvent;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * Handles cache eviction after transaction commit.
 *
 * <p>This handler receives {@link PendingEvictionCompletedEvent} which is published
 * after a transaction successfully commits. It then performs the actual cache
 * eviction through {@link CacheEvictionStrategy}.</p>
 *
 * <h3>Why Not @TransactionalEventListener?</h3>
 * <p>The {@link PendingEvictionCompletedEvent} is already published from within
 * {@code TransactionSynchronization.afterCommit()}, so we use a regular
 * {@code @EventListener} instead of {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 * The transaction-awareness is handled by the collector, not the handler.</p>
 *
 * <h3>Eviction Flow</h3>
 * <pre>
 * Transaction commits
 *     |
 *     v
 * TransactionSynchronization.afterCommit()
 *     |
 *     v
 * PendingEvictionCompletedEvent published
 *     |
 *     v
 * PostCommitCacheEvictionHandler.handlePostCommitEviction()
 *     |
 *     v
 * CacheEvictionStrategy.evict() for each pending eviction
 *     |
 *     +-- Local cache eviction
 *     +-- Distributed broadcast (if configured)
 * </pre>
 *
 * @see PendingEvictionCompletedEvent
 * @see dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector
 */
@Slf4j
@RequiredArgsConstructor
public class PostCommitCacheEvictionHandler {

    private final CacheEvictionStrategy evictionStrategy;

    /**
     * Handles cache eviction after transaction commit.
     *
     * <p>Processes all pending evictions from the committed transaction.
     * Each eviction is executed through the cache eviction strategy,
     * which handles both local and distributed cache invalidation.</p>
     *
     * @param event the event containing pending evictions
     */
    @EventListener
    public void handlePostCommitEviction(PendingEvictionCompletedEvent event) {
        if (event.getPendingEvictions().isEmpty()) {
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        log.trace("✔ Processing {} post-commit cache evictions", event.getEvictionCount());

        for (PendingEviction pending : event.getPendingEvictions()) {
            // Skip null entries to prevent NPE
            if (pending == null) {
                log.warn("⚠ Null pending eviction entry skipped");
                continue;
            }

            try {
                processEviction(pending);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                String entityName = getEntitySimpleName(pending);
                log.error("✖ Failed to evict cache for {} [{}]: {}",
                        entityName,
                        pending.getEntityId(),
                        e.getMessage());
            }
        }

        if (failureCount > 0) {
            log.warn("⚠ Completed post-commit eviction: {} success, {} failures",
                    successCount, failureCount);
        } else {
            log.trace("✔ Completed post-commit eviction: {} success", successCount);
        }
    }

    /**
     * Processes a single pending eviction.
     *
     * @param pending the pending eviction to process
     */
    private void processEviction(PendingEviction pending) {
        Class<?> entityClass = pending.getEntityClass();
        String entityId = pending.getEntityId();
        String region = pending.getRegion();

        if (entityClass == null) {
            log.warn("⚠ Cannot process eviction: entity class not found for {}",
                    pending.getEntityClassName());
            return;
        }

        log.trace("ℹ Evicting cache: {} [{}] operation={} region={}",
                entityClass.getSimpleName(),
                entityId,
                pending.getOperation(),
                region);

        // Handle bulk operations differently
        if (isBulkOperation(pending)) {
            // For bulk operations, evict entire entity cache
            evictionStrategy.evict(entityClass, null);
        } else {
            // For single entity operations, evict specific entity
            evictionStrategy.evict(entityClass, entityId);
        }
    }

    /**
     * Checks if the pending eviction is for a bulk operation.
     *
     * @param pending the pending eviction
     * @return true if this is a bulk update or bulk delete
     */
    private boolean isBulkOperation(PendingEviction pending) {
        return pending.getOperation() == PendingEviction.EvictionOperation.BULK_UPDATE
                || pending.getOperation() == PendingEviction.EvictionOperation.BULK_DELETE
                || pending.getEntityId() == null;
    }

    /**
     * Gets the simple name of the entity class, handling null safely.
     *
     * @param pending the pending eviction
     * @return the simple class name or "Unknown"
     */
    private String getEntitySimpleName(PendingEviction pending) {
        String className = pending.getEntityClassName();
        if (className == null || className.isEmpty()) {
            return "Unknown";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}
