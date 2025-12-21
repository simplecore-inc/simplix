package dev.simplecore.simplix.scheduler.strategy;

import dev.simplecore.simplix.scheduler.config.SchedulerProperties;
import dev.simplecore.simplix.scheduler.core.SchedulerExecutionLogProvider;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingStrategy;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.core.SchedulerRegistryProvider;
import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database implementation of scheduler logging strategy.
 * <p>
 * Uses provider interfaces to interact with consuming project's entities.
 * Supports distributed locking via ShedLock for registry creation.
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings({"rawtypes", "unchecked"})
public class DatabaseLoggingStrategy implements SchedulerLoggingStrategy {

    private static final String MODE = "database";
    private static final String REGISTRY_LOCK_PREFIX = "sr-";

    private final SchedulerRegistryProvider registryProvider;
    private final SchedulerExecutionLogProvider logProvider;
    private final LockProvider lockProvider;
    private final SchedulerProperties properties;

    /**
     * In-memory cache for registry entries to reduce DB queries.
     */
    private final ConcurrentHashMap<String, SchedulerRegistryEntry> registryCache =
        new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Database Scheduler Logging";
    }

    @Override
    public boolean supports(String mode) {
        return MODE.equals(mode) || "db".equals(mode);
    }

    @Override
    public SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata) {
        String schedulerName = metadata.getSchedulerName();

        // Fast path 1: Check cache - but still validate metadata
        SchedulerRegistryEntry cached = registryCache.get(schedulerName);
        if (cached != null) {
            if (cached.needsMetadataUpdate(metadata)) {
                return updateAndCacheRegistry(cached, metadata);
            }
            return cached;
        }

        // Fast path 2: Check DB
        Optional<?> existing = registryProvider.findBySchedulerName(schedulerName);
        if (existing.isPresent()) {
            SchedulerRegistryEntry entry = registryProvider.toRegistryEntry(existing.get());

            // Check if metadata has changed
            if (entry.needsMetadataUpdate(metadata)) {
                return updateAndCacheRegistry(entry, metadata);
            }

            registryCache.put(schedulerName, entry);
            return entry;
        }

        // Slow path: Create with distributed lock
        SchedulerRegistryEntry registry = createRegistryEntryWithLock(metadata);
        registryCache.put(schedulerName, registry);
        return registry;
    }

    /**
     * Update registry metadata and refresh cache
     */
    private SchedulerRegistryEntry updateAndCacheRegistry(
        SchedulerRegistryEntry existing,
        SchedulerMetadata metadata
    ) {
        int updated = registryProvider.updateMetadata(existing.getRegistryId(), metadata);
        if (updated > 0) {
            log.info("Updated registry metadata for scheduler: {} (className={}, cron={})",
                metadata.getSchedulerName(), metadata.getClassName(), metadata.getCronExpression());
        }

        // Refresh from DB to get updated entry
        Optional<?> refreshed = registryProvider.findBySchedulerName(metadata.getSchedulerName());
        if (refreshed.isPresent()) {
            SchedulerRegistryEntry updatedEntry = registryProvider.toRegistryEntry(refreshed.get());
            registryCache.put(metadata.getSchedulerName(), updatedEntry);
            return updatedEntry;
        }

        // Fallback: return existing if refresh failed
        return existing;
    }

    private SchedulerRegistryEntry createRegistryEntryWithLock(SchedulerMetadata metadata) {
        String schedulerName = metadata.getSchedulerName();

        if (lockProvider != null) {
            LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                REGISTRY_LOCK_PREFIX + schedulerName,
                properties.getLock().getLockAtMost(),
                properties.getLock().getLockAtLeast()
            );

            Optional<SimpleLock> lock = lockProvider.lock(lockConfig);

            if (lock.isPresent()) {
                try {
                    // Double-check after acquiring lock
                    Optional<?> existing = registryProvider.findBySchedulerName(schedulerName);
                    if (existing.isPresent()) {
                        return registryProvider.toRegistryEntry(existing.get());
                    }
                    return createAndSaveRegistryEntry(metadata);
                } finally {
                    lock.get().unlock();
                }
            } else {
                // Another server holds the lock - wait with exponential backoff
                log.debug("Lock not acquired for [{}], waiting with retry", schedulerName);
                return waitAndFetchRegistryEntry(metadata);
            }
        } else {
            // No lock provider - create directly (single instance mode)
            return createAndSaveRegistryEntry(metadata);
        }
    }

    private SchedulerRegistryEntry waitAndFetchRegistryEntry(SchedulerMetadata metadata) {
        String schedulerName = metadata.getSchedulerName();
        long[] retryDelays = properties.getLock().getRetryDelaysMs();
        int maxRetries = properties.getLock().getMaxRetries();

        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(retryDelays[Math.min(i, retryDelays.length - 1)]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Optional<?> existing = registryProvider.findBySchedulerName(schedulerName);
            if (existing.isPresent()) {
                log.debug("Found registry entry for [{}] after {} retries", schedulerName, i + 1);
                return registryProvider.toRegistryEntry(existing.get());
            }
        }

        // Last resort: create it ourselves
        log.warn("Registry entry not found after {} retries, creating: {}", maxRetries, schedulerName);
        return createAndSaveRegistryEntry(metadata);
    }

    private SchedulerRegistryEntry createAndSaveRegistryEntry(SchedulerMetadata metadata) {
        SchedulerRegistryEntry entry = SchedulerRegistryEntry.fromMetadata(metadata);
        Object savedEntity = registryProvider.save(entry);
        log.info("Created registry entry for scheduler: {}", metadata.getSchedulerName());
        return registryProvider.toRegistryEntry(savedEntity);
    }

    @Override
    public SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName,
        String serverHost
    ) {
        return SchedulerExecutionContext.builder()
            .registryId(registry.getRegistryId())
            .schedulerName(registry.getSchedulerName())
            .shedlockName(registry.getShedlockName())
            .startTime(Instant.now())
            .status(ExecutionStatus.RUNNING)
            .serviceName(serviceName)
            .serverHost(serverHost)
            .build();
    }

    @Override
    public void saveExecutionResult(SchedulerExecutionContext context, SchedulerExecutionResult result) {
        // Create and save execution log
        SchedulerExecutionLogProvider provider = logProvider;
        Object logEntity = provider.createFromContext(context);
        provider.applyResult(logEntity, result);
        provider.save(logEntity);

        // Update registry with last execution info
        int updated = registryProvider.updateLastExecution(
            context.getRegistryId(),
            context.getStartTime(),
            result.getDurationMs()
        );

        if (updated == 0) {
            log.warn("Registry not found during execution log save. RegistryId: {}, Scheduler: {}",
                context.getRegistryId(), context.getSchedulerName());
        }

        log.debug("Saved execution result for {}: {} in {}ms",
            context.getSchedulerName(),
            result.getStatus(),
            result.getDurationMs());
    }

    @Override
    public void initialize() {
        log.info("Initialized Database Scheduler Logging Strategy");
    }

    @Override
    public void clearCache() {
        registryCache.clear();
        log.debug("Registry cache cleared");
    }
}
