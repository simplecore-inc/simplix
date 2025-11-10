package dev.simplecore.simplix.core.event;

/**
 * Core Event Publisher Interface
 * Provides event publishing abstraction for all modules
 * Implementations should be provided via SPI (Service Provider Interface)
 */
public interface EventPublisher {

    /**
     * Publish an event
     *
     * @param event the event to publish
     */
    void publish(Event event);

    /**
     * Publish an event with options
     * Default implementation just calls publish(event)
     *
     * @param event the event to publish
     * @param options publishing options (implementation specific)
     */
    default void publish(Event event, Object options) {
        publish(event);
    }

    /**
     * Check if publisher is available
     *
     * @return true if publisher is available
     */
    boolean isAvailable();

    /**
     * Get publisher name
     *
     * @return publisher name
     */
    String getName();

    /**
     * Get publisher priority (higher value = higher priority)
     *
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
}