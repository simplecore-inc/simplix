package dev.simplecore.simplix.hibernate.cache.config;

import org.hibernate.Cache;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static holder for Hibernate cache instance.
 *
 * <p>This class provides a static reference to the Hibernate Cache that can be
 * accessed across the application.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses AtomicReference with compareAndSet() for lock-free, thread-safe initialization.
 * The reference is set exactly once - the first successful call wins, subsequent calls are ignored.</p>
 *
 * @see SimpliXHibernateCacheAutoConfiguration
 */
public class HibernateCacheHolder {

    /**
     * The Hibernate cache instance shared across the application.
     * Uses AtomicReference for lock-free thread-safe initialization.
     */
    private static final AtomicReference<Cache> cacheRef = new AtomicReference<>();

    /**
     * Gets the Hibernate cache instance.
     *
     * @return the cache instance, or null if not yet initialized
     */
    public static Cache getCache() {
        return cacheRef.get();
    }

    /**
     * Sets the Hibernate cache instance using atomic compareAndSet.
     *
     * <p>This method should only be called once during application initialization
     * by {@link SimpliXHibernateCacheAutoConfiguration}.</p>
     *
     * @param cache the Hibernate cache instance from SessionFactory
     * @return true if the cache was set, false if it was already initialized
     */
    public static boolean setCache(Cache cache) {
        return cacheRef.compareAndSet(null, cache);
    }

    /**
     * Resets the cache reference to null.
     *
     * <p>This method must be called when the ApplicationContext is closed
     * to allow new beans to be registered on context refresh.</p>
     */
    static void reset() {
        cacheRef.set(null);
    }

    /**
     * Checks if the cache reference is null (not initialized or after reset).
     *
     * @return true if the cache reference is null
     */
    public static boolean isReset() {
        return cacheRef.get() == null;
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private HibernateCacheHolder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
