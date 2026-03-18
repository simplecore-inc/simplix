package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerRegistryProvider - Default method behavior")
class SchedulerRegistryProviderTest {

    @Nested
    @DisplayName("existsBySchedulerName default implementation")
    class ExistsBySchedulerNameTest {

        @Test
        @DisplayName("Should return true when findBySchedulerName returns a present Optional")
        void shouldReturnTrueWhenPresent() {
            TestRegistryProvider provider = new TestRegistryProvider(true);

            assertThat(provider.existsBySchedulerName("test-scheduler")).isTrue();
        }

        @Test
        @DisplayName("Should return false when findBySchedulerName returns an empty Optional")
        void shouldReturnFalseWhenEmpty() {
            TestRegistryProvider provider = new TestRegistryProvider(false);

            assertThat(provider.existsBySchedulerName("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("updateMetadata default implementation")
    class UpdateMetadataTest {

        @Test
        @DisplayName("Should return 0 by default")
        void shouldReturnZeroByDefault() {
            TestRegistryProvider provider = new TestRegistryProvider(false);

            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("test")
                .className("com.example.Test")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            int result = provider.updateMetadata("reg-1", metadata);

            assertThat(result).isZero();
        }
    }

    // Minimal implementation to test default methods
    private static class TestRegistryProvider implements SchedulerRegistryProvider<String> {

        private final boolean entityExists;

        TestRegistryProvider(boolean entityExists) {
            this.entityExists = entityExists;
        }

        @Override
        public Optional<String> findBySchedulerName(String schedulerName) {
            return entityExists ? Optional.of("entity") : Optional.empty();
        }

        @Override
        public String save(SchedulerRegistryEntry entry) {
            return "saved-entity";
        }

        @Override
        public int updateLastExecution(String registryId, Instant executionAt, Long durationMs) {
            return 1;
        }

        @Override
        public SchedulerRegistryEntry toRegistryEntry(String entity) {
            return SchedulerRegistryEntry.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .build();
        }

        @Override
        public String getRegistryId(String entity) {
            return "reg-1";
        }
    }
}
