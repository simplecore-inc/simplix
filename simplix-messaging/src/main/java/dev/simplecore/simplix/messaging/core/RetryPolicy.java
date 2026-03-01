package dev.simplecore.simplix.messaging.core;

import java.time.Duration;

/**
 * Configurable retry policy with exponential backoff.
 *
 * @param maxRetries        maximum number of retry attempts
 * @param initialBackoff    initial backoff duration
 * @param backoffMultiplier multiplier applied to the backoff on each retry
 * @param maxBackoff        upper bound for backoff duration
 */
public record RetryPolicy(
        int maxRetries,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff
) {

    /**
     * Create a default retry policy: 3 retries, 1s initial backoff, 2x multiplier, 30s max.
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));
    }

    /**
     * Calculate the backoff duration for the given attempt number (zero-based).
     *
     * @param attempt the attempt number (0 = first retry)
     * @return the backoff duration, capped at {@link #maxBackoff()}
     */
    public Duration backoffFor(int attempt) {
        long millis = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt));
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }
}
