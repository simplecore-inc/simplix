package dev.simplecore.simplix.scheduler.model;

/**
 * Execution status for scheduler jobs.
 */
public enum ExecutionStatus {
    /**
     * Scheduler is currently running
     */
    RUNNING,

    /**
     * Scheduler completed successfully
     */
    SUCCESS,

    /**
     * Scheduler failed with an error
     */
    FAILED,

    /**
     * Scheduler exceeded the timeout threshold
     */
    TIMEOUT
}
