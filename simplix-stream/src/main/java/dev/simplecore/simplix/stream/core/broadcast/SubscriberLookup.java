package dev.simplecore.simplix.stream.core.broadcast;

import dev.simplecore.simplix.stream.core.model.SubscriptionKey;

import java.util.Set;

/**
 * Strategy interface for looking up subscribers by subscription key.
 * <p>
 * In distributed mode, the RedisBroadcaster uses this to find local subscribers
 * on the receiving instance when a broadcast message arrives via Redis Pub/Sub.
 * Without this, only the originating instance's session IDs are checked,
 * causing subscribers on other instances to be silently skipped.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@code EventSubscriberRegistry} - for event-based (push) resources</li>
 *   <li>{@code SchedulerSubscriberLookup} - for polling-based (scheduler) resources</li>
 * </ul>
 */
@FunctionalInterface
public interface SubscriberLookup {

    /**
     * Get all subscriber session IDs for the given subscription key on this instance.
     *
     * @param key the subscription key
     * @return unmodifiable set of session IDs (empty if none)
     */
    Set<String> getSubscribers(SubscriptionKey key);
}
