package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;

/**
 * Provider interface for execution log operations.
 * <p>
 * Consuming projects implement this interface to connect SimpliX's scheduler
 * logging with their specific execution log entity and repository.
 * <p>
 * Example implementation:
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class MyExecutionLogProvider
 *     implements SchedulerExecutionLogProvider<SchedulerExecutionLog> {
 *
 *     private final SchedulerExecutionLogRepository repository;
 *
 *     @Override
 *     public SchedulerExecutionLog createFromContext(SchedulerExecutionContext ctx) {
 *         return SchedulerExecutionLog.builder()
 *             .schedulerJobRegistryId(ctx.getRegistryId())
 *             .schedulerName(ctx.getSchedulerName())
 *             .executionStartAt(ctx.getStartTime())
 *             .status(SchedulerExecutionStatus.RUNNING)
 *             .build();
 *     }
 *
 *     @Override
 *     public void applyResult(SchedulerExecutionLog entity, SchedulerExecutionResult result) {
 *         if (result.isSuccess()) {
 *             entity.markSuccess();
 *         } else {
 *             entity.markFailed(result.getErrorMessage());
 *         }
 *     }
 *
 *     @Override
 *     public SchedulerExecutionLog save(SchedulerExecutionLog entity) {
 *         return repository.save(entity);
 *     }
 * }
 * }</pre>
 *
 * @param <T> The entity type used by the consuming project
 */
public interface SchedulerExecutionLogProvider<T> {

    /**
     * Create a new execution log entity from context.
     * <p>
     * The entity should be in RUNNING status initially.
     *
     * @param context Execution context
     * @return New entity (not yet persisted)
     */
    T createFromContext(SchedulerExecutionContext context);

    /**
     * Apply execution result to the entity.
     * <p>
     * Updates the entity's status, end time, duration, and error message based on the result.
     *
     * @param entity The entity to update
     * @param result Execution result
     */
    void applyResult(T entity, SchedulerExecutionResult result);

    /**
     * Save execution log entity
     *
     * @param entity The entity to save
     * @return Saved entity
     */
    T save(T entity);
}
