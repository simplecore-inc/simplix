package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.redis.PayloadEncoding;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for simplix-messaging module.
 *
 * <p>Bound to the {@code simplix.messaging} prefix. Provides sensible defaults
 * for local-only messaging that can be overridden per environment.
 *
 * <p>Example configuration:
 * <pre>{@code
 * simplix:
 *   messaging:
 *     broker: redis
 *     instance-id: pacs-studio-1
 *     channels:
 *       order-events:
 *         content-type: application/protobuf
 *         max-length: 100000
 *     redis:
 *       key-prefix: "pacs:"
 *       poll-timeout: 3s
 *       batch-size: 20
 *     idempotent:
 *       ttl: 48h
 *     error:
 *       max-retries: 5
 *       retry-backoff: 2s
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "simplix.messaging")
public class MessagingProperties {

    /**
     * Broker type to activate. Supported values: "local", "redis".
     */
    private String broker = "local";

    /**
     * Unique instance identifier used for consumer naming.
     * Defaults to the hostname of the current machine.
     */
    private String instanceId = resolveDefaultInstanceId();

    /**
     * Per-channel configuration overrides.
     */
    private Map<String, ChannelProperties> channels = new LinkedHashMap<>();

    /**
     * Redis broker configuration.
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * Idempotent guard configuration for message deduplication.
     */
    private IdempotentProperties idempotent = new IdempotentProperties();

    /**
     * Error handling and retry configuration.
     */
    private ErrorProperties error = new ErrorProperties();

    /**
     * Delay before starting message subscribers after the application is ready.
     *
     * <p>Allows SSE clients and other downstream consumers to establish connections
     * before the broker begins delivering backlogged messages. Set to {@code 0s}
     * for immediate start (default for local broker).
     */
    private Duration subscriberStartupDelay = Duration.ZERO;

    // ---------------------------------------------------------------
    // Nested property classes
    // ---------------------------------------------------------------

    /**
     * Per-channel configuration properties.
     */
    @Data
    public static class ChannelProperties {

        /**
         * Content type for messages on this channel.
         */
        private String contentType = "application/json";

        /**
         * Approximate maximum number of entries retained in the stream (Redis MAXLEN ~).
         */
        private long maxLength = 50_000L;
    }

    /**
     * Redis-specific broker configuration.
     */
    @Data
    public static class RedisProperties {

        /**
         * Prefix applied to all Redis stream keys (e.g., "pacs:" produces "pacs:channel-name").
         */
        private String keyPrefix = "";

        /**
         * Interval for checking pending (unacknowledged) messages.
         */
        private Duration pendingCheckInterval = Duration.ofSeconds(30);

        /**
         * Minimum idle time before a pending message can be claimed by another consumer.
         */
        private Duration claimMinIdleTime = Duration.ofMinutes(5);

        /**
         * Duration to block-wait when polling for new messages.
         */
        private Duration pollTimeout = Duration.ofSeconds(2);

        /**
         * Maximum number of messages fetched per poll cycle.
         */
        private int batchSize = 10;

        /**
         * Payload encoding strategy for binary data storage in Redis Streams.
         *
         * <p>{@code BASE64} (default) - encodes binary payload as Base64 string.
         * <p>{@code RAW} - stores raw binary bytes directly (enables protobuf viewer in Redis clients).
         */
        private PayloadEncoding payloadEncoding = PayloadEncoding.BASE64;
    }

    /**
     * Idempotent message guard configuration.
     */
    @Data
    public static class IdempotentProperties {

        /**
         * Time-to-live for processed message ID records used in deduplication.
         */
        private Duration ttl = Duration.ofHours(24);
    }

    /**
     * Error handling and retry configuration.
     */
    @Data
    public static class ErrorProperties {

        /**
         * Maximum number of retry attempts before routing to dead letter.
         */
        private int maxRetries = 3;

        /**
         * Initial backoff duration between retry attempts.
         */
        private Duration retryBackoff = Duration.ofSeconds(1);

        /**
         * Dead letter queue configuration.
         */
        private DeadLetterProperties deadLetter = new DeadLetterProperties();
    }

    /**
     * Dead letter queue configuration properties.
     */
    @Data
    public static class DeadLetterProperties {

        /**
         * Whether dead letter queue routing is enabled.
         */
        private boolean enabled = false;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Resolve the default max length for a channel, falling back to 50000 if not configured.
     *
     * @param channel the channel name
     * @return the configured max length or the default
     */
    public long resolveMaxLength(String channel) {
        ChannelProperties props = channels.get(channel);
        if (props != null) {
            return props.getMaxLength();
        }
        return 50_000L;
    }

    private static String resolveDefaultInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "simplix-" + ProcessHandle.current().pid();
        }
    }
}
