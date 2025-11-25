package dev.simplecore.simplix.event.config;

import dev.simplecore.simplix.event.publisher.UnifiedEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for event publisher
 * Reports the health status of the event publishing system
 */
@RequiredArgsConstructor
public class EventPublisherHealthIndicator implements HealthIndicator {

    private final UnifiedEventPublisher unifiedEventPublisher;
    private final EventProperties properties;

    @Override
    public Health health() {
        try {
            boolean isAvailable = unifiedEventPublisher.isAvailable();

            if (isAvailable) {
                return Health.up()
                    .withDetail("mode", unifiedEventPublisher.getName())
                    .withDetail("enrichMetadata", properties.isEnrichMetadata())
                    .withDetail("persistentByDefault", properties.isPersistentByDefault())
                    .withDetail("status", "Event publisher is ready")
                    .build();
            } else {
                return Health.down()
                    .withDetail("mode", unifiedEventPublisher.getName())
                    .withDetail("status", "Event publisher is not available")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Event publisher health check failed")
                .withException(e)
                .build();
        }
    }
}