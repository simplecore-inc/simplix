package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;

import java.time.Instant;
import java.util.Optional;

/**
 * Provider interface for scheduler registry operations.
 * <p>
 * Consuming projects implement this interface to connect SimpliX's scheduler
 * logging with their specific entity and repository implementations.
 * <p>
 * Example implementation:
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class MySchedulerRegistryProvider
 *     implements SchedulerRegistryProvider<SchedulerJobRegistry> {
 *
 *     private final SchedulerJobRegistryRepository repository;
 *
 *     @Override
 *     public Optional<SchedulerJobRegistry> findBySchedulerName(String name) {
 *         return repository.findBySchedulerName(name);
 *     }
 *
 *     @Override
 *     public SchedulerJobRegistry save(SchedulerRegistryEntry entry) {
 *         SchedulerJobRegistry entity = mapToEntity(entry);
 *         return repository.save(entity);
 *     }
 *
 *     // ... other methods
 * }
 * }</pre>
 *
 * @param <T> The entity type used by the consuming project
 */
public interface SchedulerRegistryProvider<T> {

    /**
     * Find registry entry by scheduler name
     *
     * @param schedulerName Unique scheduler name
     * @return Optional containing the entity if found
     */
    Optional<T> findBySchedulerName(String schedulerName);

    /**
     * Check if a registry entry exists by scheduler name
     *
     * @param schedulerName Unique scheduler name
     * @return true if entry exists
     */
    default boolean existsBySchedulerName(String schedulerName) {
        return findBySchedulerName(schedulerName).isPresent();
    }

    /**
     * Save a new registry entry
     *
     * @param entry Registry entry data
     * @return Saved entity
     */
    T save(SchedulerRegistryEntry entry);

    /**
     * Update last execution statistics
     *
     * @param registryId  Registry ID
     * @param executionAt Execution timestamp
     * @param durationMs  Duration in milliseconds
     * @return Number of rows updated
     */
    int updateLastExecution(String registryId, Instant executionAt, Long durationMs);

    /**
     * Convert entity to registry entry DTO
     *
     * @param entity The entity to convert
     * @return Registry entry DTO
     */
    SchedulerRegistryEntry toRegistryEntry(T entity);

    /**
     * Get registry ID from entity
     *
     * @param entity The entity
     * @return Registry ID
     */
    String getRegistryId(T entity);

    /**
     * Update registry metadata (className, cronExpression, shedlockName, etc.)
     * <p>
     * Called when scheduler metadata has changed since initial registration.
     *
     * @param registryId Registry ID
     * @param metadata   Updated metadata
     * @return Number of rows updated
     */
    default int updateMetadata(String registryId, SchedulerMetadata metadata) {
        // Default implementation does nothing - providers can override
        return 0;
    }
}
