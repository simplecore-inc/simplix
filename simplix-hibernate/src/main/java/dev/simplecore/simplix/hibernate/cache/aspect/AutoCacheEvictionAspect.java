package dev.simplecore.simplix.hibernate.cache.aspect;

import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.core.QueryCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.annotations.Cache;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * AOP aspect for automatic cache eviction on repository operations
 */
@Slf4j
@Aspect
@Order(1)
@RequiredArgsConstructor
public class AutoCacheEvictionAspect {

    private final HibernateCacheManager cacheManager;
    private final QueryCacheManager queryCacheManager;

    @AfterReturning(
            value = "execution(* org.springframework.data.jpa.repository.JpaRepository+.save*(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.delete*(..))",
            returning = "result"
    )
    public void handleRepositoryOperation(JoinPoint joinPoint, Object result) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Class<?> entityClass = extractEntityClass(joinPoint.getTarget());

            if (entityClass == null || !entityClass.isAnnotationPresent(Cache.class)) {
                return;
            }

            // Handle different operation types
            if (methodName.startsWith("save")) {
                handleSaveOperation(entityClass, result);
            } else if (methodName.startsWith("delete")) {
                handleDeleteOperation(entityClass, joinPoint.getArgs());
            }

            // Evict related query caches
            evictQueryCaches(entityClass);

            log.debug("✔ Cache auto-evicted for {} operation on {}", methodName, entityClass.getSimpleName());

        } catch (Exception e) {
            log.error("✖ Failed to handle cache eviction", e);
        }
    }

    private void handleSaveOperation(Class<?> entityClass, Object result) {
        if (result != null) {
            if (result instanceof Collection<?> collection) {
                // Batch save
                cacheManager.evictEntityCache(entityClass);
                log.debug("✔ Evicted entire cache for batch save: {}", entityClass.getSimpleName());
            } else {
                // Single save
                Object id = extractEntityId(result);
                if (id != null) {
                    cacheManager.evictEntity(entityClass, id);
                }
                // Also evict entire cache to handle query results
                cacheManager.evictEntityCache(entityClass);
            }
        }
    }

    private void handleDeleteOperation(Class<?> entityClass, Object[] args) {
        if (args != null && args.length > 0) {
            Object arg = args[0];
            if (arg != null) {
                if (arg instanceof Collection) {
                    // Batch delete
                    cacheManager.evictEntityCache(entityClass);
                    log.debug("✔ Evicted entire cache for batch delete: {}", entityClass.getSimpleName());
                } else {
                    // Try to extract ID from entity or use as ID directly
                    Object id = isEntity(arg) ? extractEntityId(arg) : arg;
                    if (id != null) {
                        cacheManager.evictEntity(entityClass, id);
                    }
                    cacheManager.evictEntityCache(entityClass);
                }
            }
        }
    }

    private void evictQueryCaches(Class<?> entityClass) {
        // Get all query cache regions associated with this entity
        var queryRegions = queryCacheManager.getQueryRegionsForEntity(entityClass);

        for (String region : queryRegions) {
            cacheManager.evictQueryRegion(region);
            log.debug("✔ Evicted query cache region: {}", region);
        }

        // Also evict default query cache
        cacheManager.evictQueryRegion("default");
    }

    private Class<?> extractEntityClass(Object repositoryProxy) {
        try {
            Class<?>[] interfaces = repositoryProxy.getClass().getInterfaces();
            for (Class<?> iface : interfaces) {
                if (JpaRepository.class.isAssignableFrom(iface)) {
                    Type[] genericInterfaces = iface.getGenericInterfaces();
                    for (Type type : genericInterfaces) {
                        if (type instanceof ParameterizedType paramType) {
                            Type[] typeArgs = paramType.getActualTypeArguments();
                            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                                return (Class<?>) typeArgs[0];
                            }
                        }
                    }
                }
            }

            // Alternative: try to extract from proxy target
            var targetClass = repositoryProxy.getClass();
            if (targetClass.getName().contains("$Proxy")) {
                // Try to find the entity class from repository method signature
                var methods = targetClass.getMethods();
                for (var method : methods) {
                    if (method.getName().equals("findById")) {
                        var returnType = method.getGenericReturnType();
                        if (returnType instanceof ParameterizedType paramType) {
                            var typeArgs = paramType.getActualTypeArguments();
                            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                                return (Class<?>) typeArgs[0];
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Could not extract entity class from repository: {}", e.getMessage());
        }
        return null;
    }

    private Object extractEntityId(Object entity) {
        try {
            var method = entity.getClass().getMethod("getId");
            return method.invoke(entity);
        } catch (Exception e) {
            log.debug("Could not extract entity ID: {}", e.getMessage());
            return null;
        }
    }

    private boolean isEntity(Object obj) {
        return obj != null && obj.getClass().isAnnotationPresent(jakarta.persistence.Entity.class);
    }
}