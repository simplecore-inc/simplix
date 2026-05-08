package dev.simplecore.simplix.auth.health;

/**
 * Optional store-specific health probe for the token blacklist.
 *
 * <p>Implementations bypass {@code TokenBlacklistService}'s failure-mode
 * catch (which may silently swallow store outages) and surface the raw
 * connectivity error so {@link BlacklistHealthIndicator} can report DOWN
 * accurately.</p>
 *
 * <p>Each implementation is gated by a string-based
 * {@code @ConditionalOnClass} so the auth module remains free of
 * compile-time coupling to any specific store client (Redis, NATS, ...).
 * When no probe is registered, {@code BlacklistHealthIndicator} falls
 * back to probing through the {@code TokenBlacklistService} interface.</p>
 */
public interface BlacklistProbe {

    /**
     * Performs a lightweight liveness check against the underlying store.
     * Implementations must throw a runtime exception when the store is
     * unreachable; a normal return is treated as UP.
     */
    void probe();
}
