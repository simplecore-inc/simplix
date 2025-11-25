package dev.simplecore.simplix.hibernate.cache.listener;

import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Cache;
import org.springframework.context.ApplicationEventPublisher;

/**
 * JPA Entity lifecycle listener for automatic cache eviction
 */
@Slf4j
@RequiredArgsConstructor
public class AutoCacheEvictionListener {

    private final HibernateCacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    @PostPersist
    public void handlePostPersist(Object entity) {
        evictCacheIfNeeded(entity, "INSERT");
    }

    @PostUpdate
    public void handlePostUpdate(Object entity) {
        evictCacheIfNeeded(entity, "UPDATE");
    }

    @PostRemove
    public void handlePostRemove(Object entity) {
        evictCacheIfNeeded(entity, "DELETE");
    }

    private void evictCacheIfNeeded(Object entity, String operation) {
        Class<?> entityClass = entity.getClass();

        // Check if entity has @Cache annotation
        if (!entityClass.isAnnotationPresent(Cache.class)) {
            return;
        }

        try {
            // Get entity ID
            Object entityId = getEntityId(entity);

            // Evict entity from cache
            if (entityId != null) {
                cacheManager.evictEntity(entityClass, entityId);
            }

            // Evict entire entity cache for this class
            cacheManager.evictEntityCache(entityClass);

            // Get cache region from annotation
            Cache cacheAnnotation = entityClass.getAnnotation(Cache.class);
            String region = cacheAnnotation.region();

            if (region != null && !region.isEmpty()) {
                cacheManager.evictRegion(region);
            }

            // Publish event for distributed cache sync
            publishCacheEvictionEvent(entityClass, entityId, region, operation);

            log.debug("✔ Auto-evicted cache for {} operation on {}", operation, entityClass.getSimpleName());

        } catch (Exception e) {
            log.error("✖ Failed to auto-evict cache for {}", entityClass.getSimpleName(), e);
        }
    }

    private Object getEntityId(Object entity) {
        try {
            // Try to find @Id field
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != Object.class) {
                for (var field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                        field.setAccessible(true);
                        return field.get(entity);
                    }
                }
                clazz = clazz.getSuperclass();
            }

            // Try getId() method
            var getIdMethod = entity.getClass().getMethod("getId");
            return getIdMethod.invoke(entity);

        } catch (Exception e) {
            log.debug("Could not extract entity ID: {}", e.getMessage());
            return null;
        }
    }

    private void publishCacheEvictionEvent(Class<?> entityClass, Object entityId, String region, String operation) {
        CacheEvictionEvent event = CacheEvictionEvent.builder()
                .entityClass(entityClass.getName())
                .entityId(entityId != null ? entityId.toString() : null)
                .region(region)
                .operation(operation)
                .timestamp(System.currentTimeMillis())
                .build();

        eventPublisher.publishEvent(event);
    }
}