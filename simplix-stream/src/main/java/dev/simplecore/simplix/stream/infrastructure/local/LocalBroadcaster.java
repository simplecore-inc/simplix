package dev.simplecore.simplix.stream.infrastructure.local;

import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local implementation of BroadcastService.
 * <p>
 * Sends messages directly to sessions via registered MessageSenders.
 * Suitable for single-server deployments.
 */
@Slf4j
public class LocalBroadcaster implements BroadcastService {

    private final Map<String, MessageSender> senders = new ConcurrentHashMap<>();
    private volatile boolean available = false;

    /**
     * Register a message sender for a session.
     *
     * @param sessionId the session ID
     * @param sender    the message sender
     */
    public void registerSender(String sessionId, MessageSender sender) {
        senders.put(sessionId, sender);
        log.debug("Sender registered for session: {}", sessionId);
    }

    /**
     * Unregister a message sender.
     *
     * @param sessionId the session ID
     */
    public void unregisterSender(String sessionId) {
        MessageSender removed = senders.remove(sessionId);
        if (removed != null) {
            log.debug("Sender unregistered for session: {}", sessionId);
        }
    }

    @Override
    public void broadcast(SubscriptionKey key, StreamMessage message, Set<String> sessionIds) {
        int successCount = 0;
        int failCount = 0;

        for (String sessionId : sessionIds) {
            if (sendToSession(sessionId, message)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        if (failCount > 0) {
            log.debug("Broadcast complete for {}: success={}, failed={}",
                    key.toKeyString(), successCount, failCount);
        }
    }

    @Override
    public boolean sendToSession(String sessionId, StreamMessage message) {
        MessageSender sender = senders.get(sessionId);

        if (sender == null) {
            log.trace("No sender found for session: {}", sessionId);
            return false;
        }

        if (!sender.isActive()) {
            log.debug("Sender not active for session: {}", sessionId);
            senders.remove(sessionId);
            return false;
        }

        try {
            return sender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize() {
        available = true;
        log.info("Local broadcaster initialized");
    }

    @Override
    public void shutdown() {
        available = false;

        // Close all senders
        senders.values().forEach(sender -> {
            try {
                sender.close();
            } catch (Exception e) {
                log.warn("Error closing sender: {}", e.getMessage());
            }
        });
        senders.clear();

        log.info("Local broadcaster shutdown");
    }

    /**
     * Get the number of registered senders.
     *
     * @return the count
     */
    public int getSenderCount() {
        return senders.size();
    }
}
