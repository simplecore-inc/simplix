package dev.simplecore.simplix.stream.core.enums;

/**
 * State of a subscription scheduler.
 * <p>
 * State transitions:
 * <pre>
 * CREATED → RUNNING ↔ ERROR
 *     |         |          |
 *     |_________|__________|
 *               v
 *           STOPPED (when subscriber count = 0)
 * </pre>
 */
public enum SchedulerState {

    /**
     * Scheduler created but not yet executed
     */
    CREATED,

    /**
     * Scheduler actively running and collecting data
     */
    RUNNING,

    /**
     * Scheduler encountered consecutive errors (still attempting recovery)
     */
    ERROR,

    /**
     * Scheduler stopped (no subscribers)
     */
    STOPPED
}
