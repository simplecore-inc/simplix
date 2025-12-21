package dev.simplecore.simplix.scheduler.model;

import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;

/**
 * Data transfer object representing a scheduler registry entry.
 * <p>
 * Used to transfer data between SimpliX framework and consuming project's entities.
 */
@Value
@Builder
@With
public class SchedulerRegistryEntry {

    /**
     * Unique registry ID
     */
    String registryId;

    /**
     * Unique scheduler name
     */
    String schedulerName;

    /**
     * Fully qualified class name
     */
    String className;

    /**
     * Method name
     */
    String methodName;

    /**
     * Scheduler type
     */
    SchedulerMetadata.SchedulerType schedulerType;

    /**
     * ShedLock name (null for local schedulers)
     */
    String shedlockName;

    /**
     * Cron expression or schedule info
     */
    String cronExpression;

    /**
     * Display name for UI
     */
    String displayName;

    /**
     * Whether the scheduler is enabled
     */
    Boolean enabled;

    /**
     * Last execution timestamp
     */
    Instant lastExecutionAt;

    /**
     * Last execution duration in milliseconds
     */
    Long lastDurationMs;

    /**
     * Create from metadata with new registry ID
     */
    public static SchedulerRegistryEntry fromMetadata(SchedulerMetadata metadata) {
        return SchedulerRegistryEntry.builder()
            .schedulerName(metadata.getSchedulerName())
            .className(metadata.getClassName())
            .methodName(metadata.getMethodName())
            .schedulerType(metadata.getSchedulerType())
            .shedlockName(metadata.getShedlockName())
            .cronExpression(metadata.getCronExpression())
            .displayName(metadata.getSchedulerName())
            .enabled(true)
            .build();
    }

    /**
     * Check if metadata needs update compared to current registry entry.
     *
     * @param metadata The current metadata from scheduler annotation
     * @return true if any metadata field has changed
     */
    public boolean needsMetadataUpdate(SchedulerMetadata metadata) {
        return !equals(className, metadata.getClassName())
            || !equals(methodName, metadata.getMethodName())
            || !equals(cronExpression, metadata.getCronExpression())
            || !equals(shedlockName, metadata.getShedlockName())
            || schedulerType != metadata.getSchedulerType();
    }

    private static boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
