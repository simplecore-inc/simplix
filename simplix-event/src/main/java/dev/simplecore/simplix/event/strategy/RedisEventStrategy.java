package dev.simplecore.simplix.event.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.config.EventProperties;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis event strategy for distributed event publishing using Redis Streams
 * This strategy publishes events to Redis Streams for multi-instance scenarios with guaranteed delivery
 */
@Slf4j
public class RedisEventStrategy implements EventStrategy {

    @Setter
    private RedisTemplate<String, Object> redisTemplate;
    @Setter
    private ObjectMapper objectMapper;
    @Setter
    private EventProperties eventProperties;
    private StreamOperations<String, Object, Object> streamOps;
    private String consumerName;
    private volatile boolean ready = false;
    private final AtomicLong publishCount = new AtomicLong(0);
    private final AtomicLong trimmingFailures = new AtomicLong(0);

    // Cache for consumer groups that have been created to avoid repeated CREATE attempts
    private final Set<String> createdStreamGroups = ConcurrentHashMap.newKeySet();

    @Override
    public void publish(Event event, PublishOptions options) {
        if (!isReady()) {
            throw new IllegalStateException("Redis event strategy is not ready");
        }

        try {
            String streamKey = buildStreamKey(event, options);

            // Ensure consumer group exists for this stream (lazy creation)
            ensureConsumerGroup(streamKey);

            log.trace("Publishing event to Redis Stream {}: {}", streamKey, event.getEventId());

            // Prepare stream record
            Map<String, Object> messageBody = new HashMap<>();
            messageBody.put("eventId", event.getEventId());
            messageBody.put("eventType", event.getEventType());
            // Only add occurredAt if present (skip if null instead of storing empty string)
            if (event.getOccurredAt() != null) {
                messageBody.put("occurredAt", event.getOccurredAt().toString());
            }
            messageBody.put("aggregateId", event.getAggregateId());
            messageBody.put("payload", objectMapper.writeValueAsString(event));

            // Add headers from options (already enriched by UnifiedEventPublisher if enabled)
            if (options.getHeaders() != null && !options.getHeaders().isEmpty()) {
                messageBody.put("headers", objectMapper.writeValueAsString(options.getHeaders()));
            }

            // Create stream record
            MapRecord<String, String, Object> record = StreamRecords.newRecord()
                .in(streamKey)
                .ofMap(messageBody);

            // Add to stream
            RecordId recordId = streamOps.add(record);

            // Periodically trim the stream if enabled (every 100 publishes for performance)
            // This reduces Redis network calls while keeping stream size under control
            if (isStreamTrimmingEnabled() && publishCount.incrementAndGet() % 100 == 0) {
                CompletableFuture.runAsync(() -> {
                    try {
                        streamOps.trim(streamKey, getMaxLen(), true);
                        log.trace("Asynchronously trimmed stream {} to maxLen {}", streamKey, getMaxLen());
                    } catch (Exception e) {
                        long failureCount = trimmingFailures.incrementAndGet();
                        log.warn("Async stream trimming failed for {} (total failures: {}): {}",
                            streamKey, failureCount, e.getMessage());
                    }
                });
            }

            log.trace("Successfully published event to Redis Stream: {} with recordId: {}",
                event.getEventId(), recordId);
        } catch (Exception e) {
            handlePublishError(event, options, e);
        }
    }

    private String buildStreamKey(Event event, PublishOptions options) {
        String streamPrefix = getStreamPrefix();
        if (options.getRoutingKey() != null) {
            return String.format("%s:%s", streamPrefix, options.getRoutingKey());
        }
        return String.format("%s:%s", streamPrefix, event.getEventType());
    }

    private String getStreamPrefix() {
        return eventProperties != null && eventProperties.getRedis() != null
            ? eventProperties.getRedis().getStreamPrefix()
            : "simplix-events";
    }

    private boolean isStreamTrimmingEnabled() {
        return eventProperties != null
            && eventProperties.getRedis() != null
            && eventProperties.getRedis().getStream() != null
            && eventProperties.getRedis().getStream().getMaxLen() > 0;
    }

    private long getMaxLen() {
        if (eventProperties != null
            && eventProperties.getRedis() != null
            && eventProperties.getRedis().getStream() != null) {
            return eventProperties.getRedis().getStream().getMaxLen();
        }
        return 10000; // Default
    }

    private void handlePublishError(Event event, PublishOptions options, Exception e) {
        log.error("Failed to publish event to Redis Stream: {}", event.getEventId(), e);

        if (options.isCritical()) {
            throw new RedisStreamPublishException("Failed to publish critical event to Redis Stream", e);
        }

        // For non-critical events, log and continue
        log.warn("Non-critical event publish failed, continuing: {}", event.getEventId());
    }

    @Override
    public boolean supports(String mode) {
        return "redis".equalsIgnoreCase(mode);
    }

    @Override
    public void initialize() {
        log.info("Initializing Redis Stream Event Strategy");

        if (redisTemplate == null) {
            log.warn("RedisTemplate not available, Redis Stream strategy will be disabled");
            this.ready = false;
            return;
        }

        if (objectMapper == null) {
            log.info("Creating default ObjectMapper for Redis serialization");
            this.objectMapper = new ObjectMapper();
            this.objectMapper.findAndRegisterModules();
        }

        // Initialize stream operations
        this.streamOps = redisTemplate.opsForStream();

        // Generate consumer name if not configured
        if (eventProperties != null
            && eventProperties.getRedis() != null
            && eventProperties.getRedis().getStream() != null
            && eventProperties.getRedis().getStream().getConsumerName() != null) {
            this.consumerName = eventProperties.getRedis().getStream().getConsumerName();
        } else {
            this.consumerName = generateConsumerName();
        }

        // Test Redis connection with proper resource management
        // RedisConnection implements AutoCloseable, so try-with-resources ensures proper cleanup
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.ping();
            this.ready = true;
            log.info("Redis Stream Event Strategy initialized successfully with consumer: {}", consumerName);
            log.info("Consumer groups will be created lazily when publishing events");
        } catch (Exception e) {
            log.error("Failed to connect to Redis", e);
            this.ready = false;
        }
    }

    private String generateConsumerName() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return String.format("%s-%s", hostname, UUID.randomUUID().toString().substring(0, 8));
        } catch (Exception e) {
            log.warn("Failed to get hostname, using random consumer name", e);
            return String.format("consumer-%s", UUID.randomUUID().toString().substring(0, 8));
        }
    }

    private boolean isAutoCreateGroupEnabled() {
        return eventProperties != null
            && eventProperties.getRedis() != null
            && eventProperties.getRedis().getStream() != null
            && eventProperties.getRedis().getStream().isAutoCreateGroup();
    }

    /**
     * Creates a consumer group for a specific stream if it doesn't exist
     * This is called lazily when first publishing to a stream
     * Uses a cache to avoid repeated CREATE attempts for the same stream
     */
    private void ensureConsumerGroup(String streamKey) {
        if (!isAutoCreateGroupEnabled()) {
            return;
        }

        // Check cache first to avoid repeated CREATE attempts
        if (createdStreamGroups.contains(streamKey)) {
            return;
        }

        String groupName = eventProperties.getRedis().getStream().getConsumerGroup();

        try {
            // Try to create the consumer group
            // ReadOffset.from("0-0") means read from the beginning
            // ReadOffset.latest() means read only new messages
            ReadOffset offset = eventProperties.getRedis().getStream().isReadFromBeginning()
                ? ReadOffset.from("0-0")
                : ReadOffset.latest();

            streamOps.createGroup(streamKey, offset, groupName);
            createdStreamGroups.add(streamKey);  // Add to cache on successful creation
            log.info("Created consumer group '{}' for stream '{}'", groupName, streamKey);
        } catch (Exception e) {
            // Group might already exist, which is fine - add to cache anyway
            createdStreamGroups.add(streamKey);
            log.trace("Consumer group '{}' might already exist for stream '{}': {}",
                groupName, streamKey, e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Redis Stream Event Strategy");
        this.ready = false;
    }

    @Override
    public boolean isReady() {
        return ready && redisTemplate != null && streamOps != null;
    }

    @Override
    public String getName() {
        return "RedisStreamEventStrategy";
    }

    /**
     * Custom exception for Redis Stream publishing failures
     */
    public static class RedisStreamPublishException extends RuntimeException {
        public RedisStreamPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}