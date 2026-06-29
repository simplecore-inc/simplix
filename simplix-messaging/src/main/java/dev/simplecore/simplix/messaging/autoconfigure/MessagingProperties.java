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
     *
     * <p>Defaults to the sanitized hostname of the current machine. The setter
     * also applies {@link #sanitizeInstanceId(String)} so that values supplied
     * via configuration cannot leak NATS-incompatible characters into consumer
     * durable names — macOS hosts for example yield names ending in
     * {@code .local}, which the broker rejects.
     */
    private String instanceId = resolveDefaultInstanceId();

    /**
     * Setter that applies {@link #sanitizeInstanceId(String)} so that any
     * configuration-supplied value remains safe to use as a NATS durable name
     * (and as a derived consumer name on every other broker).
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = sanitizeInstanceId(instanceId);
    }

    /**
     * Per-channel configuration overrides.
     */
    private Map<String, ChannelProperties> channels = new LinkedHashMap<>();

    /**
     * Redis broker configuration.
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * NATS broker configuration.
     */
    private NatsProperties nats = new NatsProperties();

    /**
     * Publisher-side configuration shared across brokers.
     */
    private PublisherProperties publisher = new PublisherProperties();

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

        /**
         * Per-channel override for NATS JetStream {@code duplicate_window}.
         * When {@code null}, falls back to {@code simplix.messaging.nats.duplicate-window}.
         */
        private Duration duplicateWindow;

        /**
         * Per-channel override for NATS consumer {@code deliver_policy}.
         * Supported values: {@code all}, {@code new}, {@code last}, {@code last_per_subject}.
         * When {@code null} or empty, falls back to {@code simplix.messaging.nats.deliver-policy}.
         */
        private String deliverPolicy;
    }

    /**
     * Publisher-side properties applied across all broker implementations.
     */
    @Data
    public static class PublisherProperties {

        /**
         * When {@code true}, the publisher automatically assigns a fresh UUIDv4 to the
         * {@link dev.simplecore.simplix.messaging.core.MessageHeaders#MESSAGE_ID} header
         * (and, for NATS, the {@code Nats-Msg-Id} header) when the caller did not provide one.
         *
         * <p>Defaults to {@code false} to preserve historical behavior. Enable this when
         * the application's retry policy may republish the same payload — without an
         * explicit message ID per attempt, NATS publish-time deduplication can silently
         * drop a retry that the application intends to be a fresh delivery.
         */
        private boolean autoMessageId = false;
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

    /**
     * NATS broker configuration.
     */
    @Data
    public static class NatsProperties {

        private String servers = "nats://localhost:4222";
        private String username = "";
        private String password = "";
        private String token = "";
        private String credsFile = "";
        private String nkeyFile = "";
        private TlsProperties tls = new TlsProperties();
        private String connectionName = "simplix-messaging";
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration reconnectWait = Duration.ofSeconds(2);
        private int maxReconnects = -1;
        private String streamPrefix = "simplix-";
        private String subjectPrefix = "simplix.";

        /**
         * When {@code true}, {@code ensureStream} creates the stream on first use.
         * When {@code false}, the stream must already exist; missing streams cause
         * {@link IllegalStateException} so misconfiguration is detected at boot.
         */
        private boolean autoCreateStreams = true;

        /**
         * When {@code true}, {@code ensureStream} updates the stream's configuration
         * if it already exists. When {@code false}, an existing stream's settings are
         * preserved as-is — useful when an external operator (e.g., IaC) owns the
         * stream definition and the application should only verify presence.
         */
        private boolean autoUpdateStreams = true;

        private Duration ackWait;
        private Integer maxDeliver;
        private String ackPolicy = "explicit";

        /**
         * Default consumer {@code deliver_policy} when no per-channel override is set.
         * Supported values: {@code all} (default), {@code new}, {@code last},
         * {@code last_per_subject}.
         */
        private String deliverPolicy = "all";

        private String retention = "limits";
        private String storage = "file";
        private String discardPolicy = "old";
        private long maxMsgs = -1;
        private Duration maxAge = Duration.ofDays(7);
        private long maxBytes = -1;
        private Duration duplicateWindow = Duration.ofMinutes(2);
        private int replicas = 1;
        private Duration pollTimeout = Duration.ofSeconds(2);
        private int batchSize = 10;
        private Duration pendingCheckInterval = Duration.ofSeconds(30);
        private SchedulerProperties scheduler = new SchedulerProperties();

        /**
         * Resolves the ack-wait duration, falling back to 30 seconds if not explicitly set.
         *
         * @param err the error/retry configuration
         * @return the effective ack-wait duration
         */
        public Duration resolveAckWait(ErrorProperties err) {
            if (ackWait != null) return ackWait;
            // The default 30s mirrors the hardcoded maxBackoff in MessagingAutoConfiguration.retryPolicy()
            // (RetryPolicy with maxBackoff = Duration.ofSeconds(30)). If RetryPolicy ever exposes
            // maxBackoff via properties, this should be changed to read from err instead.
            return Duration.ofSeconds(30);
        }

        /**
         * Resolves max-deliver as maxRetries + 1 when not explicitly set.
         *
         * @param err the error/retry configuration
         * @return the effective max-deliver value
         */
        public int resolveMaxDeliver(ErrorProperties err) {
            if (maxDeliver != null) return maxDeliver;
            return err.getMaxRetries() + 1;
        }

        /**
         * Resolves the max-msgs limit for a channel, preferring the per-channel override,
         * then the global maxMsgs setting, then falling back to 50 000.
         *
         * @param channel the channel name
         * @param props   the top-level messaging properties
         * @return the effective max-msgs value
         */
        public long resolveMaxMsgs(String channel, MessagingProperties props) {
            ChannelProperties ch = props.getChannels().get(channel);
            if (ch != null) return ch.getMaxLength();
            if (maxMsgs >= 0) return maxMsgs;
            return 50_000L;
        }

        /**
         * Resolves the duplicate-window for a channel. The per-channel
         * {@code channels.<name>.duplicate-window} takes precedence over the global
         * {@code simplix.messaging.nats.duplicate-window}.
         *
         * @param channel the channel name
         * @param props   the top-level messaging properties
         * @return the effective duplicate window for the channel's stream
         */
        public Duration resolveDuplicateWindow(String channel, MessagingProperties props) {
            ChannelProperties ch = props.getChannels().get(channel);
            if (ch != null && ch.getDuplicateWindow() != null) {
                return ch.getDuplicateWindow();
            }
            return duplicateWindow;
        }

        /**
         * Resolves the deliver-policy string for a channel. The per-channel
         * {@code channels.<name>.deliver-policy} takes precedence over the global
         * {@code simplix.messaging.nats.deliver-policy}.
         *
         * @param channel the channel name
         * @param props   the top-level messaging properties
         * @return the effective deliver policy string ({@code all}, {@code new},
         *         {@code last}, or {@code last_per_subject})
         */
        public String resolveDeliverPolicy(String channel, MessagingProperties props) {
            ChannelProperties ch = props.getChannels().get(channel);
            if (ch != null && ch.getDeliverPolicy() != null && !ch.getDeliverPolicy().isEmpty()) {
                return ch.getDeliverPolicy();
            }
            return deliverPolicy;
        }

        /**
         * TLS configuration for the NATS connection.
         */
        @Data
        public static class TlsProperties {
            private boolean enabled = false;
            private String trustStore = "";
            private String trustStorePassword = "";
            private String keyStore = "";
            private String keyStorePassword = "";
        }

        /**
         * NATS KV-based message scheduler configuration.
         *
         * <p>The scheduler relies on a JetStream KV bucket
         * ({@code kvBucket}) and therefore requires the connected
         * NATS user to hold KV-related permissions
         * ({@code $JS.API.STREAM.INFO.KV_<bucket>} and {@code $KV.<bucket>.>}, plus
         * {@code $JS.API.STREAM.CREATE.KV_<bucket>} when the bucket is not
         * pre-provisioned). The scheduler is deprecated and gated behind
         * {@code enabled}; deployments that do not opt in never
         * touch the KV bucket and therefore do not require these grants.
         */
        @Data
        public static class SchedulerProperties {
            /**
             * Whether the NATS-backed {@code MessageScheduler} bean is registered.
             * When {@code false}, no KV bucket is created or bound at startup, and
             * applications can publish without holding KV permissions.
             *
             * <p>Default is {@code false} since 1.1.1: the scheduler is deprecated
             * and gated behind an explicit opt-in. The Spring bean registration
             * uses {@code @ConditionalOnProperty(matchIfMissing = false)} so an
             * unset property leaves the bean unregistered regardless of this
             * field's runtime value; consumers reading this field programmatically
             * therefore see the same default that the Spring gate applies.
             */
            private boolean enabled = false;

            private String kvBucket = "simplix-scheduled";
            private Duration pollInterval = Duration.ofSeconds(5);
            private Duration leaderLockTtl = Duration.ofSeconds(30);
        }
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
            return sanitizeInstanceId(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            return "simplix-" + ProcessHandle.current().pid();
        }
    }

    /**
     * Replaces characters disallowed by NATS JetStream consumer durable names
     * ({@code .}, {@code *}, {@code >}, {@code \}, {@code /}, plus any
     * non-printable ASCII) with {@code _}. Hostnames such as
     * {@code MacBookPro.local} are otherwise rejected by the broker
     * with "Durable must be in the printable ASCII range and cannot include ..."
     * the moment they are used as a consumer name.
     *
     * <p>Other broker types tolerate a wider range, but the NATS rule is the
     * strictest, so applying it as the universal default keeps a single
     * derived id safe across brokers. The same sanitization is applied to
     * any broker-bound name resolved at runtime — instance id, consumer group
     * name, etc. — so callers building durable names from environment-derived
     * values (hostnames, IPs, configuration placeholders) can route through
     * this helper without duplicating the rule.
     *
     * @param raw the raw value (possibly null or empty)
     * @return a sanitized value safe for use as a NATS durable name; for
     *         {@code null} or empty input, a process-derived fallback prefixed
     *         with {@code simplix-}
     */
    public static String sanitizeInstanceId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "simplix-" + ProcessHandle.current().pid();
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean printable = c >= 0x21 && c <= 0x7E;
            boolean reserved = c == '.' || c == '*' || c == '>' || c == '\\' || c == '/';
            sb.append(printable && !reserved ? c : '_');
        }
        return sb.toString();
    }
}
