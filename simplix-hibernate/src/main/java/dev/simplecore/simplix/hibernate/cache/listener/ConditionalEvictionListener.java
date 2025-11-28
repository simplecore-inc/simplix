package dev.simplecore.simplix.hibernate.cache.listener;

import dev.simplecore.simplix.hibernate.cache.annotation.CacheEvictionPolicy;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.monitoring.EvictionMetrics;
import jakarta.persistence.PreUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Conditional cache eviction based on dirty fields
 */
@Slf4j
@RequiredArgsConstructor
public class ConditionalEvictionListener {

    private final HibernateCacheManager cacheManager;
    private final EvictionMetrics metrics;

    @PreUpdate
    public void checkConditionalEviction(Object entity) {
        Class<?> entityClass = entity.getClass();
        CacheEvictionPolicy policy = entityClass.getAnnotation(CacheEvictionPolicy.class);

        if (policy == null) {
            // No policy defined, use default behavior
            return;
        }

        try {
            String[] dirtyFields = getDirtyFields(entity);

            if (shouldEvictCache(policy, entity, dirtyFields)) {
                log.debug("✔ Conditional eviction triggered for {} due to changes in: {}",
                        entityClass.getSimpleName(), Arrays.toString(dirtyFields));

                cacheManager.evictEntityCache(entityClass);
                metrics.recordLocalEviction(entityClass.getName());

                if (policy.evictQueryCache()) {
                    cacheManager.evictQueryRegion("query." + entityClass.getSimpleName().toLowerCase());
                }
            } else {
                log.debug("ℹ Skipping cache eviction for {} - only ignored fields changed",
                        entityClass.getSimpleName());
            }

        } catch (Exception e) {
            log.error("✖ Failed to check conditional eviction", e);
        }
    }

    @SuppressWarnings("deprecation")
    private String[] getDirtyFields(Object entity) {
        // Use Hibernate's dirty checking
        try {
            SessionImplementor session = (SessionImplementor) jakarta.persistence.Persistence
                    .createEntityManagerFactory("default")
                    .createEntityManager()
                    .unwrap(SessionImplementor.class);

            EntityPersister persister = session.getEntityPersister(null, entity);
            Object[] currentState = persister.getPropertyValues(entity);
            Object[] loadedState = session.getPersistenceContext().getEntry(entity).getLoadedState();

			String[] propertyNames = persister.getPropertyNames();
            Set<String> dirtyFields = new HashSet<>();

            for (int i = 0; i < propertyNames.length; i++) {
                if (!Objects.deepEquals(currentState[i], loadedState[i])) {
                    dirtyFields.add(propertyNames[i]);
                }
            }

            return dirtyFields.toArray(new String[0]);
        } catch (Exception e) {
            log.debug("Could not determine dirty fields: {}", e.getMessage());
            return new String[0];
        }
    }

    private boolean shouldEvictCache(CacheEvictionPolicy policy, Object entity, String[] dirtyFields) {
        if (dirtyFields == null || dirtyFields.length == 0) {
            return false;
        }

        // Check if only ignored fields changed
        Set<String> ignored = new HashSet<>(Arrays.asList(policy.ignoreFields()));
        Set<String> dirty = new HashSet<>(Arrays.asList(dirtyFields));

        dirty.removeAll(ignored);

        if (dirty.isEmpty()) {
            return false; // Only ignored fields changed
        }

        // Check if specific fields require eviction
        if (policy.evictOnChange().length > 0) {
            Set<String> evictTriggers = new HashSet<>(Arrays.asList(policy.evictOnChange()));
            return dirty.stream().anyMatch(evictTriggers::contains);
        }

        // Use custom strategy if provided
        try {
            CacheEvictionPolicy.EvictionStrategy strategy = policy.strategy().getDeclaredConstructor().newInstance();
            return strategy.shouldEvict(entity, dirtyFields);
        } catch (Exception e) {
            log.error("Failed to instantiate eviction strategy", e);
            return true; // Fail safe - evict on error
        }
    }

    private static class Objects {
        static boolean deepEquals(Object a, Object b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a instanceof byte[] && b instanceof byte[]) {
                return Arrays.equals((byte[]) a, (byte[]) b);
            }
            return a.equals(b);
        }
    }
}