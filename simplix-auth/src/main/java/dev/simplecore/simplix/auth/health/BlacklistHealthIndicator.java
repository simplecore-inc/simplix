package dev.simplecore.simplix.auth.health;

import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import dev.simplecore.simplix.auth.service.impl.RedisTokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for the token blacklist service.
 * Reports the blacklist implementation type and availability status.
 *
 * <p>Endpoint: /actuator/health includes "blacklist" component.
 *
 * <p>For Redis-backed blacklist, this indicator directly probes the Redis connection
 * (bypassing the failure-mode catch in {@link RedisTokenBlacklistService}) to accurately
 * report DOWN when Redis is unavailable.
 */
@Component("blacklist")
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(TokenBlacklistService.class)
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
public class BlacklistHealthIndicator implements HealthIndicator {

    private final TokenBlacklistService blacklistService;
    private final RedisTemplate<String, String> redisTemplate;

    public BlacklistHealthIndicator(
            TokenBlacklistService blacklistService,
            @Autowired(required = false) RedisTemplate<String, String> redisTemplate) {
        this.blacklistService = blacklistService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        String implementationType = resolveImplementationType();

        try {
            if (blacklistService instanceof RedisTokenBlacklistService && redisTemplate != null) {
                // Direct Redis connectivity check — bypasses failure-mode catch
                redisTemplate.hasKey("simplix:token:bl:health-check-probe");
            } else {
                // For non-Redis implementations, probe via the service interface
                blacklistService.isBlacklisted("health-check-probe");
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
        } else if (className.contains("caffeine")) {
            return "caffeine";
        } else if (className.contains("inmemory")) {
            return "inmemory";
        }
        return "unknown";
    }
}
