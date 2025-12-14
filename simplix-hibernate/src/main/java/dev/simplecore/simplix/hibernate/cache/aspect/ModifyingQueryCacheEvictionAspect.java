package dev.simplecore.simplix.hibernate.cache.aspect;

import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;
import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AOP Aspect for handling cache eviction on @Modifying queries.
 *
 * <p>This aspect supports two modes of operation:</p>
 * <ol>
 *   <li><b>Automatic mode</b>: Intercepts {@code @Modifying} + {@code @Query} and
 *       automatically extracts entity class from JPQL (UPDATE/DELETE statements)</li>
 *   <li><b>Explicit mode</b>: Uses {@link EvictCache} annotation when automatic
 *       detection is not possible or when custom behavior is needed</li>
 * </ol>
 *
 * <h3>Automatic Entity Detection</h3>
 * <p>For @Modifying queries, the aspect parses the JPQL to extract the target entity:</p>
 * <ul>
 *   <li>{@code UPDATE User u SET ...} extracts "User"</li>
 *   <li>{@code DELETE FROM Order o WHERE ...} extracts "Order"</li>
 * </ul>
 *
 * <h3>Processing Flow</h3>
 * <ol>
 *   <li>Method with @Modifying or @EvictCache is invoked</li>
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

    /**
     * Pattern to extract entity name from JPQL UPDATE statement.
     * Case-insensitive matching since JPQL entity names are case-insensitive.
     * Matches: UPDATE EntityName alias SET ... or UPDATE entityname alias SET ...
     */
    private static final Pattern UPDATE_PATTERN =
            Pattern.compile("(?i)^\\s*UPDATE\\s+([a-zA-Z]\\w*)\\s+");

    /**
     * Pattern to extract entity name from JPQL DELETE statement.
     * Case-insensitive matching since JPQL entity names are case-insensitive.
     * Matches: DELETE FROM EntityName alias WHERE ... or DELETE entityname alias WHERE ...
     */
    private static final Pattern DELETE_PATTERN =
            Pattern.compile("(?i)^\\s*DELETE\\s+(?:FROM\\s+)?([a-zA-Z]\\w*)\\s+");

    private final TransactionAwareCacheEvictionCollector evictionCollector;
    private final EntityCacheScanner entityCacheScanner;

    /**
     * Intercepts methods annotated with @Modifying (without @EvictCache).
     *
     * <p>Automatically extracts the target entity from the @Query annotation's JPQL
     * and schedules cache eviction. This eliminates the need for explicit @EvictCache
     * in most cases.</p>
     *
     * @param joinPoint the method join point
     * @return the method's return value
     * @throws Throwable if the method throws an exception
     */
    @Around("@annotation(org.springframework.data.jpa.repository.Modifying) && " +
            "!@annotation(dev.simplecore.simplix.hibernate.cache.annotation.EvictCache)")
    public Object handleModifyingQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        // Execute the original method first
        Object result = joinPoint.proceed();

        // Try to auto-detect entity from @Query
        Class<?> entityClass = extractEntityFromQuery(joinPoint);
        if (entityClass != null) {
            String methodName = joinPoint.getSignature().toShortString();
            PendingEviction.EvictionOperation operation = determineBulkOperation(methodName);

            PendingEviction pendingEviction = PendingEviction.of(
                    entityClass, null, null, operation);

            evictionCollector.collect(pendingEviction);

            log.debug("✔ Auto-collected eviction for {} via @Modifying on {}",
                    entityClass.getSimpleName(), methodName);
        }

        return result;
    }

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

        // Determine if query cache should be evicted based on annotation setting
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
     * Extracts the target entity class from @Query annotation's JPQL.
     *
     * <p>Parses UPDATE and DELETE statements to extract the entity name,
     * then looks up the corresponding class from EntityCacheScanner.</p>
     *
     * @param joinPoint the method join point
     * @return the entity class if found, null otherwise
     */
    private Class<?> extractEntityFromQuery(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            Query queryAnnotation = method.getAnnotation(Query.class);
            if (queryAnnotation == null) {
                log.trace("No @Query annotation found on {}", method.getName());
                return null;
            }

            String jpql = queryAnnotation.value();
            if (jpql == null || jpql.isEmpty()) {
                return null;
            }

            // Try to extract entity name from UPDATE statement
            Matcher updateMatcher = UPDATE_PATTERN.matcher(jpql);
            if (updateMatcher.find()) {
                String entityName = updateMatcher.group(1);
                return resolveEntityClass(entityName, method);
            }

            // Try to extract entity name from DELETE statement
            Matcher deleteMatcher = DELETE_PATTERN.matcher(jpql);
            if (deleteMatcher.find()) {
                String entityName = deleteMatcher.group(1);
                return resolveEntityClass(entityName, method);
            }

            log.debug("Could not extract entity from JPQL: {}", jpql);
            return null;

        } catch (Exception e) {
            log.warn("⚠ Failed to extract entity from @Query", e);
            return null;
        }
    }

    /**
     * Resolves entity name to Class using EntityCacheScanner.
     * Only returns entity if it has @Cache annotation (validated by isCached check).
     */
    private Class<?> resolveEntityClass(String entityName, Method method) {
        Class<?> entityClass = entityCacheScanner.findBySimpleName(entityName);

        if (entityClass == null) {
            log.debug("Entity '{}' not found in cached entities (may not be cached)", entityName);
            return null;
        }

        // Explicit @Cache validation - ensure entity is actually cached
        if (!entityCacheScanner.isCached(entityClass)) {
            log.debug("Entity '{}' is not cached, skipping eviction", entityName);
            return null;
        }

        return entityClass;
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
