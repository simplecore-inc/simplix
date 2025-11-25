package dev.simplecore.simplix.cache.config;

import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Cache Health Indicator
 * Provides health status for the cache subsystem
 */
public class CacheHealthIndicator implements HealthIndicator {

    private final CacheStrategy cacheStrategy;

    public CacheHealthIndicator(CacheStrategy cacheStrategy) {
        this.cacheStrategy = cacheStrategy;
    }

    @Override
    public Health health() {
        try {
            if (cacheStrategy.isAvailable()) {
                return Health.up()
                    .withDetail("strategy", cacheStrategy.getName())
                    .withDetail("available", true)
                    .build();
            } else {
                return Health.down()
                    .withDetail("strategy", cacheStrategy.getName())
                    .withDetail("available", false)
                    .withDetail("reason", "Cache strategy not available")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("strategy", cacheStrategy.getName())
                .withException(e)
                .build();
        }
    }
}