package dev.simplecore.simplix.stream.monitoring;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for stream module.
 * <p>
 * Reports the health status of stream components including
 * session registry, scheduler manager, and broadcast service.
 */
public class StreamHealthIndicator implements HealthIndicator {

    private final SessionRegistry sessionRegistry;
    private final SchedulerManager schedulerManager;
    private final BroadcastService broadcastService;
    private final StreamProperties properties;

    public StreamHealthIndicator(
            SessionRegistry sessionRegistry,
            SchedulerManager schedulerManager,
            BroadcastService broadcastService,
            StreamProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.schedulerManager = schedulerManager;
        this.broadcastService = broadcastService;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        boolean sessionRegistryUp = sessionRegistry.isAvailable();
        boolean broadcastServiceUp = broadcastService.isAvailable();

        // Check critical components
        if (!sessionRegistryUp || !broadcastServiceUp) {
            builder = Health.down();
        }

        // Add component details
        builder.withDetail("mode", properties.getMode().name())
                .withDetail("sessionRegistry", sessionRegistryUp ? "UP" : "DOWN")
                .withDetail("broadcastService", broadcastServiceUp ? "UP" : "DOWN")
                .withDetail("activeSessions", sessionRegistry.count())
                .withDetail("activeSchedulers", schedulerManager.getSchedulerCount());

        // Add scheduler limits
        int maxSchedulers = properties.getScheduler().getMaxTotalSchedulers();
        if (maxSchedulers > 0) {
            double schedulerUtilization = (double) schedulerManager.getSchedulerCount() / maxSchedulers;
            builder.withDetail("schedulerUtilization", String.format("%.1f%%", schedulerUtilization * 100));

            if (schedulerUtilization > 0.9) {
                builder.withDetail("schedulerWarning", "Scheduler limit nearly reached");
            }
        }

        return builder.build();
    }
}
