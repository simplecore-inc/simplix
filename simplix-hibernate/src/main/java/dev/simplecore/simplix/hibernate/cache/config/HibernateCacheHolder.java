package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import org.hibernate.Cache;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static holder for Hibernate cache and eviction collector instances.
 *
 * <p>This class provides static references to the Hibernate Cache and
 * TransactionAwareCacheEvictionCollector that can be accessed from
 * {@link HibernateIntegrator}, which is instantiated by Hibernate's Service Provider
 * Interface (SPI) and cannot use Spring dependency injection.</p>
 *
 * <h3>Why Static Holder Pattern?</h3>
 * <ul>
 *   <li>Hibernate Integrator is loaded via Java SPI before Spring context initialization</li>
 *   <li>HibernateIntegrator cannot receive Spring-managed beans through constructor injection</li>
 *   <li>The cache instance must be shared between Spring-managed beans and SPI-loaded components</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses AtomicReference with compareAndSet() for lock-free, thread-safe initialization.
 * Each reference is set exactly once - the first successful call wins, subsequent calls are ignored.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Spring initializes {@link SimpliXHibernateCacheAutoConfiguration}</li>
 *   <li>Cache and collector references are stored here</li>
 *   <li>HibernateIntegrator retrieves them via static getters for event listeners</li>
 * </ol>
 *
 * <p><b>Note:</b> This pattern is generally not recommended but necessary due to Hibernate's
 * SPI initialization constraints. The instances are set once during application startup and
 * remain immutable throughout the application lifecycle.</p>
 *
 * @see HibernateIntegrator
 * @see SimpliXHibernateCacheAutoConfiguration
 */
public class HibernateCacheHolder {

    /**
     * Lock object for atomic reset operations.
     * Using a dedicated lock avoids potential deadlocks with other synchronization.
     */
    private static final Object RESET_LOCK = new Object();

    /**
     * The Hibernate cache instance shared across the application.
     * Uses AtomicReference for lock-free thread-safe initialization.
     */
    private static final AtomicReference<Cache> cacheRef = new AtomicReference<>();

    /**
     * The eviction collector for transaction-aware cache invalidation.
     * Uses AtomicReference for lock-free thread-safe initialization.
     */
    private static final AtomicReference<TransactionAwareCacheEvictionCollector> evictionCollectorRef =
            new AtomicReference<>();

    /**
     * The cache eviction strategy for fallback eviction when event publishing fails.
     * Uses AtomicReference for lock-free thread-safe initialization.
     */
    private static final AtomicReference<CacheEvictionStrategy> evictionStrategyRef = new AtomicReference<>();

    /**
     * Gets the Hibernate cache instance.
     *
     * @return the cache instance, or null if not yet initialized
     */
    public static Cache getCache() {
        return cacheRef.get();
    }

    /**
     * Gets the eviction collector instance.
     *
     * @return the eviction collector, or null if not yet initialized
     */
    public static TransactionAwareCacheEvictionCollector getEvictionCollector() {
        return evictionCollectorRef.get();
    }

    /**
     * Gets the eviction strategy instance.
     *
     * @return the eviction strategy, or null if not yet initialized
     */
    public static CacheEvictionStrategy getEvictionStrategy() {
        return evictionStrategyRef.get();
    }

    /**
     * Sets the Hibernate cache instance using atomic compareAndSet.
     *
     * <p>This method should only be called once during application initialization
     * by {@link SimpliXHibernateCacheAutoConfiguration}.</p>
     *
     * <p>Uses compareAndSet for lock-free thread safety.
     * Only the first call will set the cache; subsequent calls are ignored.</p>
     *
     * @param cache the Hibernate cache instance from SessionFactory
     * @return true if the cache was set, false if it was already initialized
     */
    public static boolean setCache(Cache cache) {
        return cacheRef.compareAndSet(null, cache);
    }

    /**
     * Sets the eviction collector using atomic compareAndSet.
     *
     * <p>This method should only be called once during application initialization
     * by {@link SimpliXHibernateCacheAutoConfiguration}.</p>
     *
     * <p>Uses compareAndSet for lock-free thread safety.
     * Only the first call will set the collector; subsequent calls are ignored.</p>
     *
     * @param collector the eviction collector instance
     * @return true if the collector was set, false if it was already initialized
     */
    public static boolean setEvictionCollector(TransactionAwareCacheEvictionCollector collector) {
        return evictionCollectorRef.compareAndSet(null, collector);
    }

    /**
     * Sets the cache eviction strategy using atomic compareAndSet.
     *
     * <p>This method should only be called once during application initialization
     * by {@link SimpliXHibernateCacheAutoConfiguration}.</p>
     *
     * <p>Uses compareAndSet for lock-free thread safety.
     * Only the first call will set the strategy; subsequent calls are ignored.</p>
     *
     * @param strategy the eviction strategy instance
     * @return true if the strategy was set, false if it was already initialized
     */
    public static boolean setEvictionStrategy(CacheEvictionStrategy strategy) {
        return evictionStrategyRef.compareAndSet(null, strategy);
    }

    /**
     * Resets all static references to null atomically.
     *
     * <p>This method must be called when the ApplicationContext is closed
     * (e.g., during test suite teardown or application shutdown) to allow
     * new beans to be registered on context refresh.</p>
     *
     * <p>Uses synchronized block to ensure all references are reset atomically,
     * preventing intermediate states where some references are null while others are not
     * (7th review fix - TOCTOU race condition).</p>
     *
     * <p><b>Important:</b> Without calling this method, subsequent ApplicationContext
     * initializations will fail to register new beans because compareAndSet(null, value)
     * will return false for non-null references.</p>
     *
     * @see SimpliXHibernateCacheAutoConfiguration
     */
    public static void reset() {
        synchronized (RESET_LOCK) {
            cacheRef.set(null);
            evictionCollectorRef.set(null);
            evictionStrategyRef.set(null);
        }
    }

    /**
     * Checks if all references are currently null (either not initialized or after reset).
     * Useful for defensive checks during context refresh scenarios.
     *
     * @return true if all references are null
     */
    public static boolean isReset() {
        synchronized (RESET_LOCK) {
            return cacheRef.get() == null
                    && evictionCollectorRef.get() == null
                    && evictionStrategyRef.get() == null;
        }
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static members.
     */
    private HibernateCacheHolder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
