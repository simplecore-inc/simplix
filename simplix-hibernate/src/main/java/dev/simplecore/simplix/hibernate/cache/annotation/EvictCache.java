package dev.simplecore.simplix.hibernate.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method for explicit cache eviction after execution.
 *
 * <p>This annotation is primarily used with {@code @Modifying} queries that bypass
 * Hibernate's entity lifecycle events. Since bulk updates and deletes don't trigger
 * {@code @PostUpdate} or {@code @PostRemove} callbacks, the cache would otherwise
 * become stale.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * public interface UserRepository extends JpaRepository<User, Long> {
 *
 *     @Modifying
 *     @Query("UPDATE User u SET u.status = :status WHERE u.role = :role")
 *     @EvictCache(User.class)
 *     int updateStatusByRole(@Param("status") Status status, @Param("role") Role role);
 *
 *     @Modifying
 *     @Query("DELETE FROM User u WHERE u.deletedAt < :date")
 *     @EvictCache(User.class)
 *     int deleteOldUsers(@Param("date") LocalDateTime date);
 * }
 * }</pre>
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>Eviction is performed <strong>after</strong> the method returns successfully</li>
 *   <li>If transaction is active, eviction happens after transaction commit</li>
 *   <li>Entire entity cache region is evicted (not individual entries)</li>
 *   <li>Works with distributed cache - eviction is broadcast to other nodes</li>
 * </ul>
 *
 * <h3>When to Use</h3>
 * <ul>
 *   <li>{@code @Modifying} bulk update queries</li>
 *   <li>{@code @Modifying} bulk delete queries</li>
 *   <li>Native queries that modify cached entities</li>
 *   <li>Any operation that bypasses Hibernate entity lifecycle</li>
 * </ul>
 *
 * <h3>When NOT Needed</h3>
 * <ul>
 *   <li>Standard {@code save()}, {@code delete()} operations - handled automatically</li>
 *   <li>Queries that only read data</li>
 *   <li>Entities without {@code @Cache} annotation</li>
 * </ul>
 *
 * @see dev.simplecore.simplix.hibernate.cache.aspect.ModifyingQueryCacheEvictionAspect
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EvictCache {

    /**
     * The entity classes whose caches should be evicted.
     *
     * <p>Multiple classes can be specified if the query affects multiple entity types.</p>
     *
     * @return array of entity classes to evict from cache
     */
    Class<?>[] value();

    /**
     * Optional cache region names to evict.
     *
     * <p>If not specified, the default region for each entity class is used.
     * This is useful when entities use custom cache region names.</p>
     *
     * @return array of cache region names, or empty to use entity class defaults
     */
    String[] regions() default {};

    /**
     * Whether to also evict query cache regions associated with the entities.
     *
     * <p>Defaults to {@code true} since bulk operations typically invalidate
     * query results that reference the modified entities.</p>
     *
     * @return true to evict associated query caches, false to only evict entity cache
     */
    boolean evictQueryCache() default true;
}
