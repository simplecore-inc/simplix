package dev.simplecore.simplix.scheduler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerExecutionContext - Execution context creation and utilities")
class SchedulerExecutionContextTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("Should build context with all fields")
        void shouldBuildWithAllFields() {
            Instant startTime = Instant.now();

            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .registryId("reg-123")
                .schedulerName("TestScheduler_execute")
                .shedlockName("test-lock")
                .startTime(startTime)
                .status(ExecutionStatus.RUNNING)
                .serviceName("api-server")
                .serverHost("host-01")
                .build();

            assertThat(context.getRegistryId()).isEqualTo("reg-123");
            assertThat(context.getSchedulerName()).isEqualTo("TestScheduler_execute");
            assertThat(context.getShedlockName()).isEqualTo("test-lock");
            assertThat(context.getStartTime()).isEqualTo(startTime);
            assertThat(context.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(context.getServiceName()).isEqualTo("api-server");
            assertThat(context.getServerHost()).isEqualTo("host-01");
        }

        @Test
        @DisplayName("Should build context with minimal fields")
        void shouldBuildWithMinimalFields() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("MyScheduler_run")
                .build();

            assertThat(context.getSchedulerName()).isEqualTo("MyScheduler_run");
            assertThat(context.getRegistryId()).isNull();
            assertThat(context.getShedlockName()).isNull();
            assertThat(context.getStartTime()).isNull();
            assertThat(context.getStatus()).isNull();
            assertThat(context.getServiceName()).isNull();
            assertThat(context.getServerHost()).isNull();
        }
    }

    @Nested
    @DisplayName("getDurationMs")
    class GetDurationMsTest {

        @Test
        @DisplayName("Should return zero when startTime is null")
        void shouldReturnZeroWhenStartTimeIsNull() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("test")
                .startTime(null)
                .build();

            assertThat(context.getDurationMs()).isZero();
        }

        @Test
        @DisplayName("Should return positive duration when startTime is in the past")
        void shouldReturnPositiveDurationWhenStartTimeInPast() {
            Instant pastTime = Instant.now().minusMillis(500);

            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("test")
                .startTime(pastTime)
                .build();

            assertThat(context.getDurationMs()).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("Should return non-negative duration when startTime is now")
        void shouldReturnNonNegativeDurationWhenStartTimeIsNow() {
            SchedulerExecutionContext context = SchedulerExecutionContext.builder()
                .schedulerName("test")
                .startTime(Instant.now())
                .build();

            assertThat(context.getDurationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("With methods (immutable copy)")
    class WithMethodsTest {

        @Test
        @DisplayName("withStatus should return new context with updated status")
        void withStatusShouldReturnNewContext() {
            SchedulerExecutionContext original = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .status(ExecutionStatus.RUNNING)
                .build();

            SchedulerExecutionContext updated = original.withStatus(ExecutionStatus.SUCCESS);

            assertThat(updated.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(updated.getRegistryId()).isEqualTo("reg-1");
            assertThat(updated.getSchedulerName()).isEqualTo("test");
            // Original should be unchanged
            assertThat(original.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        }

        @Test
        @DisplayName("withServiceName should return new context with updated service name")
        void withServiceNameShouldReturnNewContext() {
            SchedulerExecutionContext original = SchedulerExecutionContext.builder()
                .schedulerName("test")
                .serviceName("api-server")
                .build();

            SchedulerExecutionContext updated = original.withServiceName("scheduler-server");

            assertThat(updated.getServiceName()).isEqualTo("scheduler-server");
            assertThat(original.getServiceName()).isEqualTo("api-server");
        }

        @Test
        @DisplayName("withRegistryId should return new context with updated registry ID")
        void withRegistryIdShouldReturnNewContext() {
            SchedulerExecutionContext original = SchedulerExecutionContext.builder()
                .schedulerName("test")
                .registryId("old-id")
                .build();

            SchedulerExecutionContext updated = original.withRegistryId("new-id");

            assertThat(updated.getRegistryId()).isEqualTo("new-id");
            assertThat(original.getRegistryId()).isEqualTo("old-id");
        }
    }

    @Nested
    @DisplayName("Equality and immutability")
    class EqualityTest {

        @Test
        @DisplayName("Two contexts with same fields should be equal")
        void shouldBeEqualWithSameFields() {
            Instant startTime = Instant.parse("2025-01-01T00:00:00Z");

            SchedulerExecutionContext ctx1 = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .startTime(startTime)
                .status(ExecutionStatus.RUNNING)
                .build();

            SchedulerExecutionContext ctx2 = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .startTime(startTime)
                .status(ExecutionStatus.RUNNING)
                .build();

            assertThat(ctx1).isEqualTo(ctx2);
            assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode());
        }

        @Test
        @DisplayName("Two contexts with different fields should not be equal")
        void shouldNotBeEqualWithDifferentFields() {
            SchedulerExecutionContext ctx1 = SchedulerExecutionContext.builder()
                .registryId("reg-1")
                .schedulerName("test")
                .build();

            SchedulerExecutionContext ctx2 = SchedulerExecutionContext.builder()
                .registryId("reg-2")
                .schedulerName("test")
                .build();

            assertThat(ctx1).isNotEqualTo(ctx2);
        }
    }
}
