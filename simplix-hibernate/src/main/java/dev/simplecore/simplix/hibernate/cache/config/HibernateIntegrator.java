package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

/**
 * Hibernate Integrator for transaction-aware cache eviction.
 *
 * <p>This integrator registers event listeners that collect cache eviction requests
 * during a transaction. Actual eviction only happens after the transaction commits,
 * preventing cache-database inconsistency on rollback.</p>
 *
 * <h3>Key Design: Post-Commit Eviction</h3>
 * <p>By returning {@code true} from {@code requiresPostCommitHandling()}, the event
 * listeners are queued for execution after transaction commit. This ensures:</p>
 * <ul>
 *   <li>Cache is not evicted if transaction rolls back</li>
 *   <li>Other nodes receive eviction broadcast only after commit</li>
 *   <li>Consistent read-after-write semantics</li>
 * </ul>
 *
 * <h3>Integration with Spring</h3>
 * <p>Since this class is loaded via Hibernate SPI before Spring context initialization,
 * it uses {@link HibernateCacheHolder} to access the Spring-managed
 * {@link TransactionAwareCacheEvictionCollector}.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Hibernate loads this integrator via SPI during SessionFactory creation</li>
 *   <li>Event listeners are registered for POST_INSERT, POST_UPDATE, POST_DELETE</li>
 *   <li>Spring context initializes and sets collector in HibernateCacheHolder</li>
 *   <li>On entity change: eviction is collected (not executed)</li>
 *   <li>On transaction commit: collected evictions are published as event</li>
 *   <li>PostCommitCacheEvictionHandler executes actual eviction</li>
 * </ol>
 *
 * @see HibernateCacheHolder
 * @see TransactionAwareCacheEvictionCollector
 * @see dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler
 */
@Slf4j
public class HibernateIntegrator implements Integrator {

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {

        final EventListenerRegistry eventListenerRegistry =
                sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

        registerEventListeners(eventListenerRegistry);
    }

    private void registerEventListeners(EventListenerRegistry eventListenerRegistry) {

        if (eventListenerRegistry == null) {
            // This is a critical configuration error - cache eviction will not work
            log.error("✖ EventListenerRegistry not available! Cache eviction listeners cannot be registered. " +
                    "Hibernate L2 cache eviction will NOT work. Check Hibernate configuration.");
            return;
        }

        log.info("✔ Registering Hibernate cache eviction listeners (post-commit mode)");

        // Register post-insert listener
        eventListenerRegistry.appendListeners(EventType.POST_INSERT,
                new PostInsertEventListener() {
                    @Override
                    public void onPostInsert(PostInsertEvent event) {
                        collectEviction(event.getEntity(), event.getId(),
                                PendingEviction.EvictionOperation.INSERT);
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        // Return true to be called after transaction commit
                        return true;
                    }
                });

        // Register post-update listener
        eventListenerRegistry.appendListeners(EventType.POST_UPDATE,
                new PostUpdateEventListener() {
                    @Override
                    public void onPostUpdate(PostUpdateEvent event) {
                        collectEviction(event.getEntity(), event.getId(),
                                PendingEviction.EvictionOperation.UPDATE);
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return true;
                    }
                });

        // Register post-delete listener
        eventListenerRegistry.appendListeners(EventType.POST_DELETE,
                new PostDeleteEventListener() {
                    @Override
                    public void onPostDelete(PostDeleteEvent event) {
                        collectEviction(event.getEntity(), event.getId(),
                                PendingEviction.EvictionOperation.DELETE);
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return true;
                    }
                });
    }

    /**
     * Collects a pending cache eviction to be executed after transaction commit.
     *
     * <p>If the eviction collector is not yet available (during early startup),
     * attempts fallback to CacheEvictionStrategy for immediate eviction (9th review fix).
     * If neither is available, logs error - cache will be stale until TTL expiration.</p>
     *
     * @param entity the changed entity
     * @param id the entity ID
     * @param operation the type of operation (INSERT, UPDATE, DELETE)
     */
    private void collectEviction(Object entity, Object id, PendingEviction.EvictionOperation operation) {
        if (entity == null) {
            return;
        }

        Class<?> entityClass = entity.getClass();

        // Check if entity has cache annotation
        if (!entityClass.isAnnotationPresent(org.hibernate.annotations.Cache.class)) {
            return;
        }

        TransactionAwareCacheEvictionCollector collector = HibernateCacheHolder.getEvictionCollector();

        if (collector == null) {
            // Collector not yet initialized (early startup phase) - 9th review fix
            // Try fallback to CacheEvictionStrategy for immediate eviction
            CacheEvictionStrategy strategy = HibernateCacheHolder.getEvictionStrategy();

            if (strategy != null) {
                // Fallback: perform immediate eviction via strategy
                log.warn("⚠ Eviction collector not available for {} [{}]. " +
                        "Using fallback direct eviction via CacheEvictionStrategy.",
                        entityClass.getSimpleName(), id);
                try {
                    strategy.evict(entityClass, id);
                    log.debug("✔ Fallback eviction completed for {} [{}]", entityClass.getSimpleName(), id);
                } catch (Exception e) {
                    log.error("✖ Fallback eviction failed for {} [{}]: {}",
                            entityClass.getSimpleName(), id, e.getMessage());
                }
                return;
            }

            // Both collector and strategy are null - this is critical
            // This can happen during very early initialization before Spring context is ready
            log.error("✖ CRITICAL: Neither eviction collector nor strategy available for {} [{}]. " +
                    "Cache entry will be STALE until TTL expiration or next update. " +
                    "This indicates Hibernate SPI loaded before Spring context initialization.",
                    entityClass.getSimpleName(), id);
            return;
        }

        // Get cache region if specified
        String region = null;
        var cacheAnnotation = entityClass.getAnnotation(org.hibernate.annotations.Cache.class);
        if (cacheAnnotation != null && !cacheAnnotation.region().isEmpty()) {
            region = cacheAnnotation.region();
        }

        // Build and collect pending eviction using factory method for serialization safety
        PendingEviction pendingEviction = PendingEviction.of(entityClass, id, region, operation);

        collector.collect(pendingEviction);

        log.debug("✔ Collected eviction via Hibernate integrator for {} on {} [{}]",
                operation, entityClass.getSimpleName(), id);
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
                              SessionFactoryServiceRegistry serviceRegistry) {
        log.info("✔ Hibernate cache eviction listeners removed");
    }
}
