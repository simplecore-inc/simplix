package dev.simplecore.simplix.event.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis event strategy for distributed event publishing
 * This strategy publishes events to Redis Pub/Sub for multi-instance scenarios
 */
@Component
@ConditionalOnClass(RedisTemplate.class)
@Slf4j
public class RedisEventStrategy implements EventStrategy {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Value("${simplix.events.redis.channel-prefix:simplix-events}")
    private String channelPrefix;

    @Value("${simplix.events.redis.default-ttl:86400}")
    private long defaultTtlSeconds;

    private volatile boolean ready = false;

    @Override
    public void publish(Event event, PublishOptions options) {
        if (!isReady()) {
            throw new IllegalStateException("Redis event strategy is not ready");
        }

        try {
            String channel = buildChannelName(event, options);
            log.debug("Publishing event to Redis channel {}: {}", channel, event.getEventId());

            // Serialize event
            String eventData = objectMapper.writeValueAsString(event);

            // Publish to Redis Pub/Sub
            redisTemplate.convertAndSend(channel, eventData);

            // If persistent, also store in Redis with TTL
            if (options.isPersistent()) {
                storeEvent(event, options);
            }

            log.trace("Successfully published event to Redis: {}", event.getEventId());
        } catch (Exception e) {
            handlePublishError(event, options, e);
        }
    }

    private String buildChannelName(Event event, PublishOptions options) {
        if (options.getRoutingKey() != null) {
            return String.format("%s:%s", channelPrefix, options.getRoutingKey());
        }
        return String.format("%s:%s", channelPrefix, event.getEventType());
    }

    private void storeEvent(Event event, PublishOptions options) {
        try {
            String key = String.format("%s:stored:%s", channelPrefix, event.getEventId());
            Duration ttl = options.getTtl() != null ? options.getTtl() : Duration.ofSeconds(defaultTtlSeconds);

            redisTemplate.opsForValue().set(key, event, ttl.toSeconds(), TimeUnit.SECONDS);
            log.trace("Stored persistent event in Redis: {}", key);
        } catch (Exception e) {
            log.warn("Failed to store persistent event: {}", event.getEventId(), e);
        }
    }

    private void handlePublishError(Event event, PublishOptions options, Exception e) {
        log.error("Failed to publish event to Redis: {}", event.getEventId(), e);

        if (options.isCritical()) {
            // For critical events, we might want to fall back to local publishing
            // or store in a database outbox table
            throw new RedisPublishException("Failed to publish critical event to Redis", e);
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
        log.info("Initializing Redis Event Strategy");

        if (redisTemplate == null) {
            log.warn("RedisTemplate not available, Redis strategy will be disabled");
            this.ready = false;
            return;
        }

        if (objectMapper == null) {
            log.info("Creating default ObjectMapper for Redis serialization");
            this.objectMapper = new ObjectMapper();
            this.objectMapper.findAndRegisterModules();
        }

        // Test Redis connection
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            this.ready = true;
            log.info("Redis Event Strategy initialized successfully");
        } catch (Exception e) {
            log.error("Failed to connect to Redis", e);
            this.ready = false;
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Redis Event Strategy");
        this.ready = false;
    }

    @Override
    public boolean isReady() {
        return ready && redisTemplate != null;
    }

    @Override
    public String getName() {
        return "RedisEventStrategy";
    }

    /**
     * Custom exception for Redis publishing failures
     */
    public static class RedisPublishException extends RuntimeException {
        public RedisPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}