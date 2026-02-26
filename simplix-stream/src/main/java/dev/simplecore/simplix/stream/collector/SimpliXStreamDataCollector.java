package dev.simplecore.simplix.stream.collector;

import java.util.Map;

/**
 * Interface for data collection implementations.
 * <p>
 * Each resource type should have a corresponding SimpliXStreamDataCollector implementation.
 * Collectors are automatically discovered and registered by the framework.
 */
public interface SimpliXStreamDataCollector {

    /**
     * Get the resource name this collector handles.
     * <p>
     * This name is used to match subscription requests to collectors.
     *
     * @return the resource name (e.g., "cpu", "memory", "sales")
     */
    String getResource();

    /**
     * Collect data for the given parameters.
     * <p>
     * This method is called periodically based on the subscription interval.
     * The returned object will be serialized to JSON and sent to subscribers.
     *
     * @param params the subscription parameters
     * @return the collected data (will be serialized to JSON)
     */
    Object collect(Map<String, Object> params);

    /**
     * Get the default interval for this resource in milliseconds.
     * <p>
     * This is used when no interval is specified by the subscriber.
     *
     * @return the default interval in milliseconds
     */
    default long getDefaultIntervalMs() {
        return 1000L;
    }

    /**
     * Get the minimum allowed interval for this resource in milliseconds.
     * <p>
     * Requests for shorter intervals will be clamped to this value.
     *
     * @return the minimum interval in milliseconds
     */
    default long getMinIntervalMs() {
        return 100L;
    }

    /**
     * Validate parameters before subscription.
     * <p>
     * Override this method to validate subscription parameters.
     * Return false to reject the subscription.
     *
     * @param params the subscription parameters
     * @return true if parameters are valid
     */
    default boolean validateParams(Map<String, Object> params) {
        return true;
    }

    /**
     * Get required permission for this resource.
     * <p>
     * Override to specify a Spring Security permission/authority required
     * to subscribe to this resource.
     *
     * @return the required permission, or null if no permission required
     */
    default String getRequiredPermission() {
        return null;
    }
}
