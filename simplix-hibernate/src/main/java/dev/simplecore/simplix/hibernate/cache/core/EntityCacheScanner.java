package dev.simplecore.simplix.hibernate.cache.core;

import jakarta.persistence.Entity;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Cache;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scans classpath for entities with @Cache annotation
 */
@Slf4j
public class EntityCacheScanner {

    // Use thread-safe sets for concurrent access during scanning
    private final Set<Class<?>> cachedEntities = ConcurrentHashMap.newKeySet();
    private final Set<String> cacheRegions = ConcurrentHashMap.newKeySet();

    /**
     * Scan for all cached entities in given packages
     */
    public void scanForCachedEntities(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            basePackages = new String[]{""}; // Scan all
        }

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        // Track missing entities for better observability
        AtomicInteger missedCount = new AtomicInteger(0);

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition candidate : candidates) {
                try {
                    Class<?> clazz = Class.forName(candidate.getBeanClassName());
                    if (clazz.isAnnotationPresent(Cache.class)) {
                        registerCachedEntity(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    missedCount.incrementAndGet();
                    log.error("✖ Could not load class: {}", candidate.getBeanClassName(), e);
                }
            }
        }

        if (missedCount.get() > 0) {
            log.warn("⚠ {} entities could not be loaded during cache scan", missedCount.get());
        }
        log.info("✔ Found {} cached entities across {} regions",
                cachedEntities.size(), cacheRegions.size());
    }

    private void registerCachedEntity(Class<?> entityClass) {
        cachedEntities.add(entityClass);

        Cache cacheAnnotation = entityClass.getAnnotation(Cache.class);
        String region = cacheAnnotation.region();

        if (region != null && !region.isEmpty()) {
            cacheRegions.add(region);
            log.trace("Registered cached entity: {} in region: {}",
                    entityClass.getSimpleName(), region);
        } else {
            log.trace("Registered cached entity: {} (default region)",
                    entityClass.getSimpleName());
        }
    }

    public Set<Class<?>> getCachedEntities() {
        return Set.copyOf(cachedEntities);
    }

    public Set<String> getCacheRegions() {
        return Set.copyOf(cacheRegions);
    }

    public boolean isCached(Class<?> entityClass) {
        return cachedEntities.contains(entityClass);
    }

    /**
     * Find a cached entity class by its simple name.
     * Uses case-insensitive comparison since JPQL entity names are case-insensitive.
     *
     * @param simpleName the simple class name (e.g., "User", "user", "ORDER")
     * @return the entity class if found and cached, null otherwise
     */
    public Class<?> findBySimpleName(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return null;
        }

        // Case-insensitive comparison - JPQL entity names are case-insensitive
        return cachedEntities.stream()
                .filter(clazz -> clazz.getSimpleName().equalsIgnoreCase(simpleName))
                .findFirst()
                .orElse(null);
    }
}