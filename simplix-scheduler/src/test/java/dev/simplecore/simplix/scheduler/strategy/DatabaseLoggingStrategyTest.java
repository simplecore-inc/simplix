package dev.simplecore.simplix.scheduler.strategy;

import dev.simplecore.simplix.scheduler.config.SchedulerProperties;
import dev.simplecore.simplix.scheduler.core.SchedulerExecutionLogProvider;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.core.SchedulerRegistryProvider;
import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseLoggingStrategy - Database-backed scheduler logging with distributed lock")
class DatabaseLoggingStrategyTest {

    @Mock
    private SchedulerRegistryProvider<Object> registryProvider;

    @Mock
    private SchedulerExecutionLogProvider<Object> logProvider;

    @Mock
    private LockProvider lockProvider;

    private SchedulerProperties properties;
    private DatabaseLoggingStrategy strategy;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        properties.setMode("database");
        SchedulerProperties.LockConfig lockConfig = new SchedulerProperties.LockConfig();
        lockConfig.setLockAtMost(Duration.ofSeconds(60));
        lockConfig.setLockAtLeast(Duration.ofSeconds(1));
        lockConfig.setMaxRetries(3);
        lockConfig.setRetryDelaysMs(new long[]{50, 100, 200});
        properties.setLock(lockConfig);
        strategy = new DatabaseLoggingStrategy(registryProvider, logProvider, lockProvider, properties);
    }

    @Nested
    @DisplayName("getName")
    class GetNameTest {

        @Test
        @DisplayName("Should return descriptive strategy name")
        void shouldReturnName() {
            assertThat(strategy.getName()).isEqualTo("Database Scheduler Logging");
        }
    }

    @Nested
    @DisplayName("supports")
    class SupportsTest {

        @Test
        @DisplayName("Should support 'database' mode")
        void shouldSupportDatabaseMode() {
            assertThat(strategy.supports("database")).isTrue();
        }

        @Test
        @DisplayName("Should support 'db' mode")
        void shouldSupportDbMode() {
            assertThat(strategy.supports("db")).isTrue();
        }

        @Test
        @DisplayName("Should not support 'in-memory' mode")
        void shouldNotSupportInMemoryMode() {
            assertThat(strategy.supports("in-memory")).isFalse();
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
    }

    @Nested
    @DisplayName("ensureRegistryEntry")
    class EnsureRegistryEntryTest {

        @Test
        @DisplayName("Should return existing entry from database when found")
        void shouldReturnExistingEntryFromDb() {
            SchedulerMetadata metadata = createMetadata("TestJob_run");
            Object mockEntity = new Object();
            SchedulerRegistryEntry existing = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .className("com.example.TestJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            when(registryProvider.findBySchedulerName("TestJob_run")).thenReturn(Optional.of(mockEntity));
            when(registryProvider.toRegistryEntry(mockEntity)).thenReturn(existing);

            SchedulerRegistryEntry result = strategy.ensureRegistryEntry(metadata);

            assertThat(result.getRegistryId()).isEqualTo("reg-1");
            assertThat(result.getSchedulerName()).isEqualTo("TestJob_run");
            verify(registryProvider, never()).save(any());
        }

        @Test
        @DisplayName("Should create new entry with lock when not found in database")
        void shouldCreateNewEntryWithLock() {
            SchedulerMetadata metadata = createMetadata("NewJob_run");
            SimpleLock mockLock = mock(SimpleLock.class);
            Object savedEntity = new Object();
            SchedulerRegistryEntry savedEntry = SchedulerRegistryEntry.builder()
                .registryId("reg-new")
                .schedulerName("NewJob_run")
                .className("com.example.NewJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            when(registryProvider.findBySchedulerName("NewJob_run"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
            when(lockProvider.lock(any())).thenReturn(Optional.of(mockLock));
            when(registryProvider.save(any())).thenReturn(savedEntity);
            when(registryProvider.toRegistryEntry(savedEntity)).thenReturn(savedEntry);

            SchedulerRegistryEntry result = strategy.ensureRegistryEntry(metadata);

            assertThat(result.getRegistryId()).isEqualTo("reg-new");
            verify(registryProvider).save(any());
            verify(mockLock).unlock();
        }

        @Test
        @DisplayName("Should return cached entry on subsequent calls")
        void shouldReturnCachedEntry() {
            SchedulerMetadata metadata = createMetadata("CachedJob_run");
            Object mockEntity = new Object();
            SchedulerRegistryEntry existing = SchedulerRegistryEntry.builder()
                .registryId("reg-cached")
                .schedulerName("CachedJob_run")
                .className("com.example.CachedJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            when(registryProvider.findBySchedulerName("CachedJob_run")).thenReturn(Optional.of(mockEntity));
            when(registryProvider.toRegistryEntry(mockEntity)).thenReturn(existing);

            strategy.ensureRegistryEntry(metadata);
            strategy.ensureRegistryEntry(metadata);

            // Should only query DB once, then use cache
            verify(registryProvider, times(1)).findBySchedulerName("CachedJob_run");
        }

        @Test
        @DisplayName("Should create entry without lock when lock provider is null")
        void shouldCreateEntryWithoutLock() {
            DatabaseLoggingStrategy noLockStrategy = new DatabaseLoggingStrategy(
                registryProvider, logProvider, null, properties);

            SchedulerMetadata metadata = createMetadata("NoLockJob_run");
            Object savedEntity = new Object();
            SchedulerRegistryEntry savedEntry = SchedulerRegistryEntry.builder()
                .registryId("reg-nolock")
                .schedulerName("NoLockJob_run")
                .className("com.example.NoLockJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            when(registryProvider.findBySchedulerName("NoLockJob_run")).thenReturn(Optional.empty());
            when(registryProvider.save(any())).thenReturn(savedEntity);
            when(registryProvider.toRegistryEntry(savedEntity)).thenReturn(savedEntry);

            SchedulerRegistryEntry result = noLockStrategy.ensureRegistryEntry(metadata);

            assertThat(result.getRegistryId()).isEqualTo("reg-nolock");
            verify(registryProvider).save(any());
            verifyNoInteractions(lockProvider);
        }

        @Test
        @DisplayName("Should find entry created by another instance when lock not acquired")
        void shouldFindEntryWhenLockNotAcquired() {
            SchedulerMetadata metadata = createMetadata("ConcurrentJob_run");
            Object mockEntity = new Object();
            SchedulerRegistryEntry existing = SchedulerRegistryEntry.builder()
                .registryId("reg-concurrent")
                .schedulerName("ConcurrentJob_run")
                .className("com.example.ConcurrentJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            when(registryProvider.findBySchedulerName("ConcurrentJob_run"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockEntity));
            when(lockProvider.lock(any())).thenReturn(Optional.empty());
            when(registryProvider.toRegistryEntry(mockEntity)).thenReturn(existing);

            SchedulerRegistryEntry result = strategy.ensureRegistryEntry(metadata);

            assertThat(result.getSchedulerName()).isEqualTo("ConcurrentJob_run");
        }

        @Test
        @DisplayName("Should detect entry created between first DB check and lock acquisition")
        void shouldDetectEntryCreatedDuringLockAcquisition() {
            SchedulerMetadata metadata = createMetadata("RaceJob_run");
            SimpleLock mockLock = mock(SimpleLock.class);
            Object mockEntity = new Object();
            SchedulerRegistryEntry existing = SchedulerRegistryEntry.builder()
                .registryId("reg-race")
                .schedulerName("RaceJob_run")
                .className("com.example.RaceJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            // First call: not found, second call (after lock): found
            when(registryProvider.findBySchedulerName("RaceJob_run"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockEntity));
            when(lockProvider.lock(any())).thenReturn(Optional.of(mockLock));
            when(registryProvider.toRegistryEntry(mockEntity)).thenReturn(existing);

            SchedulerRegistryEntry result = strategy.ensureRegistryEntry(metadata);

            assertThat(result.getRegistryId()).isEqualTo("reg-race");
            verify(registryProvider, never()).save(any());
            verify(mockLock).unlock();
        }

        @Test
        @DisplayName("Should update metadata when cached entry has stale metadata")
        void shouldUpdateMetadataWhenStale() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("UpdateJob_run")
                .className("com.example.UpdatedJob")
                .methodName("run")
                .cronExpression("cron: 0 */10 * * * ?")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            Object mockEntity = new Object();
            SchedulerRegistryEntry staleEntry = SchedulerRegistryEntry.builder()
                .registryId("reg-stale")
                .schedulerName("UpdateJob_run")
                .className("com.example.OldJob")
                .methodName("run")
                .cronExpression("cron: 0 */5 * * * ?")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            SchedulerRegistryEntry refreshedEntry = SchedulerRegistryEntry.builder()
                .registryId("reg-stale")
                .schedulerName("UpdateJob_run")
                .className("com.example.UpdatedJob")
                .methodName("run")
                .cronExpression("cron: 0 */10 * * * ?")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            when(registryProvider.findBySchedulerName("UpdateJob_run"))
                .thenReturn(Optional.of(mockEntity))
                .thenReturn(Optional.of(mockEntity));
            when(registryProvider.toRegistryEntry(mockEntity))
                .thenReturn(staleEntry)
                .thenReturn(refreshedEntry);
            when(registryProvider.updateMetadata(eq("reg-stale"), any())).thenReturn(1);

            SchedulerRegistryEntry result = strategy.ensureRegistryEntry(metadata);

            verify(registryProvider).updateMetadata(eq("reg-stale"), eq(metadata));
            assertThat(result.getClassName()).isEqualTo("com.example.UpdatedJob");
        }
    }

    @Nested
    @DisplayName("createExecutionContext")
    class CreateExecutionContextTest {

        @Test
        @DisplayName("Should create context with RUNNING status")
        void shouldCreateContextWithRunningStatus() {
            SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .shedlockName("test-lock")
                .build();

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(context.getRegistryId()).isEqualTo("reg-1");
            assertThat(context.getSchedulerName()).isEqualTo("TestJob_run");
            assertThat(context.getShedlockName()).isEqualTo("test-lock");
            assertThat(context.getServiceName()).isEqualTo("api-server");
            assertThat(context.getServerHost()).isEqualTo("host-01");
            assertThat(context.getStartTime()).isNotNull();
        }

        @Test
        @DisplayName("Should handle null shedlock name")
        void shouldHandleNullShedlockName() {
            SchedulerRegistryEntry registry = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("LocalJob_run")
                .shedlockName(null)
                .build();

            SchedulerExecutionContext context = strategy.createExecutionContext(
                registry, "api-server", "host-01");

            assertThat(context.getShedlockName()).isNull();
        }
    }

    @Nested
    @DisplayName("saveExecutionResult")
    class SaveExecutionResultTest {

        @Test
        @DisplayName("Should create log entity, apply result, save, and update registry")
        void shouldSaveResultCompletely() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .startTime(Instant.now())
                .status(ExecutionStatus.RUNNING)
                .build();

            SchedulerExecutionResult result = SchedulerExecutionResult.success(200L);
            Object logEntity = new Object();

            when(logProvider.createFromContext(context)).thenReturn(logEntity);
            when(registryProvider.updateLastExecution(eq("reg-1"), any(), eq(200L))).thenReturn(1);

            strategy.saveExecutionResult(context, result);

            verify(logProvider).createFromContext(context);
            verify(logProvider).applyResult(logEntity, result);
            verify(logProvider).save(logEntity);
            verify(registryProvider).updateLastExecution(eq("reg-1"), any(), eq(200L));
        }

        @Test
        @DisplayName("Should save failure result with error message")
        void shouldSaveFailureResult() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("FailJob_run")
                .startTime(Instant.now())
                .build();

            SchedulerExecutionResult result = SchedulerExecutionResult.failure(500L, "DB error");
            Object logEntity = new Object();

            when(logProvider.createFromContext(context)).thenReturn(logEntity);
            when(registryProvider.updateLastExecution(eq("reg-1"), any(), eq(500L))).thenReturn(1);

            strategy.saveExecutionResult(context, result);

            verify(logProvider).applyResult(logEntity, result);
            verify(logProvider).save(logEntity);
        }

        @Test
        @DisplayName("Should handle case when registry update affects zero rows")
        void shouldHandleZeroRowsUpdated() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .registryId("nonexistent")
                .schedulerName("Ghost_run")
                .startTime(Instant.now())
                .build();

            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L);
            Object logEntity = new Object();

            when(logProvider.createFromContext(context)).thenReturn(logEntity);
            when(registryProvider.updateLastExecution(eq("nonexistent"), any(), eq(100L))).thenReturn(0);

            // Should not throw, just log a warning
            strategy.saveExecutionResult(context, result);

            verify(logProvider).save(logEntity);
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTest {

        @Test
        @DisplayName("Should clear internal registry cache")
        void shouldClearCache() {
            // Populate cache
            Object mockEntity = new Object();
            SchedulerRegistryEntry entry = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("CacheJob_run")
                .className("com.example.CacheJob")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();
            when(registryProvider.findBySchedulerName("CacheJob_run")).thenReturn(Optional.of(mockEntity));
            when(registryProvider.toRegistryEntry(mockEntity)).thenReturn(entry);

            strategy.ensureRegistryEntry(createMetadata("CacheJob_run"));
            strategy.clearCache();

            // After clearing, should query DB again
            strategy.ensureRegistryEntry(createMetadata("CacheJob_run"));
            verify(registryProvider, times(2)).findBySchedulerName("CacheJob_run");
        }
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTest {

        @Test
        @DisplayName("Should complete without error")
        void shouldCompleteWithoutError() {
            strategy.initialize();
            // No exception expected - logging only
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
