package dev.simplecore.simplix.scheduler.strategy;

import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryLoggingStrategy - In-memory scheduler logging storage")
class InMemoryLoggingStrategyTest {

    private InMemoryLoggingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new InMemoryLoggingStrategy();
    }

    @Nested
    @DisplayName("getName")
    class GetNameTest {

        @Test
        @DisplayName("Should return descriptive strategy name")
        void shouldReturnName() {
            assertThat(strategy.getName()).isEqualTo("In-Memory Scheduler Logging");
        }
    }

    @Nested
    @DisplayName("supports")
    class SupportsTest {

        @Test
        @DisplayName("Should support 'in-memory' mode")
        void shouldSupportInMemoryMode() {
            assertThat(strategy.supports("in-memory")).isTrue();
        }

        @Test
        @DisplayName("Should support 'memory' mode")
        void shouldSupportMemoryMode() {
            assertThat(strategy.supports("memory")).isTrue();
        }

        @Test
        @DisplayName("Should not support 'database' mode")
        void shouldNotSupportDatabaseMode() {
            assertThat(strategy.supports("database")).isFalse();
        }

        @Test
        @DisplayName("Should not support null mode")
        void shouldNotSupportNullMode() {
            assertThat(strategy.supports(null)).isFalse();
        }

        @Test
        @DisplayName("Should not support empty mode")
        void shouldNotSupportEmptyMode() {
            assertThat(strategy.supports("")).isFalse();
        }

        @Test
        @DisplayName("Should not support unknown mode")
        void shouldNotSupportUnknownMode() {
            assertThat(strategy.supports("redis")).isFalse();
        }
    }

    @Nested
    @DisplayName("ensureRegistryEntry")
    class EnsureRegistryEntryTest {

        @Test
        @DisplayName("Should create new registry entry for new scheduler")
        void shouldCreateNewEntry() {
            SchedulerMetadata metadata = createMetadata("TestJob_run");

            SchedulerRegistryEntry entry = strategy.ensureRegistryEntry(metadata);

            assertThat(entry).isNotNull();
            assertThat(entry.getSchedulerName()).isEqualTo("TestJob_run");
            assertThat(entry.getRegistryId()).isNotNull().isNotEmpty();
            assertThat(entry.getClassName()).isEqualTo("com.example.TestJob");
            assertThat(entry.getMethodName()).isEqualTo("run");
            assertThat(entry.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return same entry for same scheduler name")
        void shouldReturnCachedEntry() {
            SchedulerMetadata metadata = createMetadata("TestJob_run");

            SchedulerRegistryEntry first = strategy.ensureRegistryEntry(metadata);
            SchedulerRegistryEntry second = strategy.ensureRegistryEntry(metadata);

            assertThat(first.getRegistryId()).isEqualTo(second.getRegistryId());
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("Should create different entries for different scheduler names")
        void shouldCreateDifferentEntries() {
            SchedulerRegistryEntry entry1 = strategy.ensureRegistryEntry(createMetadata("Job1_run"));
            SchedulerRegistryEntry entry2 = strategy.ensureRegistryEntry(createMetadata("Job2_run"));

            assertThat(entry1.getRegistryId()).isNotEqualTo(entry2.getRegistryId());
            assertThat(entry1.getSchedulerName()).isNotEqualTo(entry2.getSchedulerName());
        }

        @Test
        @DisplayName("Should preserve scheduler type from metadata")
        void shouldPreserveSchedulerType() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("DistJob_run")
                .className("com.example.DistJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.DISTRIBUTED)
                .shedlockName("dist-lock")
                .build();

            SchedulerRegistryEntry entry = strategy.ensureRegistryEntry(metadata);

            assertThat(entry.getSchedulerType()).isEqualTo(SchedulerMetadata.SchedulerType.DISTRIBUTED);
            assertThat(entry.getShedlockName()).isEqualTo("dist-lock");
        }

        @Test
        @DisplayName("Should increment registry count")
        void shouldIncrementRegistryCount() {
            assertThat(strategy.getRegistryCount()).isZero();

            strategy.ensureRegistryEntry(createMetadata("Job1_run"));
            assertThat(strategy.getRegistryCount()).isEqualTo(1);

            strategy.ensureRegistryEntry(createMetadata("Job2_run"));
            assertThat(strategy.getRegistryCount()).isEqualTo(2);

            // Same name should not increment
            strategy.ensureRegistryEntry(createMetadata("Job1_run"));
            assertThat(strategy.getRegistryCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("createExecutionContext")
    class CreateExecutionContextTest {

        @Test
        @DisplayName("Should create execution context with RUNNING status")
        void shouldCreateContextWithRunningStatus() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        }

        @Test
        @DisplayName("Should set registry ID from registry entry")
        void shouldSetRegistryId() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getRegistryId()).isEqualTo(registry.getRegistryId());
        }

        @Test
        @DisplayName("Should set scheduler name from registry entry")
        void shouldSetSchedulerName() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getSchedulerName()).isEqualTo("TestJob_run");
        }

        @Test
        @DisplayName("Should set service name and server host")
        void shouldSetServiceNameAndServerHost() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "scheduler-server", "node-02");

            assertThat(context.getServiceName()).isEqualTo("scheduler-server");
            assertThat(context.getServerHost()).isEqualTo("node-02");
        }

        @Test
        @DisplayName("Should set start time close to now")
        void shouldSetStartTime() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getStartTime()).isNotNull();
        }

        @Test
        @DisplayName("Should set shedlock name from registry entry")
        void shouldSetShedlockName() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("DistJob_run")
                .className("com.example.DistJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.DISTRIBUTED)
                .shedlockName("dist-lock")
                .build();
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(metadata);

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getShedlockName()).isEqualTo("dist-lock");
        }

        @Test
        @DisplayName("Should increment execution count")
        void shouldIncrementExecutionCount() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            assertThat(strategy.getExecutionCount()).isZero();

            strategy.createExecutionContext(registry, "api-server", "host-01");
            assertThat(strategy.getExecutionCount()).isEqualTo(1);

            strategy.createExecutionContext(registry, "api-server", "host-01");
            assertThat(strategy.getExecutionCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("saveExecutionResult")
    class SaveExecutionResultTest {

        @Test
        @DisplayName("Should update registry entry with last execution info on success")
        void shouldUpdateRegistryOnSuccess() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));
            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            SchedulerExecutionResult result = SchedulerExecutionResult.success(250L);
            strategy.saveExecutionResult(context, result);

            SchedulerRegistryEntry updated = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));
            assertThat(updated.getLastExecutionAt()).isEqualTo(context.getStartTime());
            assertThat(updated.getLastDurationMs()).isEqualTo(250L);
        }

        @Test
        @DisplayName("Should update registry entry with last execution info on failure")
        void shouldUpdateRegistryOnFailure() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));
            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            SchedulerExecutionResult result = SchedulerExecutionResult.failure(500L, "Connection error");
            strategy.saveExecutionResult(context, result);

            SchedulerRegistryEntry updated = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));
            assertThat(updated.getLastDurationMs()).isEqualTo(500L);
        }

        @Test
        @DisplayName("Should handle save for non-existent scheduler name gracefully")
        void shouldHandleNonExistentScheduler() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .registryId("nonexistent")
                .schedulerName("NonExistentJob_run")
                .build();

            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L);

            // Should not throw
            strategy.saveExecutionResult(context, result);
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTest {

        @Test
        @DisplayName("Should clear all registry and execution caches")
        void shouldClearAllCaches() {
            SchedulerRegistryEntry registry = strategy.ensureRegistryEntry(createMetadata("TestJob_run"));
            strategy.createExecutionContext(registry, "api-server", "host-01");

            assertThat(strategy.getRegistryCount()).isEqualTo(1);
            assertThat(strategy.getExecutionCount()).isEqualTo(1);

            strategy.clearCache();

            assertThat(strategy.getRegistryCount()).isZero();
            assertThat(strategy.getExecutionCount()).isZero();
        }
    }

    @Nested
    @DisplayName("shutdown")
    class ShutdownTest {

        @Test
        @DisplayName("Should clear all data on shutdown")
        void shouldClearDataOnShutdown() {
            strategy.ensureRegistryEntry(createMetadata("TestJob_run"));

            assertThat(strategy.getRegistryCount()).isEqualTo(1);

            strategy.shutdown();

            assertThat(strategy.getRegistryCount()).isZero();
            assertThat(strategy.getExecutionCount()).isZero();
        }
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTest {

        @Test
        @DisplayName("Should complete without error")
        void shouldCompleteWithoutError() {
            // initialize is a logging-only method; verify it doesn't throw
            strategy.initialize();
        }
    }

    @Nested
    @DisplayName("isReady (default)")
    class IsReadyTest {

        @Test
        @DisplayName("Should return true by default")
        void shouldReturnTrue() {
            assertThat(strategy.isReady()).isTrue();
        }
    }

    private SchedulerMetadata createMetadata(String schedulerName) {
        String[] parts = schedulerName.split("_", 2);
        return SchedulerMetadata.builder()
            .schedulerName(schedulerName)
            .className("com.example." + parts[0])
            .methodName(parts.length > 1 ? parts[1] : "execute")
            .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
            .build();
    }
}
