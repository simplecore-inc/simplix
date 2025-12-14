package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.core.CacheMode;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Configuration properties for Hibernate cache management
 */
@Data
@ConfigurationProperties(prefix = "simplix.hibernate.cache")
public class HibernateCacheProperties {

    private static final Logger log = LoggerFactory.getLogger(HibernateCacheProperties.class);

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
     * Node ID for distributed cache.
     * If not explicitly set, generates a stable ID based on hostname + random UUID suffix.
     * Set this explicitly in production for consistent node identification across restarts.
     * Uses lazy initialization to avoid logging during class loading.
     */
    private String nodeId;

    /**
     * Gets the node ID, generating a default if not explicitly set.
     * Uses lazy initialization to avoid logging during class loading.
     *
     * @return the node ID
     */
    public String getNodeId() {
        if (nodeId == null || nodeId.isEmpty()) {
            nodeId = generateDefaultNodeId();
        }
        return nodeId;
    }

    /**
     * Redis configuration
     */
    private Redis redis = new Redis();

    /**
     * Retry configuration for failed evictions
     */
    private Retry retry = new Retry();

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
         * Key prefix for cache entries
         */
        private String keyPrefix = "hibernate:cache:";

        /**
         * Connection timeout in milliseconds.
         * Increased from 2000ms to 5000ms to reduce false failures under network latency.
         */
        private int connectionTimeout = 5000;
    }

    @Data
    public static class Retry {
        /**
         * Maximum retry attempts for failed evictions
         */
        private int maxAttempts = 3;

        /**
         * Delay between retry attempts in milliseconds
         */
        private long delayMs = 1000;
    }

    /**
     * Generates a default node ID based on hostname and a random UUID suffix.
     * This provides a more stable identifier than timestamp-based IDs.
     * Logs a warning since auto-generated IDs change on restart.
     *
     * @return a default node ID
     */
    private static String generateDefaultNodeId() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
            // Truncate long hostnames
            if (hostname.length() > 20) {
                hostname = hostname.substring(0, 20);
            }
        } catch (Exception e) {
            hostname = "unknown";
        }

        // Add short UUID suffix for uniqueness within same host
        String uuidSuffix = UUID.randomUUID().toString().substring(0, 8);
        String generatedId = hostname + "-" + uuidSuffix;

        // Log warning about auto-generated node ID
        log.warn("⚠ Using auto-generated node ID: {}. For production, set 'simplix.hibernate.cache.node-id' " +
                "explicitly for consistent node identification across restarts.", generatedId);

        return generatedId;
    }
}