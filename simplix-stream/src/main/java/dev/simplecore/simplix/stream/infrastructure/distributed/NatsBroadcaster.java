package dev.simplecore.simplix.stream.infrastructure.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NATS core pub/sub implementation of {@link BroadcastService}.
 *
 * <p>Functionally equivalent to {@code RedisBroadcaster}: every instance
 * publishes broadcast/direct messages to fixed NATS subjects derived from
 * {@code subjectPrefix}, and every other
 * instance receives the messages and resolves its own local subscribers via
 * {@link SubscriberLookup}. Self-messages are filtered out by comparing the
 * {@code sourceInstance} field.
 *
 * <p>Two subjects are used:
 * <ul>
 *   <li>{@code <subjectPrefix><broadcastSegment>} — keyed broadcast messages</li>
 *   <li>{@code <subjectPrefix><directSegment>} — direct messages with target session ID in the body</li>
 * </ul>
 */
@Slf4j
public class NatsBroadcaster implements BroadcastService {

    private final Connection connection;
    private final ObjectMapper objectMapper;
    private final String broadcastSubject;
    private final String directSubject;
    private final String instanceId;
    private final List<SubscriberLookup> subscriberLookups;

    private final Map<String, MessageSender> localSenders = new ConcurrentHashMap<>();
    private volatile boolean available = false;
    private Dispatcher dispatcher;

    public NatsBroadcaster(
            Connection connection,
            ObjectMapper objectMapper,
            StreamProperties properties,
            String instanceId,
            List<SubscriberLookup> subscriberLookups) {
        this.connection = connection;
        this.objectMapper = objectMapper;
        StreamProperties.NatsConfig natsConfig = properties.getDistributed().getNats();
        this.broadcastSubject = natsConfig.getSubjectPrefix() + natsConfig.getBroadcastSubjectSegment();
        this.directSubject = natsConfig.getSubjectPrefix() + natsConfig.getDirectSubjectSegment();
        this.instanceId = instanceId;
        this.subscriberLookups = subscriberLookups != null ? subscriberLookups : List.of();
    }

    public void registerSender(String sessionId, MessageSender sender) {
        localSenders.put(sessionId, sender);
        log.debug("Local sender registered for session: {}", sessionId);
    }

    public void unregisterSender(String sessionId) {
        localSenders.remove(sessionId);
        log.debug("Local sender unregistered for session: {}", sessionId);
    }

    @Override
    public void broadcast(SubscriptionKey key, StreamMessage message, Set<String> sessionIds) {
        int localDelivered = 0;
        for (String sessionId : sessionIds) {
            MessageSender sender = localSenders.get(sessionId);
            if (sender != null && sender.isActive() && sender.send(message)) {
                localDelivered++;
            }
        }

        try {
            BroadcastMessage payload = new BroadcastMessage(
                    instanceId,
                    key.toKeyString(),
                    sessionIds,
                    message);
            connection.publish(broadcastSubject,
                    objectMapper.writeValueAsBytes(payload));

            log.trace("Broadcast message: subject={}, localDelivered={}, sessions={}",
                    broadcastSubject, localDelivered, sessionIds.size());
        } catch (Exception e) {
            log.error("Failed to publish broadcast to NATS (local delivery already done: {}): {}",
                    localDelivered, key.toKeyString(), e);
        }
    }

    @Override
    public boolean sendToSession(String sessionId, StreamMessage message) {
        MessageSender sender = localSenders.get(sessionId);
        if (sender != null && sender.isActive()) {
            return sender.send(message);
        }

        try {
            DirectMessage directMessage = new DirectMessage(instanceId, sessionId, message);
            connection.publish(directSubject, objectMapper.writeValueAsBytes(directMessage));
            return true;
        } catch (Exception e) {
            log.error("Failed to send direct message via NATS: {}", sessionId, e);
            return false;
        }
    }

    void handleBroadcastMessage(BroadcastMessage broadcastMessage) {
        if (instanceId.equals(broadcastMessage.sourceInstance())) {
            return;
        }

        StreamMessage message = broadcastMessage.message();
        Set<String> targetSessionIds = resolveLocalSubscribers(broadcastMessage);
        int delivered = 0;

        for (String sessionId : targetSessionIds) {
            MessageSender sender = localSenders.get(sessionId);
            if (sender != null && sender.isActive() && sender.send(message)) {
                delivered++;
            }
        }

        if (delivered > 0) {
            log.trace("Delivered broadcast message to {} local sessions for key: {}",
                    delivered, broadcastMessage.subscriptionKey());
        }
    }

    void handleDirectMessage(DirectMessage directMessage) {
        if (instanceId.equals(directMessage.sourceInstance())) {
            return;
        }
        MessageSender sender = localSenders.get(directMessage.sessionId());
        if (sender != null && sender.isActive()) {
            sender.send(directMessage.message());
        }
    }

    private Set<String> resolveLocalSubscribers(BroadcastMessage broadcastMessage) {
        if (subscriberLookups.isEmpty()) {
            return broadcastMessage.sessionIds();
        }
        SubscriptionKey key = SubscriptionKey.fromString(broadcastMessage.subscriptionKey());
        Set<String> resolved = new HashSet<>();
        for (SubscriberLookup lookup : subscriberLookups) {
            resolved.addAll(lookup.getSubscribers(key));
        }
        return resolved;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize() {
        try {
            this.dispatcher = connection.createDispatcher();
            dispatcher.subscribe(broadcastSubject, msg -> {
                try {
                    BroadcastMessage payload = objectMapper.readValue(
                            msg.getData(), BroadcastMessage.class);
                    handleBroadcastMessage(payload);
                } catch (Exception e) {
                    log.error("Error handling NATS broadcast message: {}", e.getMessage());
                }
            });
            dispatcher.subscribe(directSubject, msg -> {
                try {
                    DirectMessage payload = objectMapper.readValue(
                            msg.getData(), DirectMessage.class);
                    handleDirectMessage(payload);
                } catch (Exception e) {
                    log.error("Error handling NATS direct message: {}", e.getMessage());
                }
            });
            available = true;
            log.info("NATS broadcaster initialized [instance={}, broadcastSubject={}, directSubject={}]",
                    instanceId, broadcastSubject, directSubject);
        } catch (Exception e) {
            log.error("Failed to initialize NATS broadcaster", e);
            available = false;
        }
    }

    @Override
    public void shutdown() {
        available = false;
        if (dispatcher != null) {
            try {
                connection.closeDispatcher(dispatcher);
            } catch (Exception e) {
                log.warn("Error closing NATS dispatcher: {}", e.getMessage());
            }
        }
        localSenders.values().forEach(sender -> {
            try {
                sender.close();
            } catch (Exception e) {
                log.warn("Error closing sender: {}", e.getMessage());
            }
        });
        localSenders.clear();
        log.info("NATS broadcaster shutdown");
    }

    public int getLocalSenderCount() {
        return localSenders.size();
    }

    /** Broadcast message wrapper for NATS Pub/Sub. */
    public record BroadcastMessage(
            String sourceInstance,
            String subscriptionKey,
            Set<String> sessionIds,
            StreamMessage message
    ) {
    }

    /** Direct message wrapper for NATS Pub/Sub. */
    public record DirectMessage(
            String sourceInstance,
            String sessionId,
            StreamMessage message
    ) {
    }
}
