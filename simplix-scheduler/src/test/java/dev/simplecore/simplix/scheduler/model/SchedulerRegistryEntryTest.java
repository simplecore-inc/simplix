package dev.simplecore.simplix.scheduler.model;

import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerRegistryEntry - Registry entry creation and metadata comparison")
class SchedulerRegistryEntryTest {

    @Nested
    @DisplayName("fromMetadata")
    class FromMetadataTest {

        @Test
        @DisplayName("Should create entry from metadata with all fields mapped")
        void shouldCreateEntryFromMetadata() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("TestJob_execute")
                .className("com.example.TestJob")
                .methodName("execute")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .cronExpression("cron: 0 0 * * * ?")
                .shedlockName(null)
                .build();

            SchedulerRegistryEntry entry = SchedulerRegistryEntry.fromMetadata(metadata);

            assertThat(entry.getSchedulerName()).isEqualTo("TestJob_execute");
            assertThat(entry.getClassName()).isEqualTo("com.example.TestJob");
            assertThat(entry.getMethodName()).isEqualTo("execute");
            assertThat(entry.getSchedulerType()).isEqualTo(SchedulerMetadata.SchedulerType.LOCAL);
            assertThat(entry.getCronExpression()).isEqualTo("cron: 0 0 * * * ?");
            assertThat(entry.getShedlockName()).isNull();
            assertThat(entry.getDisplayName()).isEqualTo("TestJob_execute");
            assertThat(entry.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should create entry from distributed scheduler metadata")
        void shouldCreateEntryFromDistributedMetadata() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("sync-users")
                .className("com.example.SyncJob")
                .methodName("syncUsers")
                .schedulerType(SchedulerMetadata.SchedulerType.DISTRIBUTED)
                .cronExpression("cron: 0 */5 * * * ?")
                .shedlockName("sync-users-lock")
                .build();

            SchedulerRegistryEntry entry = SchedulerRegistryEntry.fromMetadata(metadata);

            assertThat(entry.getSchedulerType()).isEqualTo(SchedulerMetadata.SchedulerType.DISTRIBUTED);
            assertThat(entry.getShedlockName()).isEqualTo("sync-users-lock");
        }

        @Test
        @DisplayName("Should have null registryId when created from metadata")
        void shouldHaveNullRegistryId() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("test")
                .className("Test")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            SchedulerRegistryEntry entry = SchedulerRegistryEntry.fromMetadata(metadata);

            assertThat(entry.getRegistryId()).isNull();
        }

        @Test
        @DisplayName("Should have null execution info when created from metadata")
        void shouldHaveNullExecutionInfo() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("test")
                .className("Test")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            SchedulerRegistryEntry entry = SchedulerRegistryEntry.fromMetadata(metadata);

            assertThat(entry.getLastExecutionAt()).isNull();
            assertThat(entry.getLastDurationMs()).isNull();
        }
    }

    @Nested
    @DisplayName("needsMetadataUpdate")
    class NeedsMetadataUpdateTest {

        private SchedulerRegistryEntry createEntry(
            String className, String methodName, String cron,
            String shedlockName, SchedulerMetadata.SchedulerType type
        ) {
            return SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .className(className)
                .methodName(methodName)
                .cronExpression(cron)
                .shedlockName(shedlockName)
                .schedulerType(type)
                .build();
        }

        private SchedulerMetadata createMetadata(
            String className, String methodName, String cron,
            String shedlockName, SchedulerMetadata.SchedulerType type
        ) {
            return SchedulerMetadata.builder()
                .schedulerName("test")
                .className(className)
                .methodName(methodName)
                .cronExpression(cron)
                .shedlockName(shedlockName)
                .schedulerType(type)
                .build();
        }

        @Test
        @DisplayName("Should return false when all fields match")
        void shouldReturnFalseWhenAllFieldsMatch() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", "cron: 0 * * * * ?", null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", "cron: 0 * * * * ?", null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isFalse();
        }

        @Test
        @DisplayName("Should return true when className changed")
        void shouldReturnTrueWhenClassNameChanged() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.OldJob", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.NewJob", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }

        @Test
        @DisplayName("Should return true when methodName changed")
        void shouldReturnTrueWhenMethodNameChanged() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "oldRun", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "newRun", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }

        @Test
        @DisplayName("Should return true when cronExpression changed")
        void shouldReturnTrueWhenCronChanged() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", "cron: 0 0 * * * ?", null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", "cron: 0 */5 * * * ?", null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }

        @Test
        @DisplayName("Should return true when shedlockName changed")
        void shouldReturnTrueWhenShedlockNameChanged() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", null, "old-lock",
                SchedulerMetadata.SchedulerType.DISTRIBUTED);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", null, "new-lock",
                SchedulerMetadata.SchedulerType.DISTRIBUTED);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }

        @Test
        @DisplayName("Should return true when schedulerType changed")
        void shouldReturnTrueWhenSchedulerTypeChanged() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", null, null,
                SchedulerMetadata.SchedulerType.DISTRIBUTED);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }

        @Test
        @DisplayName("Should handle null cronExpression on both sides")
        void shouldHandleNullCronBothSides() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isFalse();
        }

        @Test
        @DisplayName("Should return true when cronExpression changes from null to non-null")
        void shouldReturnTrueWhenCronChangesFromNull() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", "cron: 0 0 * * * ?", null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }

        @Test
        @DisplayName("Should return true when cronExpression changes from non-null to null")
        void shouldReturnTrueWhenCronChangesToNull() {
            SchedulerRegistryEntry entry = createEntry(
                "com.example.Job", "run", "cron: 0 0 * * * ?", null,
                SchedulerMetadata.SchedulerType.LOCAL);
            SchedulerMetadata metadata = createMetadata(
                "com.example.Job", "run", null, null,
                SchedulerMetadata.SchedulerType.LOCAL);

            assertThat(entry.needsMetadataUpdate(metadata)).isTrue();
        }
    }

    @Nested
    @DisplayName("With methods (immutable copy)")
    class WithMethodsTest {

        @Test
        @DisplayName("withLastExecutionAt should return new entry with updated timestamp")
        void withLastExecutionAtShouldReturnNewEntry() {
            Instant executionTime = Instant.parse("2025-06-01T12:00:00Z");
            SchedulerRegistryEntry original = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .build();

            SchedulerRegistryEntry updated = original.withLastExecutionAt(executionTime);

            assertThat(updated.getLastExecutionAt()).isEqualTo(executionTime);
            assertThat(updated.getRegistryId()).isEqualTo("reg-1");
            assertThat(original.getLastExecutionAt()).isNull();
        }

        @Test
        @DisplayName("withLastDurationMs should return new entry with updated duration")
        void withLastDurationMsShouldReturnNewEntry() {
            SchedulerRegistryEntry original = SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .build();

            SchedulerRegistryEntry updated = original.withLastDurationMs(250L);

            assertThat(updated.getLastDurationMs()).isEqualTo(250L);
            assertThat(original.getLastDurationMs()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("Should build entry with all fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();

            SchedulerRegistryEntry entry = SchedulerRegistryEntry.builder()
                .registryId("reg-100")
                .schedulerName("MyJob_execute")
                .className("com.example.MyJob")
                .methodName("execute")
                .schedulerType(SchedulerMetadata.SchedulerType.DISTRIBUTED)
                .shedlockName("my-lock")
                .cronExpression("cron: 0 0 3 * * ?")
                .displayName("My Job")
                .enabled(false)
                .lastExecutionAt(now)
                .lastDurationMs(1500L)
                .build();

            assertThat(entry.getRegistryId()).isEqualTo("reg-100");
            assertThat(entry.getSchedulerName()).isEqualTo("MyJob_execute");
            assertThat(entry.getClassName()).isEqualTo("com.example.MyJob");
            assertThat(entry.getMethodName()).isEqualTo("execute");
            assertThat(entry.getSchedulerType()).isEqualTo(SchedulerMetadata.SchedulerType.DISTRIBUTED);
            assertThat(entry.getShedlockName()).isEqualTo("my-lock");
            assertThat(entry.getCronExpression()).isEqualTo("cron: 0 0 3 * * ?");
            assertThat(entry.getDisplayName()).isEqualTo("My Job");
            assertThat(entry.getEnabled()).isFalse();
            assertThat(entry.getLastExecutionAt()).isEqualTo(now);
            assertThat(entry.getLastDurationMs()).isEqualTo(1500L);
        }
    }
}
