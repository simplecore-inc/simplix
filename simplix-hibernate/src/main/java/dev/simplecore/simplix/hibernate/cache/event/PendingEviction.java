package dev.simplecore.simplix.hibernate.cache.event;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Represents a pending cache eviction that will be executed after transaction commit.
 *
 * <p>This DTO holds information about an entity change that requires cache eviction.
 * Instead of immediately evicting the cache (which could cause inconsistency if the
 * transaction rolls back), evictions are collected during the transaction and executed
 * only after successful commit.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>@EvictCache annotated method triggers eviction collection</li>
 *   <li>PendingEviction created and collected by TransactionAwareCacheEvictionCollector</li>
 *   <li>On transaction commit: PendingEvictionCompletedEvent published</li>
 *   <li>PostCommitCacheEvictionHandler executes actual eviction</li>
 *   <li>On transaction rollback: PendingEviction discarded (no cache eviction)</li>
 * </ol>
 *
 * <h3>Serialization Safety</h3>
 * <p>This class uses String-based entity class name instead of Class reference
 * to ensure proper serialization across distributed cache providers (Redis, Hazelcast, Infinispan).
 * Use {@link #getEntityClass()} to resolve the actual Class when needed.</p>
 *
 * @see dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector
 * @see PendingEvictionCompletedEvent
 */
@Data
@Builder
public class PendingEviction implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * The fully qualified name of the entity class whose cache should be evicted.
     * Stored as String for safe serialization across distributed systems.
     */
    private String entityClassName;

    /**
     * The entity ID as String for serialization safety.
     * Null indicates a bulk operation (evict entire cache for this entity type).
     */
    private String entityId;

    /**
     * The cache region name. Null uses the default region.
     */
    private String region;

    /**
     * The type of operation that triggered this eviction.
     */
    private EvictionOperation operation;

    /**
     * Whether to also evict associated query cache regions.
     * Defaults to true for bulk operations.
     */
    @Builder.Default
    private boolean evictQueryCache = true;

    /**
     * Timestamp when the eviction was created (for debugging and metrics).
     */
    private long timestamp;

    /**
     * Types of operations that can trigger cache eviction.
     */
    public enum EvictionOperation {
        /**
         * New entity was inserted.
         */
        INSERT,

        /**
         * Existing entity was updated.
         */
        UPDATE,

        /**
         * Entity was deleted.
         */
        DELETE,

        /**
         * Bulk update via @Modifying query.
         */
        BULK_UPDATE,

        /**
         * Bulk delete via @Modifying query.
         */
        BULK_DELETE
    }

    /**
     * Resolves the entity Class from the stored class name.
     *
     * <p>Uses multiple ClassLoader strategies to handle different environments:
     * <ul>
     *   <li>Thread context ClassLoader (web applications, app servers)</li>
     *   <li>Current class ClassLoader (fallback)</li>
     * </ul>
     * This ensures proper class loading in multi-WAR deployments and
     * distributed cache scenarios.</p>
     *
     * @return the entity Class, or null if class cannot be found
     */
    public Class<?> getEntityClass() {
        if (entityClassName == null || entityClassName.isEmpty()) {
            return null;
        }

        // Try thread context ClassLoader first (handles web app / app server scenarios)
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        if (contextCL != null) {
            try {
                return Class.forName(entityClassName, false, contextCL);
            } catch (ClassNotFoundException e) {
                // Fall through to next ClassLoader
            }
        }

        // Fallback to current class's ClassLoader (more reliable in OSGi/app server)
        try {
            return Class.forName(entityClassName, false, PendingEviction.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            // Class truly not found in any ClassLoader
            return null;
        }
    }

    /**
     * Creates a PendingEviction from a Class and Object ID.
     * Converts to String-based storage for serialization safety.
     *
     * @param entityClass the entity class
     * @param entityId the entity ID (will be converted to String)
     * @param region the cache region
     * @param operation the eviction operation type
     * @return a new PendingEviction instance
     */
    public static PendingEviction of(Class<?> entityClass, Object entityId,
                                      String region, EvictionOperation operation) {
        return of(entityClass, entityId, region, operation, true);
    }

    /**
     * Creates a PendingEviction from a Class and Object ID with query cache control.
     * Converts to String-based storage for serialization safety.
     *
     * @param entityClass the entity class
     * @param entityId the entity ID (will be converted to String)
     * @param region the cache region
     * @param operation the eviction operation type
     * @param evictQueryCache whether to also evict query cache regions
     * @return a new PendingEviction instance
     */
    public static PendingEviction of(Class<?> entityClass, Object entityId,
                                      String region, EvictionOperation operation,
                                      boolean evictQueryCache) {
        return PendingEviction.builder()
                .entityClassName(entityClass != null ? entityClass.getName() : null)
                .entityId(entityId != null ? String.valueOf(entityId) : null)
                .region(region)
                .operation(operation)
                .evictQueryCache(evictQueryCache)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
