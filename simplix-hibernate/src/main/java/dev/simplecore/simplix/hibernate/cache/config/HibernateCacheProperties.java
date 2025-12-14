package dev.simplecore.simplix.hibernate.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Hibernate cache management.
 *
 * <p>This module focuses on @Modifying query cache eviction support.
 * For distributed cache synchronization, use Hibernate's native integration
 * with cache providers (Ehcache, Hazelcast, Infinispan, etc.).</p>
 */
@Data
@ConfigurationProperties(prefix = "simplix.hibernate.cache")
public class HibernateCacheProperties {

    /**
     * Disable auto cache management (set to true to completely disable)
     */
    private boolean disabled = false;

    /**
     * Enable query cache auto-eviction
     */
    private boolean queryCacheAutoEviction = true;

    /**
     * Packages to scan for @Cache entities
     */
    private String[] scanPackages;
}
