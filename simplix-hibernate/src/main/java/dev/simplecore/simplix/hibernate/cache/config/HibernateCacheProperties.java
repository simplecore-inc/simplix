package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.core.CacheMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

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
     * Cache provider configuration
     */
    private Provider provider = new Provider();

    /**
     * Region-specific configurations
     */
    private Map<String, RegionConfig> regions = new HashMap<>();

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

        /**
         * Redis key prefix
         */
        private String keyPrefix = "hibernate:cache:";

        /**
         * Connection timeout in milliseconds
         */
        private int connectionTimeout = 2000;
    }

    @Data
    public static class Provider {
        /**
         * Cache provider type
         */
        private String type = "ehcache";

        /**
         * Enable statistics
         */
        private boolean statisticsEnabled = false;

        /**
         * Default cache TTL in seconds
         */
        private long defaultTtl = 3600;

        /**
         * Maximum entries in cache
         */
        private long maxEntries = 10000;
    }

    @Data
    public static class RegionConfig {
        /**
         * TTL for this region in seconds
         */
        private long ttl = 3600;

        /**
         * Max entries for this region
         */
        private long maxEntries = 1000;

        /**
         * Enable query cache for this region
         */
        private boolean queryCacheEnabled = true;
    }
}