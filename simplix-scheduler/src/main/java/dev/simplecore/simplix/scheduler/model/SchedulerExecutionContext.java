package dev.simplecore.simplix.scheduler.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;

/**
 * Execution context for a scheduler run.
 * <p>
 * Carries information needed to track and log a single scheduler execution.
 */
@Value
@Builder
@With
public class SchedulerExecutionContext {

    /**
     * Registry ID this execution belongs to
     */
    String registryId;

    /**
     * Scheduler name
     */
    String schedulerName;

    /**
     * ShedLock name (if distributed)
     */
    String shedlockName;

    /**
     * Execution start time
     */
    Instant startTime;

    /**
     * Current execution status
     */
    ExecutionStatus status;

    /**
     * Service name (e.g., "api-server", "scheduler")
     */
    String serviceName;

    /**
     * Server hostname
     */
    String serverHost;

    /**
     * Calculate duration from start time to now
     */
    public long getDurationMs() {
        if (startTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }
}
