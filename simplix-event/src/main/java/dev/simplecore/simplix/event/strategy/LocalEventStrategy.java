package dev.simplecore.simplix.event.strategy;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CompletableFuture;

/**
 * Local event strategy using Spring's ApplicationEventPublisher
 * This strategy publishes events within the same JVM instance only
 */
@RequiredArgsConstructor
@Slf4j
public class LocalEventStrategy implements EventStrategy {

    private final ApplicationEventPublisher applicationEventPublisher;
    private volatile boolean ready = true;

    @Override
    public void publish(Event event, PublishOptions options) {
        if (!isReady()) {
            throw new IllegalStateException("Local event strategy is not ready");
        }

        try {
            log.trace("Publishing event locally: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId());

            // For local events, we ignore most options since they're synchronous in-memory
            if (options.isAsync()) {
                // In a real implementation, you might use @Async here
                publishAsync(event);
            } else {
                publishSync(event);
            }

            log.trace("Successfully published local event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish local event: {}", event.getEventId(), e);

            if (options.isCritical()) {
                throw new EventPublishException("Failed to publish critical event", e);
            }
        }
    }

    private void publishSync(Event event) {
        applicationEventPublisher.publishEvent(event);
    }

    private void publishAsync(Event event) {
        // Truly asynchronous publish using CompletableFuture
        CompletableFuture.runAsync(() -> {
            try {
                applicationEventPublisher.publishEvent(event);
                log.trace("Asynchronously published local event: {}", event.getEventId());
            } catch (Exception e) {
                log.error("Failed to asynchronously publish local event: {}", event.getEventId(), e);
            }
        });
    }

    @Override
    public boolean supports(String mode) {
        return "local".equalsIgnoreCase(mode);
    }

    @Override
    public void initialize() {
        log.info("Initializing Local Event Strategy");
        this.ready = true;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Local Event Strategy");
        this.ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getName() {
        return "LocalEventStrategy";
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