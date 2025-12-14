package dev.simplecore.simplix.hibernate.cache.strategy;

import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Strategy for cache eviction.
 *
 * <p>This class handles the actual cache eviction after transaction commit.
 * It is called by {@link dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler}
 * to perform local cache eviction.</p>
 *
 * <p>For distributed cache synchronization, use Hibernate's native integration with
 * distributed cache providers (Hazelcast, Infinispan, etc.) which handle cluster-wide
 * cache invalidation automatically.</p>
 *
 * @see dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler
 */
@Slf4j
@RequiredArgsConstructor
public class CacheEvictionStrategy {

    private final HibernateCacheManager cacheManager;

    /**
     * Evict cache for the given entity.
     *
     * @param entityClass the entity class to evict
     * @param entityId the entity ID (null for bulk eviction)
     */
    public void evict(Class<?> entityClass, Object entityId) {
        if (entityClass == null) {
            log.warn("⚠ Cannot evict cache: entity class is null");
            return;
        }

        try {
            if (entityId != null) {
                // Single entity eviction
                cacheManager.evictEntity(entityClass, entityId);
                log.debug("✔ Evicted entity cache: {}[{}]", entityClass.getSimpleName(), entityId);
            } else {
                // Bulk eviction - evict entire entity cache
                cacheManager.evictEntityCache(entityClass);
                log.debug("✔ Evicted all entity cache: {}", entityClass.getSimpleName());
            }
        } catch (Exception e) {
            log.error("✖ Cache eviction failed for {}: {}", entityClass.getSimpleName(), e.getMessage());
        }
    }

    /**
     * Evict cache for the given entity class name.
     *
     * @param entityClassName the fully qualified entity class name
     * @param entityId the entity ID (null for bulk eviction)
     */
    public void evict(String entityClassName, Object entityId) {
        if (entityClassName == null || entityClassName.isEmpty()) {
            log.warn("⚠ Cannot evict cache: entity class name is null or empty");
            return;
        }

        try {
            Class<?> entityClass = loadEntityClass(entityClassName);
            if (entityClass != null) {
                evict(entityClass, entityId);
            } else {
                log.warn("⚠ Entity class not found: {}", entityClassName);
            }
        } catch (Exception e) {
            log.error("✖ Cache eviction failed for {}: {}", entityClassName, e.getMessage());
        }
    }

    /**
     * Loads entity class using multiple ClassLoader strategies.
     */
    private Class<?> loadEntityClass(String className) {
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        if (contextCL != null) {
            try {
                return Class.forName(className, false, contextCL);
            } catch (ClassNotFoundException e) {
                // Fall through to next ClassLoader
            }
        }

        try {
            return Class.forName(className, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
