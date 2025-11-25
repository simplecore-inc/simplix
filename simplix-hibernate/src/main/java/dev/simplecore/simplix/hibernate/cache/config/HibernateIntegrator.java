package dev.simplecore.simplix.hibernate.cache.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

/**
 * Hibernate Integrator for automatic cache eviction
 * Automatically registered via SPI
 */
@Slf4j
public class HibernateIntegrator implements Integrator {

    // Hibernate 6 signature
    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {

        final EventListenerRegistry eventListenerRegistry =
                sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

        registerEventListeners(eventListenerRegistry);
    }

    private void registerEventListeners(EventListenerRegistry eventListenerRegistry) {

        if (eventListenerRegistry == null) {
            log.warn("⚠ EventListenerRegistry not available, cache eviction listeners not registered");
            return;
        }

        log.info("✔ Registering Hibernate cache eviction listeners");

        // Register post-insert listener
        eventListenerRegistry.appendListeners(EventType.POST_INSERT,
                new PostInsertEventListener() {
                    @Override
                    public void onPostInsert(PostInsertEvent event) {
                        evictCache(event.getEntity(), event.getId(), "INSERT");
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return false;
                    }
                });

        // Register post-update listener
        eventListenerRegistry.appendListeners(EventType.POST_UPDATE,
                new PostUpdateEventListener() {
                    @Override
                    public void onPostUpdate(PostUpdateEvent event) {
                        evictCache(event.getEntity(), event.getId(), "UPDATE");
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return false;
                    }
                });

        // Register post-delete listener
        eventListenerRegistry.appendListeners(EventType.POST_DELETE,
                new PostDeleteEventListener() {
                    @Override
                    public void onPostDelete(PostDeleteEvent event) {
                        evictCache(event.getEntity(), event.getId(), "DELETE");
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return false;
                    }
                });
    }

    private void evictCache(Object entity, Object id, String operation) {
        if (entity == null) return;

        Class<?> entityClass = entity.getClass();

        // Check if entity has cache annotation
        if (!entityClass.isAnnotationPresent(org.hibernate.annotations.Cache.class)) {
            return;
        }

        try {
            // Get the SessionFactory's cache
            org.hibernate.Cache cache = HibernateCacheHolder.getCache();
            if (cache != null) {
                // Evict from second-level cache
                cache.evict(entityClass, id);
                cache.evict(entityClass);

                // Get cache region
                var cacheAnnotation = entityClass.getAnnotation(org.hibernate.annotations.Cache.class);
                if (cacheAnnotation != null && cacheAnnotation.region() != null) {
                    cache.evictRegion(cacheAnnotation.region());
                }

                log.debug("✔ Evicted cache via Hibernate integrator for {} on {}",
                        operation, entityClass.getSimpleName());
            }
        } catch (Exception e) {
            log.error("✖ Failed to evict cache via integrator", e);
        }
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
                              SessionFactoryServiceRegistry serviceRegistry) {
        log.info("✔ Hibernate cache eviction listeners removed");
    }
}