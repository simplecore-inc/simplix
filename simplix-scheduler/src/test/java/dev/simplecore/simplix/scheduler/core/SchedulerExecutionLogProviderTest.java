package dev.simplecore.simplix.scheduler.core;

import dev.simplecore.simplix.scheduler.model.ExecutionStatus;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerExecutionLogProvider - Interface contract verification")
class SchedulerExecutionLogProviderTest {

    @Nested
    @DisplayName("Interface contract")
    class InterfaceContractTest {

        @Test
        @DisplayName("Implementation should create entity from context")
        void shouldCreateFromContext() {
            TestLogProvider provider = new TestLogProvider();

            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("TestJob_run")
                .startTime(Instant.now())
                .status(ExecutionStatus.RUNNING)
                .serviceName("api-server")
                .serverHost("host-01")
                .build();

            String entity = provider.createFromContext(context);

            assertThat(entity).isNotNull();
            assertThat(entity).contains("TestJob_run");
        }

        @Test
        @DisplayName("Implementation should apply result to entity")
        void shouldApplyResult() {
            TestLogProvider provider = new TestLogProvider();

            String entity = "execution-log:TestJob_run";
            SchedulerExecutionResult result = SchedulerExecutionResult.success(200L);

            // Should not throw
            provider.applyResult(entity, result);
        }

        @Test
        @DisplayName("Implementation should save entity and return it")
        void shouldSaveEntity() {
            TestLogProvider provider = new TestLogProvider();

            String entity = "execution-log:TestJob_run";
            String saved = provider.save(entity);

            assertThat(saved).isEqualTo(entity);
        }
    }

    // Minimal implementation for testing
    private static class TestLogProvider implements SchedulerExecutionLogProvider<String> {

        @Override
        public String createFromContext(SchedulerExecutionContext context) {
            return "execution-log:" + context.getSchedulerName();
        }

        @Override
        public void applyResult(String entity, SchedulerExecutionResult result) {
            // Apply result (no-op in test)
        }

        @Override
        public String save(String entity) {
            return entity;
        }
    }
}
