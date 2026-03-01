package dev.simplecore.simplix.stream.infrastructure.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Pub/Sub implementation of BroadcastService.
 * <p>
 * Publishes messages to Redis channels for distribution across server instances.
 * Each server subscribes to channels and delivers messages to local sessions.
 */
@Slf4j
public class RedisBroadcaster implements BroadcastService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channelPrefix;
    private final String instanceId;
    private final List<SubscriberLookup> subscriberLookups;

    private final Map<String, MessageSender> localSenders = new ConcurrentHashMap<>();
    private volatile boolean available = false;

    /**
     * @deprecated Use constructor with subscriberLookups parameter for distributed mode.
     */
    @Deprecated
    public RedisBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StreamProperties properties,
            String instanceId) {
        this(redisTemplate, objectMapper, properties, instanceId, List.of());
    }

    public RedisBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StreamProperties properties,
            String instanceId,
            List<SubscriberLookup> subscriberLookups) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channelPrefix = properties.getDistributed().getPubsub().getChannelPrefix();
        this.instanceId = instanceId;
        this.subscriberLookups = subscriberLookups != null ? subscriberLookups : List.of();
    }

    /**
     * Register a local message sender for a session.
     *
     * @param sessionId the session ID
     * @param sender    the message sender
     */
    public void registerSender(String sessionId, MessageSender sender) {
        localSenders.put(sessionId, sender);
        log.debug("Local sender registered for session: {}", sessionId);
    }

    /**
     * Unregister a local message sender.
     *
     * @param sessionId the session ID
     */
    public void unregisterSender(String sessionId) {
        localSenders.remove(sessionId);
        log.debug("Local sender unregistered for session: {}", sessionId);
    }

    @Override
    public void broadcast(SubscriptionKey key, StreamMessage message, Set<String> sessionIds) {
        // 1. Deliver to local subscribers first (immediate, no Redis round-trip)
        int localDelivered = 0;
        for (String sessionId : sessionIds) {
            MessageSender sender = localSenders.get(sessionId);
            if (sender != null && sender.isActive()) {
                if (sender.send(message)) {
                    localDelivered++;
                }
            }
        }

        // 2. Publish to Redis Pub/Sub for other instances
        try {
            BroadcastMessage broadcastMessage = new BroadcastMessage(
                    instanceId,
                    key.toKeyString(),
                    sessionIds,
                    message
            );

            String channel = channelPrefix + key.toKeyString();
            String messageJson = objectMapper.writeValueAsString(broadcastMessage);

            redisTemplate.convertAndSend(channel, messageJson);

            log.trace("Broadcast message: channel={}, localDelivered={}, sessions={}",
                    channel, localDelivered, sessionIds.size());
        } catch (Exception e) {
            log.error("Failed to publish broadcast to Redis (local delivery already done: {}): {}",
                    localDelivered, key.toKeyString(), e);
        }
    }

    @Override
    public boolean sendToSession(String sessionId, StreamMessage message) {
        // First try local delivery
        MessageSender sender = localSenders.get(sessionId);
        if (sender != null && sender.isActive()) {
            return sender.send(message);
        }

        // If not local, publish to Redis for other instances to deliver
        try {
            DirectMessage directMessage = new DirectMessage(
                    instanceId,
                    sessionId,
                    message
            );

            String channel = channelPrefix + "direct:" + sessionId;
            String messageJson = objectMapper.writeValueAsString(directMessage);

            redisTemplate.convertAndSend(channel, messageJson);
            return true;
        } catch (Exception e) {
            log.error("Failed to send direct message via Redis: {}", sessionId, e);
            return false;
        }
    }

    /**
     * Handle a broadcast message received from Redis.
     * <p>
     * When subscriber lookups are configured (distributed mode), this method resolves
     * the receiving instance's own local subscribers for the subscription key, instead
     * of relying on the originating instance's session IDs. This ensures that all
     * instances deliver messages to their locally connected SSE/WebSocket clients.
     *
     * @param broadcastMessage the broadcast message
     */
    public void handleBroadcastMessage(BroadcastMessage broadcastMessage) {
        // Skip messages from this instance (already delivered locally in broadcast())
        if (instanceId.equals(broadcastMessage.sourceInstance())) {
            return;
        }

        StreamMessage message = broadcastMessage.message();
        int delivered = 0;

        // Resolve local subscribers via SubscriberLookup (event + scheduler registries)
        Set<String> targetSessionIds = resolveLocalSubscribers(broadcastMessage);

        for (String sessionId : targetSessionIds) {
            MessageSender sender = localSenders.get(sessionId);
            if (sender != null && sender.isActive()) {
                if (sender.send(message)) {
                    delivered++;
                }
            }
        }

        if (delivered > 0) {
            log.trace("Delivered broadcast message to {} local sessions for key: {}",
                    delivered, broadcastMessage.subscriptionKey());
        }
    }

    /**
     * Resolve the target session IDs for a received broadcast message.
     * <p>
     * If subscriber lookups are available, queries this instance's own registries
     * to find local subscribers for the subscription key. Falls back to the
     * originating instance's session IDs when no lookups are configured.
     */
    private Set<String> resolveLocalSubscribers(BroadcastMessage broadcastMessage) {
        if (subscriberLookups.isEmpty()) {
            // Fallback: use session IDs from the originating instance
            return broadcastMessage.sessionIds();
        }

        SubscriptionKey key = SubscriptionKey.fromString(broadcastMessage.subscriptionKey());
        Set<String> resolved = new HashSet<>();

        for (SubscriberLookup lookup : subscriberLookups) {
            resolved.addAll(lookup.getSubscribers(key));
        }

        return resolved;
    }

    /**
     * Handle a direct message received from Redis.
     *
     * @param directMessage the direct message
     */
    public void handleDirectMessage(DirectMessage directMessage) {
        // Skip messages from this instance
        if (instanceId.equals(directMessage.sourceInstance())) {
            return;
        }

        MessageSender sender = localSenders.get(directMessage.sessionId());
        if (sender != null && sender.isActive()) {
            sender.send(directMessage.message());
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize() {
        try {
            // Test Redis connection
            redisTemplate.opsForValue().get("test");
            available = true;
            log.info("Redis broadcaster initialized (instance: {})", instanceId);
        } catch (Exception e) {
            log.error("Failed to initialize Redis broadcaster", e);
            available = false;
        }
    }

    @Override
    public void shutdown() {
        available = false;

        // Close all local senders
        localSenders.values().forEach(sender -> {
            try {
                sender.close();
            } catch (Exception e) {
                log.warn("Error closing sender: {}", e.getMessage());
            }
        });
        localSenders.clear();

        log.info("Redis broadcaster shutdown");
    }

    /**
     * Get the number of local senders.
     *
     * @return the count
     */
    public int getLocalSenderCount() {
        return localSenders.size();
    }

    /**
     * Broadcast message wrapper for Redis Pub/Sub.
     */
    public record BroadcastMessage(
            String sourceInstance,
            String subscriptionKey,
            Set<String> sessionIds,
            StreamMessage message
    ) {
    }

    /**
     * Direct message wrapper for Redis Pub/Sub.
     */
    public record DirectMessage(
            String sourceInstance,
            String sessionId,
            StreamMessage message
    ) {
    }
}
