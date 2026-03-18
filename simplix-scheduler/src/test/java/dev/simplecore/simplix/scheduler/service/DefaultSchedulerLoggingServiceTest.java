package dev.simplecore.simplix.scheduler.service;

import dev.simplecore.simplix.scheduler.config.SchedulerProperties;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingStrategy;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultSchedulerLoggingService - Logging service delegation and strategy selection")
class DefaultSchedulerLoggingServiceTest {

    @Mock
    private SchedulerLoggingStrategy databaseStrategy;

    @Mock
    private SchedulerLoggingStrategy inMemoryStrategy;

    @Nested
    @DisplayName("Strategy selection")
    class StrategySelectionTest {

        @Test
        @DisplayName("Should select strategy matching configured mode")
        void shouldSelectMatchingStrategy() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy, inMemoryStrategy), properties);

            assertThat(service.getActiveStrategy()).isEqualTo(databaseStrategy);
            verify(databaseStrategy).initialize();
        }

        @Test
        @DisplayName("Should select in-memory strategy when mode is 'in-memory'")
        void shouldSelectInMemoryStrategy() {
            when(databaseStrategy.supports("in-memory")).thenReturn(false);
            when(inMemoryStrategy.supports("in-memory")).thenReturn(true);
            when(inMemoryStrategy.getName()).thenReturn("In-Memory");

            SchedulerProperties properties = createProperties("in-memory", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy, inMemoryStrategy), properties);

            assertThat(service.getActiveStrategy()).isEqualTo(inMemoryStrategy);
        }

        @Test
        @DisplayName("Should fall back to first available strategy when no match found")
        void shouldFallbackToFirstStrategy() {
            when(databaseStrategy.supports("unknown")).thenReturn(false);
            when(inMemoryStrategy.supports("unknown")).thenReturn(false);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("unknown", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy, inMemoryStrategy), properties);

            assertThat(service.getActiveStrategy()).isEqualTo(databaseStrategy);
        }

        @Test
        @DisplayName("Should use no-op strategy when no strategies configured")
        void shouldUseNoOpWhenEmpty() {
            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                Collections.emptyList(), properties);

            SchedulerLoggingStrategy active = service.getActiveStrategy();
            assertThat(active.getName()).isEqualTo("No-Op Strategy");
            assertThat(active.supports("anything")).isTrue();
        }
    }

    @Nested
    @DisplayName("ensureRegistryEntry")
    class EnsureRegistryEntryTest {

        @Test
        @DisplayName("Should delegate to active strategy")
        void shouldDelegateToActiveStrategy() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");
            SchedulerRegistryEntry expected = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .build();
            when(databaseStrategy.ensureRegistryEntry(any())).thenReturn(expected);

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("TestJob_run")
                .build();

            SchedulerRegistryEntry result = service.ensureRegistryEntry(metadata);

            assertThat(result).isEqualTo(expected);
            verify(databaseStrategy).ensureRegistryEntry(metadata);
        }
    }

    @Nested
    @DisplayName("createExecutionContext")
    class CreateExecutionContextTest {

        @Test
        @DisplayName("Should delegate to active strategy with server host")
        void shouldDelegateToActiveStrategy() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");
            SchedulerExecutionContext expected = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .status(ExecutionStatus.RUNNING)
                .build();
            when(databaseStrategy.createExecutionContext(any(), anyString(), anyString()))
                .thenReturn(expected);

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .build();

            SchedulerExecutionContext result = service.createExecutionContext(registry, "api-server");

            assertThat(result).isEqualTo(expected);
            verify(databaseStrategy).createExecutionContext(eq(registry), eq("api-server"), anyString());
        }
    }

    @Nested
    @DisplayName("saveExecutionResult")
    class SaveExecutionResultTest {

        @Test
        @DisplayName("Should delegate to active strategy")
        void shouldDelegateToActiveStrategy() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("TestJob_run")
                .build();
            SchedulerExecutionResult result = SchedulerExecutionResult.success(200L);

            service.saveExecutionResult(context, result);

            verify(databaseStrategy).saveExecutionResult(context, result);
        }

        @Test
        @DisplayName("Should catch and log exception from strategy without rethrowing")
        void shouldCatchExceptionFromStrategy() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");
            doThrow(new RuntimeException("DB error"))
                .when(databaseStrategy).saveExecutionResult(any(), any());

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("TestJob_run")
                .build();
            SchedulerExecutionResult result = SchedulerExecutionResult.success(200L);

            // Should not throw
            service.saveExecutionResult(context, result);
        }
    }

    @Nested
    @DisplayName("isEnabled")
    class IsEnabledTest {

        @Test
        @DisplayName("Should return true when enabled in properties")
        void shouldReturnTrueWhenEnabled() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            assertThat(service.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return false when disabled in properties")
        void shouldReturnFalseWhenDisabled() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", false);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            assertThat(service.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("isExcluded")
    class IsExcludedTest {

        @Test
        @DisplayName("Should return true for excluded scheduler")
        void shouldReturnTrueForExcludedScheduler() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", true);
            properties.setExcludedSchedulers(List.of("CacheMetrics", "Health"));
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            assertThat(service.isExcluded("CacheMetricsCollector_collect")).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-excluded scheduler")
        void shouldReturnFalseForNonExcludedScheduler() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", true);
            properties.setExcludedSchedulers(List.of("CacheMetrics"));
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            assertThat(service.isExcluded("SyncJob_execute")).isFalse();
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTest {

        @Test
        @DisplayName("Should delegate clearCache to active strategy")
        void shouldDelegateClearCache() {
            when(databaseStrategy.supports("database")).thenReturn(true);
            when(databaseStrategy.getName()).thenReturn("Database");

            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                List.of(databaseStrategy), properties);

            service.clearCache();

            verify(databaseStrategy).clearCache();
        }
    }

    @Nested
    @DisplayName("No-Op Strategy behavior")
    class NoOpStrategyTest {

        @Test
        @DisplayName("No-Op strategy should return registry entry from metadata")
        void noOpShouldReturnRegistryFromMetadata() {
            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                Collections.emptyList(), properties);

            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("TestJob_run")
                .className("com.example.TestJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            SchedulerRegistryEntry result = service.ensureRegistryEntry(metadata);

            assertThat(result.getSchedulerName()).isEqualTo("TestJob_run");
        }

        @Test
        @DisplayName("No-Op strategy should create execution context with noop registry ID")
        void noOpShouldCreateContextWithNoopId() {
            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                Collections.emptyList(), properties);

            SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
                .schedulerName("TestJob_run")
                .build();

            SchedulerExecutionContext result = service.createExecutionContext(registry, "api-server");

            assertThat(result.getRegistryId()).isEqualTo("noop");
            assertThat(result.getSchedulerName()).isEqualTo("TestJob_run");
            assertThat(result.getServiceName()).isEqualTo("api-server");
        }

        @Test
        @DisplayName("No-Op strategy should handle saveExecutionResult without error")
        void noOpShouldHandleSaveWithoutError() {
            SchedulerProperties properties = createProperties("database", true);
            DefaultSchedulerLoggingService service = new DefaultSchedulerLoggingService(
                Collections.emptyList(), properties);

            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("TestJob_run")
                .build();
            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L);

            // Should not throw
            service.saveExecutionResult(context, result);
        }
    }

    private SchedulerProperties createProperties(String mode, boolean enabled) {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setMode(mode);
        properties.setEnabled(enabled);
        return properties;
    }
}
