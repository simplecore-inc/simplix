package dev.simplecore.simplix.stream.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a subscription request from a client.
 */
@Getter
@Builder
@ToString
public class Subscription {

    /**
     * Unique subscription identifier
     */
    private final SubscriptionKey key;

    /**
     * Desired push interval
     */
    private final Duration interval;

    /**
     * When the subscription was requested
     */
    private final Instant requestedAt;

    /**
     * Create a new subscription with current timestamp.
     *
     * @param key      the subscription key
     * @param interval the desired interval
     * @return the subscription
     */
    public static Subscription of(SubscriptionKey key, Duration interval) {
        return Subscription.builder()
                .key(key)
                .interval(interval)
                .requestedAt(Instant.now())
                .build();
    }

    /**
     * Get the resource name from the key.
     *
     * @return the resource name
     */
    public String getResource() {
        return key.getResource();
    }

    /**
     * Get the interval in milliseconds.
     *
     * @return the interval in milliseconds
     */
    public long getIntervalMs() {
        return interval.toMillis();
    }
}
