package dev.simplecore.simplix.event.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the event module
 * Allows configuration via application.yml/properties
 */
@Data
@ConfigurationProperties(prefix = "simplix.events")
public class EventProperties {

    /**
     * Event publishing mode: local, redis, kafka
     */
    private String mode = "local";

    /**
     * Whether to enrich events with metadata (instance ID, timestamp, etc.)
     */
    private boolean enrichMetadata = true;

    /**
     * Whether events should be persistent by default
     */
    private boolean persistentByDefault = false;

    /**
     * Redis-specific configuration
     */
    private RedisConfig redis = new RedisConfig();

    @Data
    public static class RedisConfig {
        /**
         * Stream name prefix for Redis Streams
         */
        private String streamPrefix = "simplix-events";

        /**
         * Redis Stream configuration
         */
        private StreamConfig stream = new StreamConfig();

        @Data
        public static class StreamConfig {
            /**
             * Consumer group name
             */
            private String consumerGroup = "simplix-events-group";

            /**
             * Consumer name (auto-generated if not specified)
             */
            private String consumerName;

            /**
             * Maximum stream length (approximate). 0 means no limit.
             * Uses MAXLEN ~ for approximate trimming (more efficient)
             */
            private long maxLen = 10000;

            /**
             * Auto-create consumer group if it doesn't exist
             */
            private boolean autoCreateGroup = true;

            /**
             * Start reading from the beginning if consumer group is new
             * If false, starts from latest messages only
             */
            private boolean readFromBeginning = false;
        }
    }
}
