package dev.simplecore.simplix.event.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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
     * Instance ID for this application instance
     */
    private String instanceId;

    /**
     * Whether events should be persistent by default
     */
    private boolean persistentByDefault = false;

    /**
     * Whether events should be published asynchronously by default
     */
    private boolean asyncByDefault = true;

    /**
     * Default time-to-live for events
     */
    private Duration defaultTtl = Duration.ofHours(24);

    /**
     * Retry configuration
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * Redis-specific configuration
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Kafka-specific configuration
     */
    private KafkaConfig kafka = new KafkaConfig();

    /**
     * RabbitMQ-specific configuration
     */
    private RabbitConfig rabbit = new RabbitConfig();

    /**
     * Monitoring and metrics configuration
     */
    private MonitoringConfig monitoring = new MonitoringConfig();

    @Data
    public static class RetryConfig {
        /**
         * Maximum number of retry attempts
         */
        private int maxAttempts = 3;

        /**
         * Delay between retry attempts
         */
        private Duration backoff = Duration.ofSeconds(1);

        /**
         * Whether to use exponential backoff
         */
        private boolean exponentialBackoff = false;

        /**
         * Maximum backoff duration
         */
        private Duration maxBackoff = Duration.ofMinutes(1);
    }

    @Data
    public static class RedisConfig {
        /**
         * Stream name prefix for Redis Streams
         */
        private String streamPrefix = "simplix-events";

        /**
         * Default TTL for stored events (for MAXLEN trimming)
         */
        private Duration defaultTtl = Duration.ofHours(24);

        /**
         * Redis key prefix for stored events
         */
        private String keyPrefix = "events";

        /**
         * Connection timeout
         */
        private Duration connectionTimeout = Duration.ofSeconds(5);

        /**
         * Redis Stream configuration
         */
        private StreamConfig stream = new StreamConfig();

        /**
         * Batch processing configuration
         */
        private BatchConfig batch = new BatchConfig();

        @Data
        public static class StreamConfig {
            /**
             * Enable Redis Stream (default true, falls back to Pub/Sub if false)
             */
            private boolean enabled = true;

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
             * Block duration in milliseconds when reading from stream
             * 0 means non-blocking, null means indefinite blocking
             */
            private Long blockMillis = 1000L;

            /**
             * Number of messages to read in one batch
             */
            private int batchSize = 100;

            /**
             * Auto-create consumer group if it doesn't exist
             */
            private boolean autoCreateGroup = true;

            /**
             * Start reading from the beginning if consumer group is new
             * If false, starts from latest messages only
             */
            private boolean readFromBeginning = false;

            /**
             * Enable pending message processing (messages that were read but not acknowledged)
             */
            private boolean processPending = true;

            /**
             * Idle time in milliseconds before considering a pending message as failed
             * and eligible for reprocessing by another consumer
             */
            private long pendingMessageTimeout = 300000; // 5 minutes

            /**
             * Maximum delivery attempts for a message before moving to DLQ
             */
            private int maxDeliveryAttempts = 3;
        }

        @Data
        public static class BatchConfig {
            /**
             * Enable batch processing
             */
            private boolean enabled = false;

            /**
             * Batch size
             */
            private int size = 100;

            /**
             * Flush interval in milliseconds
             */
            private long flushInterval = 5000;
        }
    }

    @Data
    public static class KafkaConfig {
        /**
         * Topic prefix for Kafka topics
         */
        private String topicPrefix = "simplix-events";

        /**
         * Default topic name
         */
        private String defaultTopic = "domain-events";

        /**
         * Producer configuration
         */
        private ProducerConfig producer = new ProducerConfig();

        /**
         * Consumer configuration
         */
        private ConsumerConfig consumer = new ConsumerConfig();

        /**
         * Producer configuration map (additional properties)
         */
        private Map<String, String> producerConfig = new HashMap<>();

        /**
         * Consumer configuration map (additional properties)
         */
        private Map<String, String> consumerConfig = new HashMap<>();

        /**
         * Number of partitions for auto-created topics
         */
        private int defaultPartitions = 3;

        /**
         * Replication factor for auto-created topics
         */
        private short replicationFactor = 1;

        /**
         * Whether to auto-create topics
         */
        private boolean autoCreateTopics = true;

        @Data
        public static class ProducerConfig {
            /**
             * Acknowledgment level (0=none, 1=leader, -1/all=all replicas)
             */
            private String acks = "1";

            /**
             * Number of retries for failed sends
             */
            private int retries = 3;

            /**
             * Retry backoff in milliseconds
             */
            private long retryBackoffMs = 100;

            /**
             * Batch size for batching messages
             */
            private int batchSize = 16384;

            /**
             * Linger time in milliseconds
             */
            private long lingerMs = 10;

            /**
             * Buffer memory for unsent messages
             */
            private long bufferMemory = 33554432;

            /**
             * Compression type (none, gzip, snappy, lz4, zstd)
             */
            private String compressionType = "snappy";
        }

        @Data
        public static class ConsumerConfig {
            /**
             * Consumer group ID
             */
            private String groupId = "simplix-events-consumer";

            /**
             * Auto offset reset (earliest, latest, none)
             */
            private String autoOffsetReset = "earliest";

            /**
             * Enable auto commit
             */
            private boolean enableAutoCommit = true;
        }
    }

    @Data
    public static class RabbitConfig {
        /**
         * Exchange name
         */
        private String exchange = "simplix.events";

        /**
         * Exchange type (direct, topic, fanout, headers)
         */
        private String exchangeType = "topic";

        /**
         * Whether the exchange should be durable
         */
        private boolean durableExchange = true;

        /**
         * Whether the exchange should be auto-deleted
         */
        private boolean autoDeleteExchange = false;

        /**
         * Queue name
         */
        private String queue = "simplix.events.queue";

        /**
         * Whether the queue should be durable
         */
        private boolean durableQueue = true;

        /**
         * Whether the queue should be exclusive
         */
        private boolean exclusiveQueue = false;

        /**
         * Whether the queue should be auto-deleted
         */
        private boolean autoDeleteQueue = false;

        /**
         * Routing key prefix
         */
        private String routingKeyPrefix = "event.";

        /**
         * Message TTL in milliseconds
         */
        private long ttl = 86400000; // 24 hours

        /**
         * Message converter type (json, java, etc.)
         */
        private String messageConverter = "json";

        /**
         * Max retries for message processing
         */
        private int maxRetries = 3;

        /**
         * Retry delay in milliseconds
         */
        private long retryDelay = 1000;

        /**
         * Retry multiplier for exponential backoff
         */
        private double retryMultiplier = 2.0;

        /**
         * Max retry delay in milliseconds
         */
        private long maxRetryDelay = 30000;

        /**
         * Dead letter queue configuration
         */
        private DlqConfig dlq = new DlqConfig();

        /**
         * Connection configuration
         */
        private ConnectionConfig connection = new ConnectionConfig();

        @Data
        public static class DlqConfig {
            /**
             * Whether DLQ is enabled
             */
            private boolean enabled = true;

            /**
             * DLQ exchange name
             */
            private String exchange = "simplix.events.dlq";

            /**
             * DLQ queue name
             */
            private String queue = "simplix.events.dlq.queue";

            /**
             * DLQ routing key
             */
            private String routingKey = "dlq.#";

            /**
             * DLQ TTL in milliseconds
             */
            private long ttl = 604800000; // 7 days
        }

        @Data
        public static class ConnectionConfig {
            /**
             * Connection timeout in milliseconds
             */
            private long timeout = 30000;

            /**
             * Requested heartbeat interval in seconds
             */
            private int requestedHeartbeat = 60;
        }
    }

    @Data
    public static class MonitoringConfig {
        /**
         * Whether to enable event metrics
         */
        private boolean metricsEnabled = true;

        /**
         * Whether to enable event tracing
         */
        private boolean tracingEnabled = false;

        /**
         * Whether to log event content (be careful with sensitive data)
         */
        private boolean logEventContent = false;

        /**
         * Metrics prefix
         */
        private String metricsPrefix = "simplix.events";

        /**
         * Health check configuration
         */
        private HealthCheckConfig healthCheck = new HealthCheckConfig();

        /**
         * Event statistics configuration
         */
        private StatisticsConfig statistics = new StatisticsConfig();
    }

    @Data
    public static class HealthCheckConfig {
        /**
         * Whether to include event publisher in health checks
         */
        private boolean enabled = true;

        /**
         * Health check interval in milliseconds
         */
        private long interval = 30000; // 30 seconds

        /**
         * Timeout for health check operations
         */
        private Duration timeout = Duration.ofSeconds(5);
    }

    @Data
    public static class StatisticsConfig {
        /**
         * Whether to enable event statistics collection
         */
        private boolean enabled = true;

        /**
         * Statistics window size in seconds
         */
        private int windowSize = 60; // 60 seconds
    }
}