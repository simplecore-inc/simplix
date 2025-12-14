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

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP aspect for automatic cache eviction on repository operations.
 *
 * <p>This aspect runs after transaction commit logic by using a high order value,
 * ensuring cache eviction only happens after successful transaction completion.</p>
 */
@Slf4j
@Aspect
// Use explicit order value consistent with ModifyingQueryCacheEvictionAspect (100)
// AutoCacheEvictionAspect should run after ModifyingQueryCacheEvictionAspect for @Modifying methods
@Order(200)
@RequiredArgsConstructor
public class AutoCacheEvictionAspect {

    /**
     * Cache for entity class extraction to avoid repeated reflection.
     * Limited size to prevent unbounded memory growth.
     * Lock object for atomic size check and cache update.
     */
    private static final int MAX_ENTITY_CLASS_CACHE_SIZE = 1000;
    private static final Map<Class<?>, Class<?>> ENTITY_CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Object CACHE_LOCK = new Object();

    private final HibernateCacheManager cacheManager;
    private final QueryCacheManager queryCacheManager;

    /**
     * Clears the static entity class cache to prevent stale entries
     * when application context is refreshed (8th review fix).
     */
    @PreDestroy
    public void cleanup() {
        synchronized (CACHE_LOCK) {
            ENTITY_CLASS_CACHE.clear();
            log.debug("✔ AutoCacheEvictionAspect entity class cache cleared");
        }
    }

    // Specific method names instead of wildcards to avoid matching internal methods
    @AfterReturning(
            value = "execution(* org.springframework.data.jpa.repository.JpaRepository+.save(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAll(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAndFlush(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAllAndFlush(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.delete(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteById(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteAll(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteAllById(..)) " +
                    "|| execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteAllInBatch(..))",
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
                // Batch save - evict entire entity cache
                cacheManager.evictEntityCache(entityClass);
                log.debug("✔ Evicted entire cache for batch save: {}", entityClass.getSimpleName());
            } else {
                // Single save - only evict specific entity by ID (more efficient)
                Object id = extractEntityId(result);
                if (id != null) {
                    cacheManager.evictEntity(entityClass, id);
                    log.debug("✔ Evicted entity by ID for single save: {}#{}", entityClass.getSimpleName(), id);
                } else {
                    // Fallback to entire cache eviction if ID cannot be extracted
                    cacheManager.evictEntityCache(entityClass);
                }
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
                    // Single delete - only evict by ID, not entire cache
                    Object id = isEntity(arg) ? extractEntityId(arg) : arg;
                    if (id != null) {
                        cacheManager.evictEntity(entityClass, id);
                        log.debug("✔ Evicted entity by ID for single delete: {}#{}", entityClass.getSimpleName(), id);
                    } else {
                        // Fallback to entire cache eviction only if ID cannot be extracted
                        cacheManager.evictEntityCache(entityClass);
                        log.debug("✔ Evicted entire cache for delete (ID not extractable): {}", entityClass.getSimpleName());
                    }
                }
            }
        }
    }

    private void evictQueryCaches(Class<?> entityClass) {
        // Null check for queryCacheManager
        if (queryCacheManager == null) {
            log.debug("QueryCacheManager not available, skipping query cache eviction");
            return;
        }

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
        if (repositoryProxy == null) {
            return null;
        }

        Class<?> proxyClass = repositoryProxy.getClass();

        // Check cache first to avoid repeated reflection
        Class<?> cached = ENTITY_CLASS_CACHE.get(proxyClass);
        if (cached != null) {
            return cached;
        }

        Class<?> entityClass = doExtractEntityClass(proxyClass);
        if (entityClass != null) {
            // Synchronized block for atomic size check and cache update
            synchronized (CACHE_LOCK) {
                if (ENTITY_CLASS_CACHE.size() < MAX_ENTITY_CLASS_CACHE_SIZE) {
                    ENTITY_CLASS_CACHE.putIfAbsent(proxyClass, entityClass);
                } else {
                    log.debug("Entity class cache size limit reached, not caching: {}", proxyClass.getName());
                }
            }
        }
        return entityClass;
    }

    private Class<?> doExtractEntityClass(Class<?> proxyClass) {
        try {
            // Strategy 1: Check interfaces for JpaRepository type parameter
            Class<?>[] interfaces = proxyClass.getInterfaces();
            for (Class<?> iface : interfaces) {
                if (JpaRepository.class.isAssignableFrom(iface)) {
                    Type[] genericInterfaces = iface.getGenericInterfaces();
                    for (Type type : genericInterfaces) {
                        if (type instanceof ParameterizedType paramType) {
                            Type[] typeArgs = paramType.getActualTypeArguments();
                            if (typeArgs.length > 0) {
                                // Use safe type resolution to handle TypeVariable (8th review fix)
                                Class<?> resolved = resolveTypeArgument(typeArgs[0]);
                                if (resolved != null) {
                                    return resolved;
                                }
                            }
                        }
                    }
                }
            }

            // Strategy 2: Try to extract from proxy target via findById method
            if (proxyClass.getName().contains("$Proxy")) {
                for (Method method : proxyClass.getMethods()) {
                    if (method.getName().equals("findById")) {
                        Type returnType = method.getGenericReturnType();
                        if (returnType instanceof ParameterizedType paramType) {
                            Type[] typeArgs = paramType.getActualTypeArguments();
                            if (typeArgs.length > 0) {
                                // Use safe type resolution (8th review fix)
                                Class<?> resolved = resolveTypeArgument(typeArgs[0]);
                                if (resolved != null) {
                                    return resolved;
                                }
                            }
                        }
                    }
                }
            }

            // Strategy 3: Check superclass for type parameters (CGLIB proxies)
            Type superclass = proxyClass.getGenericSuperclass();
            if (superclass instanceof ParameterizedType paramType) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    // Use safe type resolution (8th review fix)
                    Class<?> resolved = resolveTypeArgument(typeArgs[0]);
                    if (resolved != null && resolved.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                        return resolved;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Could not extract entity class from repository: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Safely resolves a Type to a Class, handling TypeVariable and ParameterizedType.
     * Returns null for unresolvable types like TypeVariable or WildcardType (8th review fix).
     *
     * @param type the Type to resolve
     * @return the resolved Class, or null if not resolvable
     */
    private Class<?> resolveTypeArgument(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }

        if (type instanceof ParameterizedType paramType) {
            Type rawType = paramType.getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }

        // TypeVariable, WildcardType, GenericArrayType cannot be resolved to concrete class
        if (type instanceof TypeVariable) {
            log.debug("Cannot resolve TypeVariable to concrete class: {}", type);
        }
        return null;
    }

    /**
     * Extracts entity ID using JPA @Id, @EmbeddedId annotations or standard getId() method.
     * Handles Hibernate proxies by unwrapping them first (8th review fix).
     * Supports composite keys via @EmbeddedId (9th review fix).
     *
     * @param entity the entity to extract ID from
     * @return the entity ID, or null if extraction fails
     */
    private Object extractEntityId(Object entity) {
        try {
            // Unwrap Hibernate proxy first (8th review fix)
            Object unwrapped = unwrapHibernateProxy(entity);
            Class<?> entityClass = unwrapped.getClass();

            // Strategy 1: Check for @EmbeddedId (composite key support - 9th review fix)
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(EmbeddedId.class)) {
                    field.setAccessible(true);
                    return field.get(unwrapped);
                }
            }

            // Strategy 2: Use JPA @Id annotation to find ID field (handles custom ID names)
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    return field.get(unwrapped);
                }
            }

            // Strategy 3: Check superclass for @EmbeddedId or @Id annotation
            Class<?> superClass = entityClass.getSuperclass();
            while (superClass != null && superClass != Object.class) {
                for (Field field : superClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(EmbeddedId.class)) {
                        field.setAccessible(true);
                        return field.get(unwrapped);
                    }
                    if (field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        return field.get(unwrapped);
                    }
                }
                superClass = superClass.getSuperclass();
            }

            // Strategy 4: Fallback to standard getId() method
            Method getIdMethod = entityClass.getMethod("getId");
            return getIdMethod.invoke(unwrapped);

        } catch (Exception e) {
            // Upgraded to warn - ID extraction failure leads to full cache eviction (less efficient)
            log.warn("⚠ Could not extract entity ID from {}: {} - falling back to full cache eviction",
                    entity.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Checks if an object is a JPA entity.
     * Handles Hibernate proxies by checking the underlying persistent class (8th review fix).
     *
     * @param obj the object to check
     * @return true if the object is a JPA entity
     */
    private boolean isEntity(Object obj) {
        if (obj == null) {
            return false;
        }

        Class<?> entityClass = obj.getClass();

        // Unwrap Hibernate proxy to get the actual entity class (8th review fix)
        if (obj instanceof org.hibernate.proxy.HibernateProxy) {
            entityClass = ((org.hibernate.proxy.HibernateProxy) obj)
                    .getHibernateLazyInitializer()
                    .getPersistentClass();
        }

        return entityClass.isAnnotationPresent(jakarta.persistence.Entity.class);
    }

    /**
     * Unwraps Hibernate proxy to get the actual entity implementation (8th review fix).
     *
     * @param entity the potentially proxied entity
     * @return the unwrapped entity, or original if not a proxy
     */
    private Object unwrapHibernateProxy(Object entity) {
        if (entity instanceof org.hibernate.proxy.HibernateProxy) {
            return ((org.hibernate.proxy.HibernateProxy) entity)
                    .getHibernateLazyInitializer()
                    .getImplementation();
        }
        return entity;
    }
}