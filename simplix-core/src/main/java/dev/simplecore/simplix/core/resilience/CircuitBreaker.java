package dev.simplecore.simplix.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key circuit breaker that suspends operations after consecutive failures.
 *
 * <p>States follow the standard circuit breaker pattern:
 * <ul>
 *   <li><b>CLOSED</b> — Normal operation, requests allowed.</li>
 *   <li><b>OPEN</b> — Requests suspended after reaching the failure threshold.</li>
 *   <li><b>HALF_OPEN</b> — After the timeout expires, a single probe request is allowed.
 *       Success closes the circuit; failure reopens it.</li>
 * </ul>
 *
 * <p>All state transitions are synchronized per-key to ensure atomicity
 * under concurrent access.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final int failureThreshold;
    private final long halfOpenTimeoutMs;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public CircuitBreaker(int failureThreshold, long halfOpenTimeoutMs) {
        this.failureThreshold = failureThreshold;
        this.halfOpenTimeoutMs = halfOpenTimeoutMs;
    }

    /**
     * Check whether a request is allowed for the given key.
     *
     * @param key the resource key to check
     * @return true if the request should proceed
     */
    public boolean allowRequest(String key) {
        CircuitState state = circuits.get(key);
        if (state == null) {
            return true;
        }

        synchronized (state) {
            return switch (state.status) {
                case CLOSED -> true;
                case OPEN -> {
                    if (Instant.now().toEpochMilli() - state.openedAt >= halfOpenTimeoutMs) {
                        state.status = Status.HALF_OPEN;
                        log.debug("Circuit half-open for key={}", key);
                        yield true;
                    }
                    yield false;
                }
                case HALF_OPEN -> false;
            };
        }
    }

    /**
     * Record a successful operation for the given key.
     * Resets failure count and closes the circuit.
     *
     * @param key the resource key that succeeded
     */
    public void recordSuccess(String key) {
        CircuitState state = circuits.get(key);
        if (state == null) {
            return;
        }

        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                log.info("Circuit closed after successful probe for key={}", key);
            }
            state.consecutiveFailures = 0;
            state.status = Status.CLOSED;
        }
    }

    /**
     * Record a failed operation for the given key.
     * Opens the circuit if the failure threshold is reached.
     *
     * @param key the resource key that failed
     */
    public void recordFailure(String key) {
        CircuitState state = circuits.computeIfAbsent(key, k -> new CircuitState());

        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                state.status = Status.OPEN;
                state.openedAt = Instant.now().toEpochMilli();
                state.consecutiveFailures = 0;
                log.warn("Circuit reopened after failed probe for key={}", key);
                return;
            }

            state.consecutiveFailures++;
            if (state.consecutiveFailures >= failureThreshold) {
                state.status = Status.OPEN;
                state.openedAt = Instant.now().toEpochMilli();
                log.warn("Circuit opened for key={} after {} consecutive failures",
                        key, state.consecutiveFailures);
            }
        }
    }

    /**
     * Get the current circuit status for a key.
     *
     * <p>Note: This method returns the stored status and does NOT trigger
     * the OPEN to HALF_OPEN timeout transition. Use {@link #allowRequest}
     * for actual gate-keeping decisions.
     *
     * @param key the resource key to check
     * @return the circuit status, or CLOSED if no state exists
     */
    public Status getStatus(String key) {
        CircuitState state = circuits.get(key);
        if (state == null) {
            return Status.CLOSED;
        }
        synchronized (state) {
            return state.status;
        }
    }

    /**
     * Reset the circuit breaker for a specific key.
     *
     * @param key the resource key to reset
     */
    public void reset(String key) {
        CircuitState state = circuits.get(key);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.consecutiveFailures = 0;
            state.status = Status.CLOSED;
        }
    }

    public enum Status {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static class CircuitState {
        Status status = Status.CLOSED;
        int consecutiveFailures = 0;
        long openedAt = 0;
    }
}
