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
     * Cache mode: local, redis, hazelcast
     */
    private String mode = "local";

    /**
     * Default TTL in seconds
     */
    private long defaultTtlSeconds = 3600; // 1 hour

    /**
     * Maximum cache size for local caches
     */
    private long maxSize = 10000;

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
     * Hazelcast-specific configuration
     */
    private HazelcastConfig hazelcast = new HazelcastConfig();

    /**
     * Initialize default cache configurations
     */
    public CacheProperties() {
        // Default cache configuration
        // Applications should define their own cache configurations in application.yml
        cacheConfigs.put("default", new CacheConfig(3600L, 1000L));
    }

    /**
     * Individual cache configuration
     */
    @Data
    public static class CacheConfig {
        private long ttlSeconds;
        private long maxSize;

        public CacheConfig() {
            this.ttlSeconds = 3600L;
            this.maxSize = 1000L;
        }

        public CacheConfig(long ttlSeconds, long maxSize) {
            this.ttlSeconds = ttlSeconds;
            this.maxSize = maxSize;
        }
    }

    /**
     * Metrics configuration
     */
    @Data
    public static class MetricsConfig {
        private boolean enabled = true;
        private long collectionIntervalSeconds = 60;
    }

    /**
     * Redis-specific configuration
     */
    @Data
    public static class RedisConfig {
        private String keyPrefix = "cache:";
        private boolean useKeyPrefix = true;
        private long commandTimeout = 2000; // milliseconds
        private boolean enableStatistics = true;
    }

    /**
     * Hazelcast-specific configuration
     */
    @Data
    public static class HazelcastConfig {
        private String instanceName = "simplix-cache";
        private boolean enableStatistics = true;
        private int backupCount = 1;
        private boolean readBackupData = true;
    }
}