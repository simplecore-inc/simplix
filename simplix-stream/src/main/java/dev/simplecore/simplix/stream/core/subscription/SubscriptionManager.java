package dev.simplecore.simplix.stream.core.subscription;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.Subscription;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.exception.SubscriptionLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Manages subscriptions for stream sessions.
 * <p>
 * Coordinates subscription changes, calculates diffs, and notifies
 * the scheduler manager about additions and removals.
 */
@Slf4j
@RequiredArgsConstructor
public class SubscriptionManager {

    private final SessionManager sessionManager;
    private final StreamProperties properties;

    // Subscription details by key (for interval tracking)
    private final ConcurrentHashMap<SubscriptionKey, Subscription> subscriptionDetails = new ConcurrentHashMap<>();

    // Per-session locks for atomic subscription updates
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    // Callbacks for subscription events (volatile for thread visibility)
    private volatile BiConsumer<SubscriptionKey, String> onSubscriptionAdded;
    private volatile BiConsumer<SubscriptionKey, String> onSubscriptionRemoved;

    /**
     * Update subscriptions for a session.
     * <p>
     * Calculates the diff between current and requested subscriptions,
     * applies changes, and notifies scheduler manager.
     *
     * @param sessionId     the session ID
     * @param subscriptions the new subscription list
     * @return the result containing active, denied, and invalid subscriptions
     */
    public SubscriptionUpdateResult updateSubscriptions(String sessionId, List<Subscription> subscriptions) {
        StreamSession session = sessionManager.getSession(sessionId);

        // Check subscription limit
        int maxPerSession = properties.getSubscription().getMaxPerSession();
        if (maxPerSession > 0 && subscriptions.size() > maxPerSession) {
            throw new SubscriptionLimitExceededException(subscriptions.size(), maxPerSession);
        }

        // Get or create session lock for atomic operations
        Object sessionLock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());

        synchronized (sessionLock) {
            // Get current subscriptions
            Set<SubscriptionKey> currentKeys = session.getSubscriptions();

            // Extract requested keys
            Set<SubscriptionKey> requestedKeys = subscriptions.stream()
                    .map(Subscription::getKey)
                    .collect(Collectors.toSet());

            // Calculate diff
            SubscriptionDiff diff = SubscriptionDiff.calculate(currentKeys, requestedKeys);

            log.debug("Subscription update for session {}: added={}, removed={}, unchanged={}",
                    sessionId, diff.added().size(), diff.removed().size(), diff.unchanged().size());

            // Process removals
            for (SubscriptionKey key : diff.removed()) {
                session.removeSubscription(key);
                subscriptionDetails.remove(key);

                if (onSubscriptionRemoved != null) {
                    onSubscriptionRemoved.accept(key, sessionId);
                }

                log.debug("Subscription removed: {} from session {}", key.toKeyString(), sessionId);
            }

            // Process additions
            Map<SubscriptionKey, Subscription> subscriptionMap = subscriptions.stream()
                    .collect(Collectors.toMap(Subscription::getKey, s -> s, (existing, duplicate) -> existing));

            for (SubscriptionKey key : diff.added()) {
                Subscription subscription = subscriptionMap.get(key);
                session.addSubscription(key);
                subscriptionDetails.put(key, subscription);

                if (onSubscriptionAdded != null) {
                    onSubscriptionAdded.accept(key, sessionId);
                }

                log.debug("Subscription added: {} to session {}", key.toKeyString(), sessionId);
            }

            // Build result
            List<SubscriptionInfo> active = subscriptions.stream()
                    .map(s -> new SubscriptionInfo(
                            s.getKey().toKeyString(),
                            s.getKey().getResource(),
                            s.getKey().getParams(),
                            getActualInterval(s.getKey(), s.getInterval()).toMillis()
                    ))
                    .collect(Collectors.toList());

            return new SubscriptionUpdateResult(active, List.of(), List.of());
        }
    }

    /**
     * Remove all subscriptions for a session.
     *
     * @param sessionId the session ID
     */
    public void clearSubscriptions(String sessionId) {
        sessionManager.findSession(sessionId).ifPresent(session -> {
            // Get or create session lock for atomic operations
            Object sessionLock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());

            synchronized (sessionLock) {
                Set<SubscriptionKey> keys = session.clearSubscriptions();

                for (SubscriptionKey key : keys) {
                    subscriptionDetails.remove(key);

                    if (onSubscriptionRemoved != null) {
                        onSubscriptionRemoved.accept(key, sessionId);
                    }
                }

                log.debug("Cleared {} subscriptions for session {}", keys.size(), sessionId);

                // Clean up session lock inside synchronized block
                sessionLocks.remove(sessionId, sessionLock);
            }
        });
    }

    /**
     * Get the actual interval for a subscription.
     * <p>
     * The first subscriber's interval is used; subsequent subscribers
     * receive the same interval.
     *
     * @param key              the subscription key
     * @param requestedInterval the requested interval
     * @return the actual interval
     */
    public Duration getActualInterval(SubscriptionKey key, Duration requestedInterval) {
        Subscription existing = subscriptionDetails.get(key);
        if (existing != null) {
            return existing.getInterval();
        }
        return clampInterval(requestedInterval);
    }

    /**
     * Clamp interval to configured bounds.
     *
     * @param interval the requested interval
     * @return the clamped interval
     */
    private Duration clampInterval(Duration interval) {
        Duration min = properties.getScheduler().getMinInterval();
        Duration max = properties.getScheduler().getMaxInterval();

        if (interval.compareTo(min) < 0) {
            return min;
        }
        if (interval.compareTo(max) > 0) {
            return max;
        }
        return interval;
    }

    /**
     * Set callback for subscription added events.
     *
     * @param callback the callback (key, sessionId)
     */
    public void setOnSubscriptionAdded(BiConsumer<SubscriptionKey, String> callback) {
        this.onSubscriptionAdded = callback;
    }

    /**
     * Set callback for subscription removed events.
     *
     * @param callback the callback (key, sessionId)
     */
    public void setOnSubscriptionRemoved(BiConsumer<SubscriptionKey, String> callback) {
        this.onSubscriptionRemoved = callback;
    }

    /**
     * Result of a subscription update operation.
     */
    public record SubscriptionUpdateResult(
            List<SubscriptionInfo> active,
            List<SubscriptionDenied> denied,
            List<SubscriptionInvalid> invalid
    ) {
    }

    /**
     * Information about an active subscription.
     */
    public record SubscriptionInfo(
            String key,
            String resource,
            Map<String, Object> params,
            long intervalMs
    ) {
    }

    /**
     * Information about a denied subscription.
     */
    public record SubscriptionDenied(
            String resource,
            Map<String, Object> params,
            String reason
    ) {
    }

    /**
     * Information about an invalid subscription.
     */
    public record SubscriptionInvalid(
            String resource,
            Map<String, Object> params,
            String reason
    ) {
    }
}
