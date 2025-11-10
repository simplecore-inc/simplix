package dev.simplecore.simplix.event.config;

import dev.simplecore.simplix.event.publisher.UnifiedEventPublisher;
import lombok.RequiredArgsConstructor;

/**
 * Health indicator for event publisher
 * Reports the health status of the event publishing system
 */
@RequiredArgsConstructor
public class EventPublisherHealthIndicator {

    private final UnifiedEventPublisher unifiedEventPublisher;
    private final EventProperties properties;

    public boolean isHealthy() {
        try {
            return unifiedEventPublisher.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    public String getMode() {
        return unifiedEventPublisher.getName();
    }

    public boolean isEnrichMetadataEnabled() {
        return properties.isEnrichMetadata();
    }

    public boolean isPersistentByDefault() {
        return properties.isPersistentByDefault();
    }
}