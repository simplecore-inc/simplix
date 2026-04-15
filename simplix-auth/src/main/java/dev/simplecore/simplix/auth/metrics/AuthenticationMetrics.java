package dev.simplecore.simplix.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Micrometer-based authentication metrics for token lifecycle observability.
 * <p>
 * Records counters and timers for token issuance, validation, login attempts,
 * and key rotation events. All metric names are prefixed with {@code simplix.auth.}.
 */
@Component
@ConditionalOnBean(MeterRegistry.class)
public class AuthenticationMetrics {

    private static final String TOKEN_ISSUED = "simplix.auth.token.issued";
    private static final String TOKEN_VALIDATION = "simplix.auth.token.validation";
    private static final String TOKEN_VALIDATION_FAILURE = "simplix.auth.token.validation.failure";
    private static final String LOGIN_ATTEMPTS = "simplix.auth.login.attempts";
    private static final String KEY_ROTATION = "simplix.auth.key.rotation";

    private final MeterRegistry registry;

    public AuthenticationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record a token issuance event.
     *
     * @param tokenType the token type — "access" or "refresh"
     */
    public void recordTokenIssued(String tokenType) {
        Counter.builder(TOKEN_ISSUED)
                .tag("type", tokenType)
                .register(registry)
                .increment();
    }

    /**
     * Start a timer sample for token validation duration measurement.
     *
     * @return a started Timer.Sample
     */
    public Timer.Sample startValidation() {
        return Timer.start(registry);
    }

    /**
     * Record a successful token validation with its duration.
     *
     * @param sample the timer sample started via {@link #startValidation()}
     */
    public void recordValidationSuccess(Timer.Sample sample) {
        sample.stop(Timer.builder(TOKEN_VALIDATION)
                .register(registry));
    }

    /**
     * Record a token validation failure.
     *
     * @param reason the failure reason — e.g. "expired", "revoked", "malformed", "type_mismatch", "blacklist_error"
     */
    public void recordValidationFailure(String reason) {
        Counter.builder(TOKEN_VALIDATION_FAILURE)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Record a login attempt outcome.
     *
     * @param outcome "success" or "failure"
     */
    public void recordLoginAttempt(String outcome) {
        Counter.builder(LOGIN_ATTEMPTS)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    /**
     * Record a key rotation event.
     */
    public void recordKeyRotation() {
        Counter.builder(KEY_ROTATION)
                .register(registry)
                .increment();
    }
}
