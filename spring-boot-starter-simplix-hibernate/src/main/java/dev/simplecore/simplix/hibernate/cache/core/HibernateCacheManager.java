package dev.simplecore.simplix.hibernate.cache.core;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for Hibernate second-level cache operations
 */
@Slf4j
@RequiredArgsConstructor
public class HibernateCacheManager {

    private final EntityManagerFactory entityManagerFactory;
    private final Set<String> activeRegions = ConcurrentHashMap.newKeySet();

    /**
     * Evict all entries from entity cache
     */
    public void evictEntityCache(Class<?> entityClass) {
        try {
            jakarta.persistence.Cache cache = entityManagerFactory.getCache();
            cache.evict(entityClass);
            log.debug("✔ Evicted entity cache for: {}", entityClass.getSimpleName());
        } catch (Exception e) {
            log.error("✖ Failed to evict entity cache for: {}", entityClass.getSimpleName(), e);
        }
    }

    /**
     * Evict specific entity from cache
     */
    public void evictEntity(Class<?> entityClass, Object id) {
        try {
            jakarta.persistence.Cache cache = entityManagerFactory.getCache();
            cache.evict(entityClass, id);
            log.debug("✔ Evicted entity from cache: {} [{}]", entityClass.getSimpleName(), id);
        } catch (Exception e) {
            log.error("✖ Failed to evict entity from cache: {} [{}]", entityClass.getSimpleName(), id, e);
        }
    }

    /**
     * Evict all entries from all caches
     */
    public void evictAll() {
        try {
            jakarta.persistence.Cache cache = entityManagerFactory.getCache();
            cache.evictAll();

            // Also evict query cache
            getHibernateCache().evictAllRegions();

            log.info("✔ Evicted all cache regions");
        } catch (Exception e) {
            log.error("✖ Failed to evict all caches", e);
        }
    }

    /**
     * Evict specific cache region
     */
    public void evictRegion(String regionName) {
        try {
            getHibernateCache().evictRegion(regionName);
            log.debug("✔ Evicted cache region: {}", regionName);
        } catch (Exception e) {
            log.error("✖ Failed to evict cache region: {}", regionName, e);
        }
    }

    /**
     * Evict query cache region
     */
    public void evictQueryRegion(String queryRegion) {
        try {
            getHibernateCache().evictQueryRegion(queryRegion);
            log.debug("✔ Evicted query cache region: {}", queryRegion);
        } catch (Exception e) {
            log.error("✖ Failed to evict query cache region: {}", queryRegion, e);
        }
    }

    /**
     * Check if entity is in cache
     */
    public boolean contains(Class<?> entityClass, Object id) {
        try {
            jakarta.persistence.Cache cache = entityManagerFactory.getCache();
            return cache.contains(entityClass, id);
        } catch (Exception e) {
            log.error("✖ Failed to check cache for: {} [{}]", entityClass.getSimpleName(), id, e);
            return false;
        }
    }

    /**
     * Register active cache region
     */
    public void registerRegion(String regionName) {
        activeRegions.add(regionName);
        log.debug("ℹ Registered cache region: {}", regionName);
    }

    /**
     * Get all active cache regions
     */
    public Set<String> getActiveRegions() {
        return Set.copyOf(activeRegions);
    }

    private org.hibernate.Cache getHibernateCache() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        return sessionFactory.getCache();
    }
}