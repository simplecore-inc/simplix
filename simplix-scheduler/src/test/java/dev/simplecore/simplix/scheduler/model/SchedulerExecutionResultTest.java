package dev.simplecore.simplix.scheduler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerExecutionResult - Execution result creation and queries")
class SchedulerExecutionResultTest {

    @Nested
    @DisplayName("success(durationMs)")
    class SuccessFactory {

        @Test
        @DisplayName("Should create result with SUCCESS status")
        void shouldCreateSuccessStatus() {
            SchedulerExecutionResult result = SchedulerExecutionResult.success(150L);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should set duration in milliseconds")
        void shouldSetDuration() {
            SchedulerExecutionResult result = SchedulerExecutionResult.success(250L);

            assertThat(result.getDurationMs()).isEqualTo(250L);
        }

        @Test
        @DisplayName("Should set end time close to now")
        void shouldSetEndTime() {
            Instant before = Instant.now();
            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L);
            Instant after = Instant.now();

            assertThat(result.getEndTime()).isAfterOrEqualTo(before);
            assertThat(result.getEndTime()).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("Should have null error message")
        void shouldHaveNullErrorMessage() {
            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L);

            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Should have null items processed")
        void shouldHaveNullItemsProcessed() {
            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L);

            assertThat(result.getItemsProcessed()).isNull();
        }
    }

    @Nested
    @DisplayName("success(durationMs, itemsProcessed)")
    class SuccessWithItemsFactory {

        @Test
        @DisplayName("Should create result with SUCCESS status and items count")
        void shouldCreateSuccessWithItems() {
            SchedulerExecutionResult result = SchedulerExecutionResult.success(200L, 42);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(result.getDurationMs()).isEqualTo(200L);
            assertThat(result.getItemsProcessed()).isEqualTo(42);
        }

        @Test
        @DisplayName("Should set end time close to now")
        void shouldSetEndTime() {
            Instant before = Instant.now();
            SchedulerExecutionResult result = SchedulerExecutionResult.success(100L, 10);
            Instant after = Instant.now();

            assertThat(result.getEndTime()).isAfterOrEqualTo(before);
            assertThat(result.getEndTime()).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("Should handle zero items processed")
        void shouldHandleZeroItems() {
            SchedulerExecutionResult result = SchedulerExecutionResult.success(50L, 0);

            assertThat(result.getItemsProcessed()).isZero();
        }
    }

    @Nested
    @DisplayName("failure(durationMs, errorMessage)")
    class FailureFactory {

        @Test
        @DisplayName("Should create result with FAILED status")
        void shouldCreateFailedStatus() {
            SchedulerExecutionResult result = SchedulerExecutionResult.failure(300L, "Connection timeout");

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        }

        @Test
        @DisplayName("Should set error message")
        void shouldSetErrorMessage() {
            SchedulerExecutionResult result = SchedulerExecutionResult.failure(300L, "Connection timeout");

            assertThat(result.getErrorMessage()).isEqualTo("Connection timeout");
        }

        @Test
        @DisplayName("Should set duration in milliseconds")
        void shouldSetDuration() {
            SchedulerExecutionResult result = SchedulerExecutionResult.failure(500L, "Error");

            assertThat(result.getDurationMs()).isEqualTo(500L);
        }

        @Test
        @DisplayName("Should set end time close to now")
        void shouldSetEndTime() {
            Instant before = Instant.now();
            SchedulerExecutionResult result = SchedulerExecutionResult.failure(100L, "Error");
            Instant after = Instant.now();

            assertThat(result.getEndTime()).isAfterOrEqualTo(before);
            assertThat(result.getEndTime()).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("Should handle null error message")
        void shouldHandleNullErrorMessage() {
            SchedulerExecutionResult result = SchedulerExecutionResult.failure(100L, null);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(result.getErrorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("timeout(durationMs)")
    class TimeoutFactory {

        @Test
        @DisplayName("Should create result with TIMEOUT status")
        void shouldCreateTimeoutStatus() {
            SchedulerExecutionResult result = SchedulerExecutionResult.timeout(60000L);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TIMEOUT);
        }

        @Test
        @DisplayName("Should set duration in milliseconds")
        void shouldSetDuration() {
            SchedulerExecutionResult result = SchedulerExecutionResult.timeout(60000L);

            assertThat(result.getDurationMs()).isEqualTo(60000L);
        }

        @Test
        @DisplayName("Should have null error message")
        void shouldHaveNullErrorMessage() {
            SchedulerExecutionResult result = SchedulerExecutionResult.timeout(60000L);

            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Should set end time close to now")
        void shouldSetEndTime() {
            Instant before = Instant.now();
            SchedulerExecutionResult result = SchedulerExecutionResult.timeout(60000L);
            Instant after = Instant.now();

            assertThat(result.getEndTime()).isAfterOrEqualTo(before);
            assertThat(result.getEndTime()).isBeforeOrEqualTo(after);
        }
    }

    @Nested
    @DisplayName("Status check methods")
    class StatusChecks {

        @Test
        @DisplayName("isSuccess should return true only for SUCCESS status")
        void isSuccessShouldReturnTrueForSuccessOnly() {
            assertThat(SchedulerExecutionResult.success(100L).isSuccess()).isTrue();
            assertThat(SchedulerExecutionResult.failure(100L, "err").isSuccess()).isFalse();
            assertThat(SchedulerExecutionResult.timeout(100L).isSuccess()).isFalse();
        }

        @Test
        @DisplayName("isFailed should return true only for FAILED status")
        void isFailedShouldReturnTrueForFailedOnly() {
            assertThat(SchedulerExecutionResult.failure(100L, "err").isFailed()).isTrue();
            assertThat(SchedulerExecutionResult.success(100L).isFailed()).isFalse();
            assertThat(SchedulerExecutionResult.timeout(100L).isFailed()).isFalse();
        }

        @Test
        @DisplayName("isTimeout should return true only for TIMEOUT status")
        void isTimeoutShouldReturnTrueForTimeoutOnly() {
            assertThat(SchedulerExecutionResult.timeout(100L).isTimeout()).isTrue();
            assertThat(SchedulerExecutionResult.success(100L).isTimeout()).isFalse();
            assertThat(SchedulerExecutionResult.failure(100L, "err").isTimeout()).isFalse();
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("Should build result with all fields via builder")
        void shouldBuildWithAllFields() {
            Instant endTime = Instant.now();

            SchedulerExecutionResult result = SchedulerExecutionResult.builder()
                .status(ExecutionStatus.RUNNING)
                .endTime(endTime)
                .durationMs(999L)
                .errorMessage("some error")
                .itemsProcessed(50)
                .build();

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(result.getEndTime()).isEqualTo(endTime);
            assertThat(result.getDurationMs()).isEqualTo(999L);
            assertThat(result.getErrorMessage()).isEqualTo("some error");
            assertThat(result.getItemsProcessed()).isEqualTo(50);
        }
    }
}
