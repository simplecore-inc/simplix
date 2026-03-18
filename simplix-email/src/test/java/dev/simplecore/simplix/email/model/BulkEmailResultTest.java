package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BulkEmailResult")
class BulkEmailResultTest {

    @Nested
    @DisplayName("isAllSuccess")
    class IsAllSuccess {

        @Test
        @DisplayName("Should return true when no failures and no pending")
        void shouldReturnTrueWhenAllSuccessful() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(3)
                    .successCount(3)
                    .failureCount(0)
                    .pendingCount(0)
                    .build();

            assertThat(result.isAllSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return false when there are failures")
        void shouldReturnFalseWhenFailures() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(3)
                    .successCount(2)
                    .failureCount(1)
                    .pendingCount(0)
                    .build();

            assertThat(result.isAllSuccess()).isFalse();
        }

        @Test
        @DisplayName("Should return false when there are pending emails")
        void shouldReturnFalseWhenPending() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(3)
                    .successCount(2)
                    .failureCount(0)
                    .pendingCount(1)
                    .build();

            assertThat(result.isAllSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasAnySuccess")
    class HasAnySuccess {

        @Test
        @DisplayName("Should return true when at least one success")
        void shouldReturnTrueWithOneSuccess() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(3)
                    .successCount(1)
                    .failureCount(2)
                    .build();

            assertThat(result.hasAnySuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return false when no successes")
        void shouldReturnFalseWhenNoSuccess() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(3)
                    .successCount(0)
                    .failureCount(3)
                    .build();

            assertThat(result.hasAnySuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("getSuccessRate")
    class GetSuccessRate {

        @Test
        @DisplayName("Should return 100.0 when all successful")
        void shouldReturn100WhenAllSuccessful() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(5)
                    .successCount(5)
                    .build();

            assertThat(result.getSuccessRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Should return 0.0 when totalCount is 0")
        void shouldReturn0WhenTotalCountIsZero() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(0)
                    .successCount(0)
                    .build();

            assertThat(result.getSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return 50.0 when half successful")
        void shouldReturnCorrectRate() {
            BulkEmailResult result = BulkEmailResult.builder()
                    .totalCount(4)
                    .successCount(2)
                    .build();

            assertThat(result.getSuccessRate()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("RecipientResult")
    class RecipientResultTest {

        @Test
        @DisplayName("success should create a successful recipient result")
        void successShouldCreateSuccessResult() {
            BulkEmailResult.RecipientResult result = BulkEmailResult.RecipientResult.success(
                    "user@example.com", "msg-123"
            );

            assertThat(result.getEmail()).isEqualTo("user@example.com");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessageId()).isEqualTo("msg-123");
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("failure should create a failed recipient result")
        void failureShouldCreateFailedResult() {
            BulkEmailResult.RecipientResult result = BulkEmailResult.RecipientResult.failure(
                    "user@example.com", "Invalid address"
            );

            assertThat(result.getEmail()).isEqualTo("user@example.com");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Invalid address");
            assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
            assertThat(result.getMessageId()).isNull();
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("Should build complete BulkEmailResult")
        void shouldBuildComplete() {
            Instant start = Instant.now();
            Instant end = start.plusSeconds(5);

            BulkEmailResult result = BulkEmailResult.builder()
                    .batchId("batch-1")
                    .totalCount(2)
                    .successCount(1)
                    .failureCount(1)
                    .pendingCount(0)
                    .results(List.of(
                            BulkEmailResult.RecipientResult.success("a@example.com", "m1"),
                            BulkEmailResult.RecipientResult.failure("b@example.com", "error")
                    ))
                    .startTime(start)
                    .endTime(end)
                    .providerType(MailProviderType.SMTP)
                    .build();

            assertThat(result.getBatchId()).isEqualTo("batch-1");
            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.getPendingCount()).isZero();
            assertThat(result.getResults()).hasSize(2);
            assertThat(result.getStartTime()).isEqualTo(start);
            assertThat(result.getEndTime()).isEqualTo(end);
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.SMTP);
        }

        @Test
        @DisplayName("Should default results to empty list")
        void shouldDefaultResultsToEmptyList() {
            BulkEmailResult result = BulkEmailResult.builder().build();

            assertThat(result.getResults()).isNotNull().isEmpty();
        }
    }
}
