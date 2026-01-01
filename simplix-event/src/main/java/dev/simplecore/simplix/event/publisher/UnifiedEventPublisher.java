package dev.simplecore.simplix.event.publisher;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.core.event.EventPublisher;
import dev.simplecore.simplix.event.config.EventMetrics;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private EventMetrics eventMetrics;

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
        log.info("UnifiedEventPublisher initialized with {} strategies", strategies.size());
        log.info("Active strategy: {}, Ready: {}",
            activeStrategy.getName(), activeStrategy.isReady());

        if (!activeStrategy.isReady()) {
            log.warn("Active strategy {} is not ready. Some events may not be published.",
                activeStrategy.getName());
        }
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
            String errorMsg = "Attempted to publish null event";
            log.error(errorMsg);
            // Throw exception to fail fast in development and catch issues early
            throw new IllegalArgumentException(errorMsg);
        }

        // Start metrics timer
        Timer.Sample sample = eventMetrics != null ? eventMetrics.startTimer() : null;

        // Enrich event metadata if enabled
        PublishOptions enrichedOptions = options;
        if (enrichMetadata) {
            enrichedOptions = enrichEventMetadata(event, options);
        }

        log.trace("Publishing event: {} with type: {} using strategy: {}",
            event.getEventId(), event.getEventType(), activeStrategy.getName());

        try {
            activeStrategy.publish(event, enrichedOptions);
            logEventPublished(event, enrichedOptions);

            // Record success metrics
            if (eventMetrics != null) {
                eventMetrics.recordPublished(event.getEventType().toString());
            }
        } catch (Exception e) {
            // Record failure metrics
            if (eventMetrics != null) {
                eventMetrics.recordFailed(event.getEventType().toString());
            }
            handlePublishError(event, enrichedOptions, e);
        } finally {
            // Stop timer
            if (eventMetrics != null && sample != null) {
                eventMetrics.stopTimer(sample);
            }
        }
    }

    /**
     * Enriches event metadata by adding instance information and publisher details
     * Returns a new PublishOptions with enriched headers
     */
    private PublishOptions enrichEventMetadata(Event event, PublishOptions options) {
        // Create enriched metadata
        Map<String, Object> enrichedHeaders = new HashMap<>();

        // Copy existing headers from options
        if (options.getHeaders() != null) {
            enrichedHeaders.putAll(options.getHeaders());
        }

        // Copy event metadata
        if (event.getMetadata() != null) {
            enrichedHeaders.putAll(event.getMetadata());
        }

        // Add publisher metadata
        enrichedHeaders.put("instanceId", instanceId);
        enrichedHeaders.put("publisherMode", mode);
        enrichedHeaders.put("enrichedAt", Instant.now().toString());

        // Create new options with enriched headers
        return PublishOptions.builder()
            .persistent(options.isPersistent())
            .critical(options.isCritical())
            .ttl(options.getTtl())
            .routingKey(options.getRoutingKey())
            .partitionKey(options.getPartitionKey())
            .async(options.isAsync())
            .maxRetries(options.getMaxRetries())
            .retryDelay(options.getRetryDelay())
            .headers(enrichedHeaders)
            .build();
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