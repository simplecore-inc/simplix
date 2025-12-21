package dev.simplecore.simplix.scheduler.strategy;

import dev.simplecore.simplix.scheduler.core.SchedulerLoggingStrategy;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of scheduler logging strategy.
 * <p>
 * Useful for development, testing, or when no database persistence is needed.
 * Data is lost on application restart.
 */
@Slf4j
public class InMemoryLoggingStrategy implements SchedulerLoggingStrategy {

    private static final String MODE = "in-memory";

    private final ConcurrentHashMap<String, SchedulerRegistryEntry> registryCache =
        new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, SchedulerExecutionContext> executionCache =
        new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "In-Memory Scheduler Logging";
    }

    @Override
    public boolean supports(String mode) {
        return MODE.equals(mode) || "memory".equals(mode);
    }

    @Override
    public SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata) {
        return registryCache.computeIfAbsent(metadata.getSchedulerName(), name -> {
            log.debug("Creating in-memory registry entry for: {}", name);
            return SchedulerRegistryEntry.builder()
                .registryId(UUID.randomUUID().toString())
                .schedulerName(metadata.getSchedulerName())
                .className(metadata.getClassName())
                .methodName(metadata.getMethodName())
                .schedulerType(metadata.getSchedulerType())
                .shedlockName(metadata.getShedlockName())
                .cronExpression(metadata.getCronExpression())
                .displayName(metadata.getSchedulerName())
                .enabled(true)
                .build();
        });
    }

    @Override
    public SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName,
        String serverHost
    ) {
        SchedulerExecutionContext context = SchedulerExecutionContext.builder()
            .registryId(registry.getRegistryId())
            .schedulerName(registry.getSchedulerName())
            .shedlockName(registry.getShedlockName())
            .startTime(Instant.now())
            .status(ExecutionStatus.RUNNING)
            .serviceName(serviceName)
            .serverHost(serverHost)
            .build();

        executionCache.put(registry.getSchedulerName() + "_" + context.getStartTime(), context);
        return context;
    }

    @Override
    public void saveExecutionResult(SchedulerExecutionContext context, SchedulerExecutionResult result) {
        // Update registry with last execution info
        SchedulerRegistryEntry existing = registryCache.get(context.getSchedulerName());
        if (existing != null) {
            SchedulerRegistryEntry updated = existing
                .withLastExecutionAt(context.getStartTime())
                .withLastDurationMs(result.getDurationMs());
            registryCache.put(context.getSchedulerName(), updated);
        }

        log.debug("Saved in-memory execution result for {}: {} in {}ms",
            context.getSchedulerName(),
            result.getStatus(),
            result.getDurationMs());
    }

    @Override
    public void initialize() {
        log.info("Initialized In-Memory Scheduler Logging Strategy");
    }

    @Override
    public void shutdown() {
        log.info("Shutting down In-Memory Scheduler Logging Strategy");
        registryCache.clear();
        executionCache.clear();
    }

    @Override
    public void clearCache() {
        registryCache.clear();
        executionCache.clear();
        log.debug("In-memory caches cleared");
    }

    /**
     * Get the number of registered schedulers
     */
    public int getRegistryCount() {
        return registryCache.size();
    }

    /**
     * Get the number of cached executions
     */
    public int getExecutionCount() {
        return executionCache.size();
    }
}
