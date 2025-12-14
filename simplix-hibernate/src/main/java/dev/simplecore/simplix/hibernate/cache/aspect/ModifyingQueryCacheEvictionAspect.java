package dev.simplecore.simplix.hibernate.cache.aspect;

import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;
import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

/**
 * AOP Aspect for handling cache eviction via @EvictCache annotation.
 *
 * <p>This aspect intercepts methods annotated with {@link EvictCache} and
 * schedules cache eviction after successful transaction commit.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // For @Modifying queries, explicitly declare which entities to evict
 * @Modifying
 * @Query("UPDATE User u SET u.active = false WHERE u.lastLogin < :date")
 * @EvictCache(User.class)
 * int deactivateOldUsers(@Param("date") LocalDate date);
 *
 * // Multiple entities can be specified
 * @Modifying
 * @Query("DELETE FROM OrderItem oi WHERE oi.order.id = :orderId")
 * @EvictCache({OrderItem.class, Order.class})
 * void deleteOrderItems(@Param("orderId") Long orderId);
 *
 * // Native queries are also supported
 * @Modifying
 * @Query(value = "UPDATE users SET status = 'INACTIVE' WHERE ...", nativeQuery = true)
 * @EvictCache(User.class)
 * int bulkUpdateUsers();
 * }</pre>
 *
 * <h3>Processing Flow</h3>
 * <ol>
 *   <li>Method with @EvictCache is invoked</li>
 *   <li>Original method executes (query runs)</li>
 *   <li>On success: pending evictions are collected via TransactionAwareCacheEvictionCollector</li>
 *   <li>On transaction commit: evictions are executed</li>
 *   <li>On exception/rollback: no eviction occurs</li>
 * </ol>
 *
 * @see EvictCache
 * @see TransactionAwareCacheEvictionCollector
 */
@Slf4j
@Aspect
@Order(100) // Run after transaction advice
@RequiredArgsConstructor
public class ModifyingQueryCacheEvictionAspect {

    private final TransactionAwareCacheEvictionCollector evictionCollector;

    /**
     * Intercepts methods annotated with @EvictCache.
     *
     * <p>The method is executed first, then cache eviction is scheduled
     * for after transaction commit. If the method throws an exception,
     * no eviction is scheduled.</p>
     *
     * @param joinPoint the method join point
     * @return the method's return value
     * @throws Throwable if the method throws an exception
     */
    @Around("@annotation(dev.simplecore.simplix.hibernate.cache.annotation.EvictCache)")
    public Object handleEvictCache(ProceedingJoinPoint joinPoint) throws Throwable {
        // Execute the original method first
        Object result = joinPoint.proceed();

        // Get @EvictCache annotation
        EvictCache evictCache = getEvictCacheAnnotation(joinPoint);
        if (evictCache == null) {
            return result;
        }

        // Collect evictions for each specified entity class
        collectEvictions(evictCache, joinPoint);

        return result;
    }

    /**
     * Extracts the @EvictCache annotation from the method.
     */
    private EvictCache getEvictCacheAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            return method.getAnnotation(EvictCache.class);
        } catch (Exception e) {
            log.warn("⚠ Failed to get @EvictCache annotation", e);
            return null;
        }
    }

    /**
     * Collects pending evictions for all entity classes specified in @EvictCache.
     * Respects the evictQueryCache attribute to control query cache eviction.
     */
    private void collectEvictions(EvictCache evictCache, ProceedingJoinPoint joinPoint) {
        Class<?>[] entityClasses = evictCache.value();
        String[] regions = evictCache.regions();
        boolean evictQueryCache = evictCache.evictQueryCache();

        String methodName = joinPoint.getSignature().toShortString();
        PendingEviction.EvictionOperation operation = determineBulkOperation(methodName);

        if (!evictQueryCache) {
            log.debug("Query cache eviction disabled via @EvictCache(evictQueryCache=false) on {}",
                    methodName);
        }

        for (int i = 0; i < entityClasses.length; i++) {
            Class<?> entityClass = entityClasses[i];
            // Safe region access with bounds checking
            String region = (regions != null && i < regions.length && !regions[i].isEmpty())
                    ? regions[i] : null;

            // Create pending eviction using factory method for serialization safety
            // Bulk operation uses null entityId to evict entire cache
            PendingEviction pendingEviction = PendingEviction.of(
                    entityClass, null, region, operation, evictQueryCache);

            evictionCollector.collect(pendingEviction);

            log.debug("✔ Collected bulk eviction for {} via @EvictCache on {} (evictQueryCache={})",
                    entityClass.getSimpleName(), methodName, evictQueryCache);
        }
    }

    /**
     * Determines the bulk operation type based on method name heuristics.
     */
    private PendingEviction.EvictionOperation determineBulkOperation(String methodName) {
        String lowerName = methodName.toLowerCase();
        if (lowerName.contains("delete") || lowerName.contains("remove")) {
            return PendingEviction.EvictionOperation.BULK_DELETE;
        }
        return PendingEviction.EvictionOperation.BULK_UPDATE;
    }
}