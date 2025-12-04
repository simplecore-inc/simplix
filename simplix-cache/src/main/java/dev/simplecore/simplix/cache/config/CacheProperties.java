package dev.simplecore.simplix.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache Configuration Properties
 */
@Data
@ConfigurationProperties(prefix = "simplix.cache")
public class CacheProperties {

    /**
     * Cache mode: local, redis
     */
    private String mode = "local";

    /**
     * Default TTL in seconds
     */
    private long defaultTtlSeconds = 3600; // 1 hour

    /**
     * Whether to cache null values
     */
    private boolean cacheNullValues = false;

    /**
     * Cache-specific configurations
     */
    private Map<String, CacheConfig> cacheConfigs = new HashMap<>();

    /**
     * Metrics configuration
     */
    private MetricsConfig metrics = new MetricsConfig();

    /**
     * Redis-specific configuration
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Initialize default cache configurations
     */
    public CacheProperties() {
        // Default cache configuration
        // Applications should define their own cache configurations in application.yml
        cacheConfigs.put("default", new CacheConfig(3600L));
    }

    /**
     * Individual cache configuration
     */
    @Data
    public static class CacheConfig {
        private long ttlSeconds;

        public CacheConfig() {
            this.ttlSeconds = 3600L;
        }

        public CacheConfig(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    /**
     * Metrics configuration
     */
    @Data
    public static class MetricsConfig {
        private boolean enabled = true;
    }

    /**
     * Redis-specific configuration
     */
    @Data
    public static class RedisConfig {
        private String keyPrefix = "cache:";
        private boolean useKeyPrefix = true;
    }
}