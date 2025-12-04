package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.core.CacheMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Hibernate cache management
 */
@Data
@ConfigurationProperties(prefix = "simplix.hibernate.cache")
public class HibernateCacheProperties {

    /**
     * Disable auto cache management (set to true to completely disable)
     */
    private boolean disabled = false;  // Default enabled, opt-out approach

    /**
     * Cache operation mode (AUTO will detect best available)
     */
    private CacheMode mode = CacheMode.AUTO;

    /**
     * Enable query cache auto-eviction
     */
    private boolean queryCacheAutoEviction = true;

    /**
     * Auto-detect best eviction strategy (JPA, AOP, or Global)
     */
    private boolean autoDetectEvictionStrategy = true;

    /**
     * Node ID for distributed cache
     */
    private String nodeId = "node-" + System.currentTimeMillis();

    /**
     * Redis configuration
     */
    private Redis redis = new Redis();

    /**
     * Packages to scan for @Cache entities
     */
    private String[] scanPackages;

    @Data
    public static class Redis {
        /**
         * Redis channel for cache sync
         */
        private String channel = "hibernate-cache-sync";

        /**
         * Enable Redis pub/sub for cache sync
         */
        private boolean pubSubEnabled = true;
    }
}