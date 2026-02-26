package dev.simplecore.simplix.stream.core.broadcast;

import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;

import java.util.Set;

/**
 * Service for broadcasting messages to subscribers.
 * <p>
 * Implementations provide Local (direct session access) or Distributed (Redis Pub/Sub)
 * message delivery.
 */
public interface BroadcastService {

    /**
     * Broadcast a message to subscribers.
     *
     * @param key        the subscription key
     * @param message    the message to broadcast
     * @param sessionIds the subscriber session IDs
     */
    void broadcast(SubscriptionKey key, StreamMessage message, Set<String> sessionIds);

    /**
     * Send a message to a specific session.
     *
     * @param sessionId the session ID
     * @param message   the message to send
     * @return true if sent successfully
     */
    boolean sendToSession(String sessionId, StreamMessage message);

    /**
     * Check if the service is available.
     *
     * @return true if available
     */
    boolean isAvailable();

    /**
     * Initialize the service.
     */
    void initialize();

    /**
     * Shutdown the service.
     */
    void shutdown();
}
