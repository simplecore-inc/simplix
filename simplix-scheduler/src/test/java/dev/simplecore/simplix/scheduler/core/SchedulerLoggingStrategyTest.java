package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerLoggingStrategy - Default method behavior")
class SchedulerLoggingStrategyTest {

    @Nested
    @DisplayName("Default methods")
    class DefaultMethodsTest {

        @Test
        @DisplayName("initialize should complete without error by default")
        void initializeShouldBeNoOp() {
            TestStrategy strategy = new TestStrategy();

            // Should not throw
            strategy.initialize();
        }

        @Test
        @DisplayName("shutdown should complete without error by default")
        void shutdownShouldBeNoOp() {
            TestStrategy strategy = new TestStrategy();

            // Should not throw
            strategy.shutdown();
        }

        @Test
        @DisplayName("isReady should return true by default")
        void isReadyShouldReturnTrue() {
            TestStrategy strategy = new TestStrategy();

            assertThat(strategy.isReady()).isTrue();
        }

        @Test
        @DisplayName("clearCache should complete without error by default")
        void clearCacheShouldBeNoOp() {
            TestStrategy strategy = new TestStrategy();

            // Should not throw
            strategy.clearCache();
        }
    }

    // Minimal implementation to test default methods
    private static class TestStrategy implements SchedulerLoggingStrategy {

        @Override
        public String getName() {
            return "Test Strategy";
        }

        @Override
        public boolean supports(String mode) {
            return "test".equals(mode);
        }

        @Override
        public SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata) {
            return SchedulerRegistryEntry.fromMetadata(metadata);
        }

        @Override
        public SchedulerExecutionContext createExecutionContext(
            SchedulerRegistryEntry registry, String serviceName, String serverHost) {
            return SchedulerExecutionContext.builder().build();
        }

        @Override
        public void saveExecutionResult(
            SchedulerExecutionContext context, SchedulerExecutionResult result) {
        }
    }
}
