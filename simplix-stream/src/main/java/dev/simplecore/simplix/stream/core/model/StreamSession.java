package dev.simplecore.simplix.stream.core.model;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a client stream session.
 * <p>
 * Manages session lifecycle, subscriptions, and message delivery.
 */
@Getter
@ToString(exclude = {"subscriptions", "metadata", "transientMetadata"})
public class StreamSession {

    private final String id;
    private final String userId;
    private final TransportType transportType;
    private final Instant connectedAt;
    private final Map<String, Object> metadata;
    private final Set<SubscriptionKey> subscriptions;
    private final Map<String, Object> transientMetadata = new ConcurrentHashMap<>();

    private volatile SessionState state;
    private volatile Instant lastActiveAt;
    private volatile Instant disconnectedAt;

    @Builder
    private StreamSession(String id, String userId, TransportType transportType,
                          Map<String, Object> metadata) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.userId = userId;
        this.transportType = transportType;
        this.connectedAt = Instant.now();
        this.lastActiveAt = Instant.now();
        this.state = SessionState.CONNECTED;
        this.metadata = metadata != null
                ? new ConcurrentHashMap<>(metadata)
                : new ConcurrentHashMap<>();
        this.subscriptions = ConcurrentHashMap.newKeySet();
    }

    /**
     * Create a new session for the given user.
     *
     * @param userId        the user ID
     * @param transportType the transport type
     * @return the new session
     */
    public static StreamSession create(String userId, TransportType transportType) {
        return StreamSession.builder()
                .userId(userId)
                .transportType(transportType)
                .build();
    }

    /**
     * Update the last active timestamp.
     */
    public void touch() {
        this.lastActiveAt = Instant.now();
    }

    /**
     * Mark the session as disconnected.
     */
    public void markDisconnected() {
        if (this.state == SessionState.CONNECTED) {
            this.state = SessionState.DISCONNECTED;
            this.disconnectedAt = Instant.now();
        }
    }

    /**
     * Mark the session as reconnected.
     */
    public void markReconnected() {
        if (this.state == SessionState.DISCONNECTED) {
            this.state = SessionState.CONNECTED;
            this.disconnectedAt = null;
            this.lastActiveAt = Instant.now();
        }
    }

    /**
     * Mark the session as terminated.
     */
    public void markTerminated() {
        this.state = SessionState.TERMINATED;
    }

    /**
     * Check if the session is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return this.state == SessionState.CONNECTED;
    }

    /**
     * Check if the session is terminated.
     *
     * @return true if terminated
     */
    public boolean isTerminated() {
        return this.state == SessionState.TERMINATED;
    }

    /**
     * Add a subscription to this session.
     *
     * @param key the subscription key
     * @return true if added (not already present)
     */
    public boolean addSubscription(SubscriptionKey key) {
        return subscriptions.add(key);
    }

    /**
     * Remove a subscription from this session.
     *
     * @param key the subscription key
     * @return true if removed
     */
    public boolean removeSubscription(SubscriptionKey key) {
        return subscriptions.remove(key);
    }

    /**
     * Clear all subscriptions.
     *
     * @return the removed subscription keys
     */
    public Set<SubscriptionKey> clearSubscriptions() {
        Set<SubscriptionKey> removed = Set.copyOf(subscriptions);
        subscriptions.clear();
        return removed;
    }

    /**
     * Get the current subscriptions.
     *
     * @return unmodifiable set of subscription keys
     */
    public Set<SubscriptionKey> getSubscriptions() {
        return Set.copyOf(subscriptions);
    }

    /**
     * Get the subscription count.
     *
     * @return the number of subscriptions
     */
    public int getSubscriptionCount() {
        return subscriptions.size();
    }

    /**
     * Check if the session has a specific subscription.
     *
     * @param key the subscription key
     * @return true if subscribed
     */
    public boolean hasSubscription(SubscriptionKey key) {
        return subscriptions.contains(key);
    }

    /**
     * Add metadata to the session.
     *
     * @param key   the metadata key
     * @param value the metadata value
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Get metadata from the session.
     *
     * @param key the metadata key
     * @return the metadata value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    /**
     * Add transient metadata to the session.
     * <p>
     * Transient metadata is NOT persisted to the database and is only
     * available for the lifetime of this in-memory session object.
     *
     * @param key   the metadata key
     * @param value the metadata value
     */
    public void addTransientMetadata(String key, Object value) {
        transientMetadata.put(key, value);
    }

    /**
     * Get transient metadata from the session.
     *
     * @param key the metadata key
     * @return the metadata value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getTransientMetadata(String key) {
        return (T) transientMetadata.get(key);
    }

    /**
     * Get all transient metadata as an unmodifiable view.
     *
     * @return unmodifiable map of transient metadata
     */
    public Map<String, Object> getTransientMetadata() {
        return Collections.unmodifiableMap(transientMetadata);
    }
}
