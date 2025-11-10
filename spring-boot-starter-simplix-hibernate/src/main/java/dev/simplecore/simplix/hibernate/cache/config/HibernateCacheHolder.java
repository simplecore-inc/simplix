package dev.simplecore.simplix.hibernate.cache.config;

import lombok.Getter;
import org.hibernate.Cache;

/**
 * Static holder for Hibernate cache instance.
 *
 * <p>This class provides a static reference to the Hibernate Cache that can be accessed
 * from {@link HibernateIntegrator}, which is instantiated by Hibernate's Service Provider
 * Interface (SPI) and cannot use Spring dependency injection.</p>
 *
 * <h3>Why Static Holder Pattern?</h3>
 * <ul>
 *   <li>Hibernate Integrator is loaded via Java SPI before Spring context initialization</li>
 *   <li>HibernateIntegrator cannot receive Spring-managed beans through constructor injection</li>
 *   <li>The cache instance must be shared between Spring-managed beans and SPI-loaded components</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Spring initializes {@link SimpliXHibernateCacheAutoConfiguration}</li>
 *   <li>Cache reference is extracted from SessionFactory and stored here via {@link #setCache(Cache)}</li>
 *   <li>HibernateIntegrator retrieves the cache via {@link #getCache()} for event listeners</li>
 * </ol>
 *
 * <p><b>Note:</b> This pattern is generally not recommended but necessary due to Hibernate's
 * SPI initialization constraints. The cache is set once during application startup and
 * remains immutable throughout the application lifecycle.</p>
 *
 * @see HibernateIntegrator
 * @see SimpliXHibernateCacheAutoConfiguration#hibernateCacheManager
 */
public class HibernateCacheHolder {

    /**
     * The Hibernate cache instance shared across the application.
     * This field is set once during application startup and should not be modified afterward.
     */
    @Getter
    private static Cache cache;

    /**
     * Sets the Hibernate cache instance.
     *
     * <p>This method should only be called once during application initialization
     * by {@link SimpliXHibernateCacheAutoConfiguration}.</p>
     *
     * @param cache the Hibernate cache instance from SessionFactory
     */
    public static void setCache(Cache cache) {
        HibernateCacheHolder.cache = cache;
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static members.
     */
    private HibernateCacheHolder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}