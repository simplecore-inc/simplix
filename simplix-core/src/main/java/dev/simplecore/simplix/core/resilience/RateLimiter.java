package dev.simplecore.simplix.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter that limits operations per second.
 *
 * <p>Tokens replenish at a fixed rate and each operation consumes one token.
 * When the bucket is empty, operations are rejected until tokens are replenished.
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int maxPerSecond;
    private final long refillIntervalNanos;
    private final AtomicLong availableTokens;
    private final AtomicLong lastRefillNanos;

    /**
     * Create a new rate limiter.
     *
     * @param maxPerSecond maximum number of operations allowed per second
     */
    public RateLimiter(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
        this.refillIntervalNanos = 1_000_000_000L;
        this.availableTokens = new AtomicLong(maxPerSecond);
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Try to acquire a permit for one operation.
     *
     * @return true if the operation is allowed, false if rate-limited
     */
    public boolean tryAcquire() {
        refill();
        long current = availableTokens.get();
        while (current > 0) {
            if (availableTokens.compareAndSet(current, current - 1)) {
                return true;
            }
            current = availableTokens.get();
        }
        log.trace("Rate limited: no tokens available (max={}/s)", maxPerSecond);
        return false;
    }

    /**
     * Get the number of currently available tokens.
     *
     * @return available token count
     */
    public long getAvailableTokens() {
        refill();
        return availableTokens.get();
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        long elapsed = now - last;
        if (elapsed >= refillIntervalNanos) {
            if (lastRefillNanos.compareAndSet(last, now)) {
                long tokensToAdd = (elapsed / refillIntervalNanos) * maxPerSecond;
                availableTokens.updateAndGet(current ->
                        Math.min(maxPerSecond, current + tokensToAdd));
            }
        }
    }
}
