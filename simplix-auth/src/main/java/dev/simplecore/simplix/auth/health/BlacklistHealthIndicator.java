package dev.simplecore.simplix.auth.health;

import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for the token blacklist service.
 * Reports the blacklist implementation type and availability status.
 *
 * <p>Endpoint: {@code /actuator/health} includes a {@code blacklist}
 * component.</p>
 *
 * <p>When a store-specific {@link BlacklistProbe} is registered (e.g.,
 * {@link RedisBlacklistProbe}), this indicator delegates to it for direct
 * connectivity checks that bypass failure-mode catches in the service
 * layer. Otherwise it falls back to a service-level probe via
 * {@link TokenBlacklistService#isBlacklisted(String)}, which works for
 * every implementation including NATS, Caffeine, and InMemory.</p>
 *
 * <p>The indicator itself has no compile-time dependency on Redis (or any
 * other store client), so it loads cleanly regardless of which optional
 * starters are present on the classpath.</p>
 */
@Component("blacklist")
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(TokenBlacklistService.class)
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
public class BlacklistHealthIndicator implements HealthIndicator {

    private static final String FALLBACK_PROBE_TOKEN = "health-check-probe";

    private final TokenBlacklistService blacklistService;
    private final ObjectProvider<BlacklistProbe> probeProvider;

    public BlacklistHealthIndicator(
            TokenBlacklistService blacklistService,
            ObjectProvider<BlacklistProbe> probeProvider) {
        this.blacklistService = blacklistService;
        this.probeProvider = probeProvider;
    }

    @Override
    public Health health() {
        String implementationType = resolveImplementationType();

        try {
            // getIfUnique() returns null when zero or multiple probes are
            // registered, falling back to the service-level probe in both
            // cases. This is intentional: ambiguity should not cause a
            // health-check failure.
            BlacklistProbe probe = probeProvider.getIfUnique();
            if (probe != null) {
                probe.probe();
            } else {
                blacklistService.isBlacklisted(FALLBACK_PROBE_TOKEN);
            }
            return Health.up()
                .withDetail("type", implementationType)
                .withDetail("implementation", blacklistService.getClass().getSimpleName())
                .build();
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("type", implementationType)
                .withDetail("implementation", blacklistService.getClass().getSimpleName())
                .build();
        }
    }

    private String resolveImplementationType() {
        String className = blacklistService.getClass().getSimpleName().toLowerCase();
        if (className.contains("redis")) {
            return "redis";
        } else if (className.contains("nats")) {
            return "nats";
        } else if (className.contains("caffeine")) {
            return "caffeine";
        } else if (className.contains("inmemory")) {
            return "inmemory";
        }
        return "unknown";
    }
}
