package dev.simplecore.simplix.stream.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for SimpliX Stream module.
 * <p>
 * Provides configuration for real-time subscription system including
 * session management, scheduler settings, and distributed mode options.
 */
@Data
@ConfigurationProperties(prefix = "simplix.stream")
public class StreamProperties {

    /**
     * Enable or disable the stream module
     */
    private boolean enabled = true;

    /**
     * Operation mode: local or distributed
     */
    private Mode mode = Mode.LOCAL;

    /**
     * Session management configuration
     */
    private SessionConfig session = new SessionConfig();

    /**
     * Scheduler configuration
     */
    private SchedulerConfig scheduler = new SchedulerConfig();

    /**
     * Subscription configuration
     */
    private SubscriptionConfig subscription = new SubscriptionConfig();

    /**
     * Broadcast configuration
     */
    private BroadcastConfig broadcast = new BroadcastConfig();

    /**
     * Distributed mode configuration (Redis)
     */
    private DistributedConfig distributed = new DistributedConfig();

    /**
     * Server instance configuration (for distributed mode)
     */
    private ServerConfig server = new ServerConfig();

    /**
     * Monitoring configuration
     */
    private MonitoringConfig monitoring = new MonitoringConfig();

    /**
     * Security configuration
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * WebSocket configuration
     */
    private WebSocketConfig websocket = new WebSocketConfig();

    /**
     * Admin command configuration for distributed mode
     */
    private AdminConfig admin = new AdminConfig();

    /**
     * Event publishing integration configuration
     */
    private EventPublishingConfig eventPublishing = new EventPublishingConfig();

    /**
     * Event-based streaming configuration (using simplix-event)
     */
    private EventSourceConfig eventSource = new EventSourceConfig();

    /**
     * Operation mode enum
     */
    public enum Mode {
        LOCAL,
        DISTRIBUTED
    }

    /**
     * Session management configuration
     */
    @Data
    public static class SessionConfig {
        /**
         * SSE connection timeout (0 for unlimited)
         */
        private Duration timeout = Duration.ofMinutes(5);

        /**
         * Heartbeat interval
         */
        private Duration heartbeatInterval = Duration.ofSeconds(30);

        /**
         * Grace period after disconnection for reconnection
         */
        private Duration gracePeriod = Duration.ofSeconds(30);

        /**
         * Interval for cleaning up inactive sessions
         */
        private Duration cleanupInterval = Duration.ofSeconds(30);

        /**
         * Maximum sessions per user (0 for unlimited)
         */
        private int maxPerUser = 5;
    }

    /**
     * Scheduler configuration for data collection
     */
    @Data
    public static class SchedulerConfig {
        /**
         * Thread pool size for scheduler execution
         */
        private int threadPoolSize = 10;

        /**
         * Default push interval
         */
        private Duration defaultInterval = Duration.ofMillis(1000);

        /**
         * Minimum allowed push interval
         */
        private Duration minInterval = Duration.ofMillis(100);

        /**
         * Maximum allowed push interval
         */
        private Duration maxInterval = Duration.ofMillis(60000);

        /**
         * Maximum consecutive errors before ERROR state
         */
        private int maxConsecutiveErrors = 5;

        /**
         * Maximum total schedulers (0 for unlimited)
         */
        private int maxTotalSchedulers = 500;
    }

    /**
     * Subscription configuration
     */
    @Data
    public static class SubscriptionConfig {
        /**
         * Maximum subscriptions per session
         */
        private int maxPerSession = 20;

        /**
         * Allow partial success when some subscriptions are denied
         */
        private boolean partialSuccess = true;
    }

    /**
     * Broadcast configuration
     */
    @Data
    public static class BroadcastConfig {
        /**
         * Message send timeout
         */
        private Duration timeout = Duration.ofSeconds(5);

        /**
         * Batch size for message sending (0 to disable batching)
         */
        private int batchSize = 0;
    }

    /**
     * Distributed mode configuration (Redis)
     */
    @Data
    public static class DistributedConfig {
        /**
         * Enable Redis for distributed features (leader election + Pub/Sub broadcast).
         * When enabled in DISTRIBUTED mode:
         * - Leader election ensures only one server runs each scheduler
         * - Pub/Sub broadcasts data to all servers
         * When disabled in DISTRIBUTED mode:
         * - Each server runs schedulers independently
         * - No cross-server data broadcast
         */
        private boolean redisEnabled = false;

        /**
         * Leader election configuration
         */
        private LeaderElectionConfig leaderElection = new LeaderElectionConfig();

        /**
         * Pub/Sub configuration
         */
        private PubSubConfig pubsub = new PubSubConfig();

        /**
         * Registry configuration
         */
        private RegistryConfig registry = new RegistryConfig();
    }

    /**
     * Server instance configuration for distributed mode.
     * Tracks server instances in DB for orphan session detection.
     */
    @Data
    public static class ServerConfig {
        /**
         * Unique instance ID. If not set, a UUID will be generated.
         */
        private String instanceId;

        /**
         * Heartbeat interval for server health check.
         */
        private Duration heartbeatInterval = Duration.ofSeconds(30);

        /**
         * Threshold after which a server is considered dead.
         * Should be at least 2x heartbeatInterval.
         */
        private Duration deadThreshold = Duration.ofMinutes(2);

        /**
         * Interval for cleaning up dead servers and orphan sessions.
         */
        private Duration cleanupInterval = Duration.ofSeconds(60);
    }

    /**
     * Leader election configuration for distributed mode
     */
    @Data
    public static class LeaderElectionConfig {
        /**
         * Leadership TTL
         */
        private Duration ttl = Duration.ofSeconds(30);

        /**
         * Leadership renewal interval
         */
        private Duration renewInterval = Duration.ofSeconds(10);

        /**
         * Election retry interval
         */
        private Duration retryInterval = Duration.ofSeconds(5);
    }

    /**
     * Pub/Sub configuration for distributed mode
     */
    @Data
    public static class PubSubConfig {
        /**
         * Channel prefix for data broadcasting
         */
        private String channelPrefix = "stream:data:";
    }

    /**
     * Registry configuration for distributed mode
     */
    @Data
    public static class RegistryConfig {
        /**
         * Key prefix for Redis keys
         */
        private String keyPrefix = "stream:";

        /**
         * TTL for registry entries
         */
        private Duration ttl = Duration.ofHours(1);
    }

    /**
     * Monitoring configuration
     */
    @Data
    public static class MonitoringConfig {
        /**
         * Enable metrics collection
         */
        private boolean metricsEnabled = true;

        /**
         * Metrics name prefix
         */
        private String metricsPrefix = "simplix.stream";

        /**
         * Health check interval
         */
        private Duration healthCheckInterval = Duration.ofSeconds(10);
    }

    /**
     * Security configuration
     */
    @Data
    public static class SecurityConfig {
        /**
         * Enforce authorization for all resources.
         * When true, subscriptions to resources without an authorizer will be denied.
         * When false, resources without an authorizer are allowed by default.
         */
        private boolean enforceAuthorization = false;

        /**
         * Require authentication for stream connections.
         */
        private boolean requireAuthentication = false;
    }

    /**
     * WebSocket transport configuration
     */
    @Data
    public static class WebSocketConfig {
        /**
         * Enable WebSocket transport.
         */
        private boolean enabled = false;

        /**
         * WebSocket endpoint path.
         */
        private String endpoint = "/ws/stream";

        /**
         * Allowed origins pattern.
         */
        private String allowedOrigins = "*";

        /**
         * Enable SockJS fallback.
         */
        private boolean sockjsEnabled = true;
    }

    /**
     * Event publishing integration configuration
     */
    @Data
    public static class EventPublishingConfig {
        /**
         * Enable event publishing via simplix-event
         */
        private boolean enabled = false;

        /**
         * Events to publish
         */
        private EventsConfig events = new EventsConfig();
    }

    /**
     * Individual event publishing configuration
     */
    @Data
    public static class EventsConfig {
        /**
         * Publish session connected events
         */
        private boolean sessionConnected = true;

        /**
         * Publish session disconnected events
         */
        private boolean sessionDisconnected = true;

        /**
         * Publish subscription changed events
         */
        private boolean subscriptionChanged = false;
    }

    /**
     * Admin command configuration for distributed admin operations
     */
    @Data
    public static class AdminConfig {
        /**
         * Enable database-based admin commands for distributed mode.
         * When enabled, admin operations (terminate session, stop scheduler, etc.)
         * are queued to the database and executed by the instance that owns the target.
         */
        private boolean enabled = false;

        /**
         * Polling interval for checking pending commands.
         */
        private Duration pollingInterval = Duration.ofSeconds(2);

        /**
         * Command timeout. Commands not executed within this time will expire.
         */
        private Duration commandTimeout = Duration.ofMinutes(5);

        /**
         * Retention period for executed/expired commands before cleanup.
         */
        private Duration retentionPeriod = Duration.ofDays(7);

        /**
         * Cleanup cron expression for removing old commands.
         */
        private String cleanupCron = "0 0 3 * * ?";
    }

    /**
     * Event-based streaming configuration.
     * <p>
     * Enables streaming via simplix-event instead of polling via SimpliXStreamDataCollector.
     * When enabled, SimpliXStreamEventSource implementations receive events immediately
     * and push data to subscribers without scheduler overhead.
     */
    @Data
    public static class EventSourceConfig {
        /**
         * Enable event-based streaming.
         * When enabled, SimpliXStreamEventSource beans will be auto-discovered and
         * events will be routed to stream subscribers.
         * <p>
         * Requires simplix-event module to be on the classpath.
         */
        private boolean enabled = false;
    }
}
