package dev.simplecore.simplix.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Scheduler execution logging configuration properties.
 * <p>
 * Configuration example:
 * <pre>{@code
 * simplix:
 *   scheduler:
 *     enabled: true
 *     mode: database
 *     retention-days: 90
 *     excluded-schedulers:
 *       - CacheMetricsCollector
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "simplix.scheduler")
public class SchedulerProperties {

    /**
     * Enable/disable scheduler logging
     */
    private boolean enabled = true;

    /**
     * Enable/disable the AOP aspect
     */
    private boolean aspectEnabled = true;

    /**
     * Logging mode: database, in-memory
     */
    private String mode = "database";

    /**
     * Number of days to retain execution logs
     */
    private int retentionDays = 90;

    /**
     * Cron expression for log cleanup
     */
    private String cleanupCron = "0 0 3 * * ?";

    /**
     * Timeout threshold in minutes for detecting stuck executions
     */
    private int stuckThresholdMinutes = 30;

    /**
     * List of scheduler name patterns to exclude from logging.
     * Supports simple class name prefix matching.
     */
    private List<String> excludedSchedulers = Collections.emptyList();

    /**
     * Lock configuration for distributed registry creation
     */
    private LockConfig lock = new LockConfig();

    /**
     * Check if a scheduler is excluded from logging
     *
     * @param schedulerName The scheduler name
     * @return true if excluded
     */
    public boolean isExcluded(String schedulerName) {
        if (excludedSchedulers == null || excludedSchedulers.isEmpty()) {
            return false;
        }
        return excludedSchedulers.stream()
            .anyMatch(pattern -> schedulerName.startsWith(pattern));
    }

    /**
     * Lock configuration for distributed coordination
     */
    @Data
    public static class LockConfig {

        /**
         * Maximum time to hold the lock
         */
        private Duration lockAtMost = Duration.ofSeconds(60);

        /**
         * Minimum time to hold the lock
         */
        private Duration lockAtLeast = Duration.ofSeconds(1);

        /**
         * Maximum retry attempts when lock not acquired
         */
        private int maxRetries = 3;

        /**
         * Retry delays in milliseconds (exponential backoff)
         */
        private long[] retryDelaysMs = {100, 200, 500};
    }
}
