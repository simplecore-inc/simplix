package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailResult")
class EmailResultTest {

    @Nested
    @DisplayName("success factory method")
    class SuccessFactory {

        @Test
        @DisplayName("Should create successful result with message ID and provider type")
        void shouldCreateSuccessResult() {
            EmailResult result = EmailResult.success("msg-123", MailProviderType.SMTP);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getMessageId()).isEqualTo("msg-123");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.SMTP);
            assertThat(result.getTimestamp()).isNotNull();
            assertThat(result.getErrorMessage()).isNull();
            assertThat(result.getRetryCount()).isZero();
            assertThat(result.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("failure factory method")
    class FailureFactory {

        @Test
        @DisplayName("Should create failure result with error message and provider type")
        void shouldCreateFailureResult() {
            EmailResult result = EmailResult.failure("Connection refused", MailProviderType.AWS_SES);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("Connection refused");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.AWS_SES);
            assertThat(result.getTimestamp()).isNotNull();
            assertThat(result.isRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("retryableFailure factory method")
    class RetryableFailureFactory {

        @Test
        @DisplayName("Should create retryable failure with PENDING status")
        void shouldCreateRetryableFailure() {
            EmailResult result = EmailResult.retryableFailure(
                    "Throttled", "Throttling", MailProviderType.AWS_SES
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.PENDING);
            assertThat(result.getErrorMessage()).isEqualTo("Throttled");
            assertThat(result.getErrorCode()).isEqualTo("Throttling");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.AWS_SES);
            assertThat(result.isRetryable()).isTrue();
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("Should build EmailResult with all fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();
            EmailResult result = EmailResult.builder()
                    .success(true)
                    .status(EmailStatus.DELIVERED)
                    .messageId("msg-456")
                    .providerType(MailProviderType.SENDGRID)
                    .timestamp(now)
                    .retryCount(2)
                    .retryable(false)
                    .recipients(List.of("user@example.com"))
                    .build();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.DELIVERED);
            assertThat(result.getMessageId()).isEqualTo("msg-456");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.SENDGRID);
            assertThat(result.getTimestamp()).isEqualTo(now);
            assertThat(result.getRetryCount()).isEqualTo(2);
            assertThat(result.getRecipients()).containsExactly("user@example.com");
        }

        @Test
        @DisplayName("Should use default values for retryCount, retryable, and recipients")
        void shouldUseDefaultValues() {
            EmailResult result = EmailResult.builder().build();

            assertThat(result.getRetryCount()).isZero();
            assertThat(result.isRetryable()).isFalse();
            assertThat(result.getRecipients()).isNotNull().isEmpty();
        }
    }
}
