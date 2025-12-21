package dev.simplecore.simplix.scheduler.core;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable metadata about a scheduler method.
 * <p>
 * Captures information extracted from @Scheduled and @SchedulerLock annotations.
 */
@Value
@Builder
public class SchedulerMetadata {

    /**
     * Unique scheduler name (format: "ClassName_methodName")
     */
    String schedulerName;

    /**
     * Fully qualified class name containing the scheduled method
     */
    String className;

    /**
     * Name of the scheduled method
     */
    String methodName;

    /**
     * Cron expression or fixed delay/rate info
     */
    String cronExpression;

    /**
     * ShedLock name (null for local schedulers)
     */
    String shedlockName;

    /**
     * Scheduler type (LOCAL or DISTRIBUTED)
     */
    SchedulerType schedulerType;

    /**
     * Scheduler type enumeration
     */
    public enum SchedulerType {
        /**
         * Local scheduler - runs on each instance
         */
        LOCAL,

        /**
         * Distributed scheduler - coordinated via ShedLock
         */
        DISTRIBUTED
    }
}
