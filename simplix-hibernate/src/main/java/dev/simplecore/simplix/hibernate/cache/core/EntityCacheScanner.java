package dev.simplecore.simplix.hibernate.cache.core;

import jakarta.persistence.Entity;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Cache;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.HashSet;
import java.util.Set;

/**
 * Scans classpath for entities with @Cache annotation
 */
@Slf4j
public class EntityCacheScanner {

    private final Set<Class<?>> cachedEntities = new HashSet<>();
    private final Set<String> cacheRegions = new HashSet<>();

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

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition candidate : candidates) {
                try {
                    Class<?> clazz = Class.forName(candidate.getBeanClassName());
                    if (clazz.isAnnotationPresent(Cache.class)) {
                        registerCachedEntity(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    log.error("Could not load class: {}", candidate.getBeanClassName(), e);
                }
            }
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
            log.debug("Registered cached entity: {} in region: {}",
                    entityClass.getSimpleName(), region);
        } else {
            log.debug("Registered cached entity: {} (default region)",
                    entityClass.getSimpleName());
        }
    }

    public Set<Class<?>> getCachedEntities() {
        return new HashSet<>(cachedEntities);
    }

    public Set<String> getCacheRegions() {
        return new HashSet<>(cacheRegions);
    }

    public boolean isCached(Class<?> entityClass) {
        return cachedEntities.contains(entityClass);
    }
}