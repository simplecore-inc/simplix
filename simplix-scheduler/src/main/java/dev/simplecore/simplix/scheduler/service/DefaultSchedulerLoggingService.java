package dev.simplecore.simplix.scheduler.service;

import dev.simplecore.simplix.scheduler.config.SchedulerProperties;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingService;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingStrategy;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.List;

/**
 * Default implementation of scheduler logging service.
 * <p>
 * Delegates to the appropriate strategy based on configuration mode.
 */
@Slf4j
public class DefaultSchedulerLoggingService implements SchedulerLoggingService {

    private final List<SchedulerLoggingStrategy> strategies;
    private final SchedulerProperties properties;
    private final SchedulerLoggingStrategy activeStrategy;
    private String serverHost;

    public DefaultSchedulerLoggingService(
        List<SchedulerLoggingStrategy> strategies,
        SchedulerProperties properties
    ) {
        this.strategies = strategies;
        this.properties = properties;
        this.activeStrategy = selectStrategy(strategies, properties.getMode());

        log.info("Scheduler logging service initialized with strategy: {} (mode: {})",
            activeStrategy.getName(), properties.getMode());

        activeStrategy.initialize();
    }

    private SchedulerLoggingStrategy selectStrategy(
        List<SchedulerLoggingStrategy> strategies,
        String mode
    ) {
        return strategies.stream()
            .filter(s -> s.supports(mode))
            .findFirst()
            .orElseGet(() -> {
                log.warn("No strategy found for mode '{}', falling back to first available", mode);
                return strategies.isEmpty() ? createFallbackStrategy() : strategies.get(0);
            });
    }

    private SchedulerLoggingStrategy createFallbackStrategy() {
        log.warn("No strategies configured, using no-op strategy");
        return new NoOpLoggingStrategy();
    }

    @Override
    public SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata) {
        return activeStrategy.ensureRegistryEntry(metadata);
    }

    @Override
    public SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName
    ) {
        return activeStrategy.createExecutionContext(registry, serviceName, getServerHost());
    }

    @Override
    public void saveExecutionResult(SchedulerExecutionContext context, SchedulerExecutionResult result) {
        try {
            activeStrategy.saveExecutionResult(context, result);
        } catch (Exception e) {
            log.error("Failed to save execution result for [{}]: {}",
                context.getSchedulerName(), e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean isExcluded(String schedulerName) {
        return properties.isExcluded(schedulerName);
    }

    @Override
    public void clearCache() {
        activeStrategy.clearCache();
    }

    private String getServerHost() {
        if (serverHost == null) {
            try {
                serverHost = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                serverHost = "unknown";
            }
        }
        return serverHost;
    }

    /**
     * Get the currently active strategy
     */
    public SchedulerLoggingStrategy getActiveStrategy() {
        return activeStrategy;
    }

    /**
     * No-op strategy for fallback when no strategies are configured
     */
    private static class NoOpLoggingStrategy implements SchedulerLoggingStrategy {
        @Override
        public String getName() {
            return "No-Op Strategy";
        }

        @Override
        public boolean supports(String mode) {
            return true;
        }

        @Override
        public SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata) {
            return SchedulerRegistryEntry.fromMetadata(metadata);
        }

        @Override
        public SchedulerExecutionContext createExecutionContext(
            SchedulerRegistryEntry registry,
            String serviceName,
            String serverHost
        ) {
            return SchedulerExecutionContext.builder()
                .registryId("noop")
                .schedulerName(registry.getSchedulerName())
                .serviceName(serviceName)
                .serverHost(serverHost)
                .build();
        }

        @Override
        public void saveExecutionResult(
            SchedulerExecutionContext context,
            SchedulerExecutionResult result
        ) {
            // No-op
        }
    }
}
