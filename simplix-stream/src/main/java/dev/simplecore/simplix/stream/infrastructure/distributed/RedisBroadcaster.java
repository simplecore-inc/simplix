package dev.simplecore.simplix.stream.infrastructure.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

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

    private final Map<String, MessageSender> localSenders = new ConcurrentHashMap<>();
    private volatile boolean available = false;

    public RedisBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StreamProperties properties,
            String instanceId) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channelPrefix = properties.getDistributed().getPubsub().getChannelPrefix();
        this.instanceId = instanceId;
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
        try {
            // Create broadcast message wrapper
            BroadcastMessage broadcastMessage = new BroadcastMessage(
                    instanceId,
                    key.toKeyString(),
                    sessionIds,
                    message
            );

            String channel = channelPrefix + key.toKeyString();
            String messageJson = objectMapper.writeValueAsString(broadcastMessage);

            // Publish to Redis
            redisTemplate.convertAndSend(channel, messageJson);

            log.trace("Broadcast message published to Redis: channel={}, sessions={}",
                    channel, sessionIds.size());
        } catch (Exception e) {
            log.error("Failed to broadcast message via Redis: {}", key.toKeyString(), e);
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
     *
     * @param broadcastMessage the broadcast message
     */
    public void handleBroadcastMessage(BroadcastMessage broadcastMessage) {
        // Skip messages from this instance (already delivered locally)
        if (instanceId.equals(broadcastMessage.sourceInstance())) {
            return;
        }

        StreamMessage message = broadcastMessage.message();
        int delivered = 0;

        for (String sessionId : broadcastMessage.sessionIds()) {
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
