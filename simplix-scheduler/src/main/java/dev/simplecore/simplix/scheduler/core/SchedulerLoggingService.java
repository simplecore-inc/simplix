package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;

/**
 * Main service interface for scheduler logging.
 * <p>
 * This is the primary entry point for scheduler execution logging.
 * The default implementation delegates to the configured strategy.
 */
public interface SchedulerLoggingService {

    /**
     * Ensure a registry entry exists for the scheduler
     *
     * @param metadata Scheduler metadata
     * @return Registry entry
     */
    SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata);

    /**
     * Create execution context for a scheduler run
     *
     * @param registry    Registry entry
     * @param serviceName Service name
     * @return Execution context
     */
    SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName
    );

    /**
     * Save execution result
     *
     * @param context Execution context
     * @param result  Execution result
     */
    void saveExecutionResult(SchedulerExecutionContext context, SchedulerExecutionResult result);

    /**
     * Check if logging is enabled
     *
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * Check if a scheduler is excluded from logging
     *
     * @param schedulerName Scheduler name
     * @return true if excluded
     */
    boolean isExcluded(String schedulerName);

    /**
     * Clear any caches
     */
    void clearCache();
}
