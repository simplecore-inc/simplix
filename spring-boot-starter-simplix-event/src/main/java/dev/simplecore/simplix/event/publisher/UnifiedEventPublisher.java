package dev.simplecore.simplix.event.publisher;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.core.event.EventPublisher;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified event publisher that delegates to appropriate strategy
 * This is the main implementation of Core EventPublisher interface
 */
@Slf4j
public class UnifiedEventPublisher implements EventPublisher {

    private final List<EventStrategy> strategies;
    private final EventStrategy activeStrategy;
    private final String mode;

    @Value("${simplix.events.enrich-metadata:true}")
    private boolean enrichMetadata;

    @Value("${simplix.events.instance-id:#{T(java.util.UUID).randomUUID().toString()}}")
    private String instanceId;

    public UnifiedEventPublisher(List<EventStrategy> strategies, String mode) {
        this.strategies = strategies;
        this.mode = mode;
        this.activeStrategy = selectStrategy(mode);
        log.info("UnifiedEventPublisher initialized with mode: {} using strategy: {}",
            mode, activeStrategy.getName());
    }

    private EventStrategy selectStrategy(String mode) {
        return strategies.stream()
            .filter(s -> s.supports(mode))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No event strategy found for mode: " + mode +
                ". Available strategies: " + strategies.stream()
                    .map(EventStrategy::getName)
                    .toList()));
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing UnifiedEventPublisher with {} strategies", strategies.size());
        activeStrategy.initialize();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down UnifiedEventPublisher");
        activeStrategy.shutdown();
    }

    @Override
    public void publish(Event event) {
        publish(event, PublishOptions.defaults());
    }

    @Override
    public void publish(Event event, Object options) {
        PublishOptions publishOptions = (options instanceof PublishOptions)
            ? (PublishOptions) options
            : PublishOptions.defaults();

        publishWithOptions(event, publishOptions);
    }

    private void publishWithOptions(Event event, PublishOptions options) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return;
        }

        // Enrich event metadata if enabled
        if (enrichMetadata) {
            enrichEvent(event);
        }

        log.debug("Publishing event: {} with type: {} using strategy: {}",
            event.getEventId(), event.getEventType(), activeStrategy.getName());

        try {
            activeStrategy.publish(event, options);
            logEventPublished(event, options);
        } catch (Exception e) {
            handlePublishError(event, options, e);
        }
    }

    private void enrichEvent(Event event) {
        // Create enriched metadata
        Map<String, Object> enrichedMetadata = new HashMap<>();
        if (event.getMetadata() != null) {
            enrichedMetadata.putAll(event.getMetadata());
        }
        enrichedMetadata.put("instanceId", instanceId);
        enrichedMetadata.put("publisherMode", mode);
        enrichedMetadata.put("enrichedAt", Instant.now().toString());

        // Note: Core Event interface is immutable,
        // so we pass enriched metadata through the strategy
    }

    private void logEventPublished(Event event, PublishOptions options) {
        if (log.isTraceEnabled()) {
            log.trace("Event published successfully - ID: {}, Type: {}, Critical: {}, Persistent: {}",
                event.getEventId(),
                event.getEventType(),
                options.isCritical(),
                options.isPersistent());
        }
    }

    private void handlePublishError(Event event, PublishOptions options, Exception e) {
        log.error("Failed to publish event: {} with type: {}",
            event.getEventId(), event.getEventType(), e);

        if (options.isCritical()) {
            // For critical events, we propagate the exception
            throw new EventPublishException(
                "Failed to publish critical event: " + event.getEventId(), e);
        }

        // For non-critical events, we just log the error
        log.warn("Non-critical event publish failed, continuing. Event ID: {}",
            event.getEventId());
    }

    @Override
    public boolean isAvailable() {
        return activeStrategy != null && activeStrategy.isReady();
    }

    @Override
    public String getName() {
        return "UnifiedEventPublisher-" + mode;
    }

    @Override
    public int getPriority() {
        return 100; // High priority
    }

    /**
     * Custom exception for event publishing failures
     */
    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}