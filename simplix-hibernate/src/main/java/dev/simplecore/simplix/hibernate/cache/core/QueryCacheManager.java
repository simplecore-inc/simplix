package dev.simplecore.simplix.hibernate.cache.core;

import jakarta.persistence.QueryHint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages query cache regions and their relationship to entities
 */
@Slf4j
public class QueryCacheManager {

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Class<?>, Set<String>> entityQueryCacheMap = new ConcurrentHashMap<>();
    private final Map<String, Set<Class<?>>> regionEntityMap = new ConcurrentHashMap<>();

    @EventListener(ContextRefreshedEvent.class)
    public void scanRepositoryMethods() {
        log.info("ℹ Scanning repositories for query cache configuration...");

        // Get all JPA repositories
        Map<String, JpaRepository> repositories = applicationContext.getBeansOfType(JpaRepository.class);

        for (Map.Entry<String, JpaRepository> entry : repositories.entrySet()) {
            String beanName = entry.getKey();
            Object repository = entry.getValue();

            try {
                Class<?> repositoryInterface = getRepositoryInterface(repository);
                if (repositoryInterface != null) {
                    scanRepositoryInterface(repositoryInterface);
                }
            } catch (Exception e) {
                log.debug("Could not scan repository {}: {}", beanName, e.getMessage());
            }
        }

        log.info("✔ Query cache scan complete. Found {} entity mappings", entityQueryCacheMap.size());
    }

    private void scanRepositoryInterface(Class<?> repositoryInterface) {
        // Extract entity class from repository
        Class<?> entityClass = extractEntityClassFromRepository(repositoryInterface);
        if (entityClass == null) {
            return;
        }

        // Scan all methods for @QueryHints
        for (Method method : repositoryInterface.getDeclaredMethods()) {
            QueryHints queryHints = method.getAnnotation(QueryHints.class);
            if (queryHints != null) {
                processQueryHints(entityClass, queryHints);
            }
        }
    }

    private void processQueryHints(Class<?> entityClass, QueryHints queryHints) {
        for (QueryHint hint : queryHints.value()) {
            if ("org.hibernate.cacheRegion".equals(hint.name())) {
                String region = hint.value();
                registerQueryCache(entityClass, region);
                log.debug("✔ Registered query cache: {} -> {}", entityClass.getSimpleName(), region);
            }
        }
    }

    private Class<?> extractEntityClassFromRepository(Class<?> repositoryInterface) {
        // Check generic interfaces
        Type[] genericInterfaces = repositoryInterface.getGenericInterfaces();
        for (Type type : genericInterfaces) {
            if (type instanceof ParameterizedType paramType) {
                Type rawType = paramType.getRawType();
                if (rawType instanceof Class && JpaRepository.class.isAssignableFrom((Class<?>) rawType)) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        return (Class<?>) typeArgs[0];
                    }
                }
            }
        }
        return null;
    }

    private Class<?> getRepositoryInterface(Object repository) {
        Class<?> targetClass = repository.getClass();

        // Handle Spring proxies
        if (targetClass.getName().contains("Proxy")) {
            Class<?>[] interfaces = targetClass.getInterfaces();
            for (Class<?> iface : interfaces) {
                if (JpaRepository.class.isAssignableFrom(iface) && !JpaRepository.class.equals(iface)) {
                    return iface;
                }
            }
        }

        return null;
    }

    /**
     * Register a query cache region for an entity
     */
    public void registerQueryCache(Class<?> entityClass, String region) {
        entityQueryCacheMap.computeIfAbsent(entityClass, k -> ConcurrentHashMap.newKeySet())
                .add(region);

        regionEntityMap.computeIfAbsent(region, k -> ConcurrentHashMap.newKeySet())
                .add(entityClass);
    }

    /**
     * Get all query cache regions for an entity
     */
    public Set<String> getQueryRegionsForEntity(Class<?> entityClass) {
        Set<String> regions = new HashSet<>();

        // Direct regions for this entity
        Set<String> directRegions = entityQueryCacheMap.get(entityClass);
        if (directRegions != null) {
            regions.addAll(directRegions);
        }

        // Check for pattern-based regions
        String entityName = entityClass.getSimpleName().toLowerCase();
        for (String region : regionEntityMap.keySet()) {
            if (region.contains(entityName)) {
                regions.add(region);
            }
        }

        return regions;
    }

    /**
     * Get all entities affected by a query cache region
     */
    public Set<Class<?>> getEntitiesForRegion(String region) {
        return regionEntityMap.getOrDefault(region, Set.of());
    }

    /**
     * Clear all mappings
     */
    public void clear() {
        entityQueryCacheMap.clear();
        regionEntityMap.clear();
    }
}