package dev.simplecore.simplix.stream.eventsource;

import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that tracks subscribers for event-based streaming resources.
 * <p>
 * Unlike the scheduler-based approach which tracks subscribers per scheduler,
 * this registry provides a simple mapping from SubscriptionKey to subscriber session IDs.
 * This is used by EventStreamHandler to find subscribers when an event is received.
 * <p>
 * Implements {@link SubscriberLookup} so that RedisBroadcaster can query local
 * event-based subscribers when handling cross-instance broadcast messages.
 */
@Slf4j
public class EventSubscriberRegistry implements SubscriberLookup {

    // SubscriptionKey -> Set of subscriber session IDs
    private final Map<SubscriptionKey, Set<String>> subscribers = new ConcurrentHashMap<>();

    /**
     * Add a subscriber to a subscription key.
     *
     * @param key       the subscription key
     * @param sessionId the subscriber session ID
     * @return true if the subscriber was added (not already present)
     */
    public boolean addSubscriber(SubscriptionKey key, String sessionId) {
        Set<String> sessionIds = subscribers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        boolean added = sessionIds.add(sessionId);

        if (added) {
            log.debug("Event subscriber added: {} -> {} (total={})",
                    sessionId, key.toKeyString(), sessionIds.size());
        }

        return added;
    }

    /**
     * Remove a subscriber from a subscription key.
     *
     * @param key       the subscription key
     * @param sessionId the subscriber session ID
     * @return true if the subscriber was removed
     */
    public boolean removeSubscriber(SubscriptionKey key, String sessionId) {
        Set<String> sessionIds = subscribers.get(key);
        if (sessionIds == null) {
            return false;
        }

        boolean removed = sessionIds.remove(sessionId);

        if (removed) {
            log.debug("Event subscriber removed: {} <- {} (remaining={})",
                    sessionId, key.toKeyString(), sessionIds.size());

            // Clean up empty entries
            if (sessionIds.isEmpty()) {
                subscribers.remove(key);
                log.debug("Subscription key removed (no subscribers): {}", key.toKeyString());
            }
        }

        return removed;
    }

    /**
     * Remove a subscriber from all subscription keys.
     * <p>
     * Called when a session disconnects to clean up all its subscriptions.
     *
     * @param sessionId the subscriber session ID
     */
    public void removeSubscriberFromAll(String sessionId) {
        subscribers.forEach((key, sessionIds) -> {
            if (sessionIds.remove(sessionId)) {
                log.debug("Event subscriber removed from {}: {} (remaining={})",
                        key.toKeyString(), sessionId, sessionIds.size());
            }
        });

        // Clean up empty entries
        subscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Get all subscribers for a subscription key.
     *
     * @param key the subscription key
     * @return unmodifiable set of session IDs (empty if none)
     */
    public Set<String> getSubscribers(SubscriptionKey key) {
        Set<String> sessionIds = subscribers.get(key);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(sessionIds);
    }

    /**
     * Check if a subscription key has any subscribers.
     *
     * @param key the subscription key
     * @return true if has subscribers
     */
    public boolean hasSubscribers(SubscriptionKey key) {
        Set<String> sessionIds = subscribers.get(key);
        return sessionIds != null && !sessionIds.isEmpty();
    }

    /**
     * Get the subscriber count for a subscription key.
     *
     * @param key the subscription key
     * @return the subscriber count
     */
    public int getSubscriberCount(SubscriptionKey key) {
        Set<String> sessionIds = subscribers.get(key);
        return sessionIds != null ? sessionIds.size() : 0;
    }

    /**
     * Get all tracked subscription keys.
     *
     * @return unmodifiable set of subscription keys
     */
    public Set<SubscriptionKey> getSubscriptionKeys() {
        return Collections.unmodifiableSet(subscribers.keySet());
    }

    /**
     * Get the total number of tracked subscription keys.
     *
     * @return the count
     */
    public int size() {
        return subscribers.size();
    }

    /**
     * Get the total number of subscriptions (across all keys).
     *
     * @return the total subscription count
     */
    public int getTotalSubscriptionCount() {
        return subscribers.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * Clear all subscriptions.
     */
    public void clear() {
        subscribers.clear();
        log.info("Event subscriber registry cleared");
    }
}
