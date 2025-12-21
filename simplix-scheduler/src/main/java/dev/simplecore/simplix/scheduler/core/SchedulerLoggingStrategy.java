package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;

/**
 * Strategy interface for scheduler execution logging.
 * <p>
 * Implementations can provide different storage backends (database, in-memory, etc.)
 * for scheduler execution logs.
 * <p>
 * Example implementation:
 * <pre>{@code
 * @Component
 * public class MyLoggingStrategy implements SchedulerLoggingStrategy {
 *     @Override
 *     public String getName() {
 *         return "My Custom Strategy";
 *     }
 *
 *     @Override
 *     public boolean supports(String mode) {
 *         return "custom".equals(mode);
 *     }
 *     // ... other methods
 * }
 * }</pre>
 */
public interface SchedulerLoggingStrategy {

    /**
     * Get the strategy name for logging/monitoring
     *
     * @return Strategy name
     */
    String getName();

    /**
     * Check if this strategy supports the given mode
     *
     * @param mode The mode string (e.g., "database", "in-memory")
     * @return true if this strategy handles the given mode
     */
    boolean supports(String mode);

    /**
     * Ensure a registry entry exists for the scheduler.
     * <p>
     * Creates a new entry if one doesn't exist, otherwise returns the existing one.
     *
     * @param metadata Scheduler metadata
     * @return Registry entry (never null)
     */
    SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata);

    /**
     * Create an execution context for tracking a scheduler run.
     *
     * @param registry    Registry entry for this scheduler
     * @param serviceName Service name (e.g., "api-server")
     * @param serverHost  Server hostname
     * @return Execution context
     */
    SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName,
        String serverHost
    );

    /**
     * Save execution result.
     *
     * @param context Execution context
     * @param result  Execution result
     */
    void saveExecutionResult(SchedulerExecutionContext context, SchedulerExecutionResult result);

    /**
     * Initialize the strategy.
     * <p>
     * Called during application startup.
     */
    default void initialize() {
        // Default: no-op
    }

    /**
     * Shutdown the strategy.
     * <p>
     * Called during application shutdown.
     */
    default void shutdown() {
        // Default: no-op
    }

    /**
     * Check if strategy is ready to accept requests.
     *
     * @return true if ready
     */
    default boolean isReady() {
        return true;
    }

    /**
     * Clear any caches held by the strategy.
     * <p>
     * Useful for testing or when external changes are made.
     */
    default void clearCache() {
        // Default: no-op
    }
}
