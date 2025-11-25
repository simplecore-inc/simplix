package dev.simplecore.simplix.event.core;

import dev.simplecore.simplix.core.event.Event;

/**
 * Strategy interface for different event publishing implementations
 * Implements the Strategy pattern to allow runtime selection of publishing mechanism
 */
public interface EventStrategy {

    /**
     * Publish an event using this strategy
     * @param event The event to publish
     * @param options Publishing options
     */
    void publish(Event event, PublishOptions options);

    /**
     * Check if this strategy supports the given mode
     * @param mode The mode string (e.g., "local", "redis", "kafka")
     * @return true if this strategy handles the mode
     */
    boolean supports(String mode);

    /**
     * Initialize the strategy (called once on startup)
     */
    void initialize();

    /**
     * Cleanup resources (called on shutdown)
     */
    void shutdown();

    /**
     * Check if the strategy is ready to publish
     * @return true if ready
     */
    boolean isReady();

    /**
     * Get strategy name for logging/monitoring
     * @return Strategy name
     */
    String getName();
}