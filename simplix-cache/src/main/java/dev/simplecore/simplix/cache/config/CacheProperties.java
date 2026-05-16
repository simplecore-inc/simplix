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
     * NATS-specific configuration
     */
    private NatsConfig nats = new NatsConfig();

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

    /**
     * NATS KV-specific configuration.
     *
     * <p>Each cache name is mapped to a separate JetStream KV bucket named
     * {@code <bucketPrefix><sanitized cacheName>}. Because NATS KV honours
     * a single {@code maxAge} per bucket, the configured TTL is applied at
     * bucket creation; per-call TTL parameters cannot override the bucket
     * setting once the bucket exists. Cache names containing characters that
     * are invalid in NATS bucket names ({@code .}, {@code *}, {@code >},
     * spaces, etc.) are sanitised by replacing each invalid character with
     * {@code _}.
     */
    @Data
    public static class NatsConfig {
        /** Prefix prepended to every per-cache KV bucket name. */
        private String bucketPrefix = "simplix-cache-";

        /** Default replicas for newly created KV buckets. */
        private int replicas = 1;
    }
}