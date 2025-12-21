package dev.simplecore.simplix.scheduler.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Result of a scheduler execution.
 */
@Value
@Builder
public class SchedulerExecutionResult {

    /**
     * Final execution status
     */
    ExecutionStatus status;

    /**
     * Execution end time
     */
    Instant endTime;

    /**
     * Duration in milliseconds
     */
    Long durationMs;

    /**
     * Error message (if failed)
     */
    String errorMessage;

    /**
     * Number of items processed (optional)
     */
    Integer itemsProcessed;

    /**
     * Create a success result
     */
    public static SchedulerExecutionResult success(long durationMs) {
        return SchedulerExecutionResult.builder()
            .status(ExecutionStatus.SUCCESS)
            .endTime(Instant.now())
            .durationMs(durationMs)
            .build();
    }

    /**
     * Create a success result with items processed
     */
    public static SchedulerExecutionResult success(long durationMs, int itemsProcessed) {
        return SchedulerExecutionResult.builder()
            .status(ExecutionStatus.SUCCESS)
            .endTime(Instant.now())
            .durationMs(durationMs)
            .itemsProcessed(itemsProcessed)
            .build();
    }

    /**
     * Create a failure result
     */
    public static SchedulerExecutionResult failure(long durationMs, String errorMessage) {
        return SchedulerExecutionResult.builder()
            .status(ExecutionStatus.FAILED)
            .endTime(Instant.now())
            .durationMs(durationMs)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Create a timeout result
     */
    public static SchedulerExecutionResult timeout(long durationMs) {
        return SchedulerExecutionResult.builder()
            .status(ExecutionStatus.TIMEOUT)
            .endTime(Instant.now())
            .durationMs(durationMs)
            .build();
    }

    /**
     * Check if execution was successful
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    /**
     * Check if execution failed
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * Check if execution timed out
     */
    public boolean isTimeout() {
        return status == ExecutionStatus.TIMEOUT;
    }
}
