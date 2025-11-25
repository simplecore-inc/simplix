package dev.simplecore.simplix.hibernate.cache.listener;

import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Global JPA entity listener that intercepts ALL entity operations
 * Automatically registered to all entities via orm.xml
 */
@Slf4j
public class GlobalEntityListener {

    private static HibernateCacheManager cacheManager;
    private static ApplicationEventPublisher eventPublisher;
    private static EntityCacheScanner entityScanner;
    private static CacheEvictionStrategy evictionStrategy;

    @Autowired
    public void setCacheManager(HibernateCacheManager manager) {
        GlobalEntityListener.cacheManager = manager;
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher publisher) {
        GlobalEntityListener.eventPublisher = publisher;
    }

    @Autowired
    public void setEntityScanner(EntityCacheScanner scanner) {
        GlobalEntityListener.entityScanner = scanner;
    }

    @Autowired
    public void setEvictionStrategy(CacheEvictionStrategy strategy) {
        GlobalEntityListener.evictionStrategy = strategy;
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        handleCacheEviction(entity, "INSERT");
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        handleCacheEviction(entity, "UPDATE");
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        handleCacheEviction(entity, "DELETE");
    }

    @PreUpdate
    public void onPreUpdate(Object entity) {
        // Can be used to track dirty fields if needed
    }

    private void handleCacheEviction(Object entity, String operation) {
        if (entity == null) return;

        Class<?> entityClass = entity.getClass();

        // Check if entity has @Cache annotation
        if (!entityClass.isAnnotationPresent(Cache.class)) {
            return;
        }

        try {
            Object entityId = extractEntityId(entity);

            // Use strategy for eviction (handles local and distributed)
            if (evictionStrategy != null) {
                evictionStrategy.evict(entityClass, entityId);
            } else if (cacheManager != null) {
                // Fallback to direct cache manager
                if (entityId != null) {
                    cacheManager.evictEntity(entityClass, entityId);
                }
                cacheManager.evictEntityCache(entityClass);
            }

            // Publish event for additional processing
            if (eventPublisher != null) {
                publishCacheEvictionEvent(entityClass, entityId, operation);
            }

            log.debug("✔ Cache evicted for {} operation on {}",
                    operation, entityClass.getSimpleName());

        } catch (Exception e) {
            log.error("✖ Failed to evict cache for {}: {}",
                    entityClass.getSimpleName(), e.getMessage());
        }
    }

    private Object extractEntityId(Object entity) {
        try {
            Class<?> clazz = entity.getClass();

            // Try @Id field
            while (clazz != null && clazz != Object.class) {
                for (var field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class) ||
                            field.isAnnotationPresent(EmbeddedId.class)) {
                        field.setAccessible(true);
                        return field.get(entity);
                    }
                }
                clazz = clazz.getSuperclass();
            }

            // Try getId() method
            try {
                var method = entity.getClass().getMethod("getId");
                return method.invoke(entity);
            } catch (NoSuchMethodException ignored) {
            }

        } catch (Exception e) {
            log.debug("Could not extract entity ID: {}", e.getMessage());
        }
        return null;
    }

    private void publishCacheEvictionEvent(Class<?> entityClass, Object entityId, String operation) {
        Cache cacheAnnotation = entityClass.getAnnotation(Cache.class);
        String region = cacheAnnotation != null ? cacheAnnotation.region() : null;

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