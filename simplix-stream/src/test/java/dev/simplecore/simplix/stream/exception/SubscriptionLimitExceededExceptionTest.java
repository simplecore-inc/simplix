package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SubscriptionLimitExceededException.
 */
@DisplayName("SubscriptionLimitExceededException")
class SubscriptionLimitExceededExceptionTest {

    @Test
    @DisplayName("should include current and max in message")
    void shouldIncludeCurrentAndMaxInMessage() {
        SubscriptionLimitExceededException ex = new SubscriptionLimitExceededException(15, 20);

        assertThat(ex.getMessage()).contains("15/20");
        assertThat(ex.getMessage()).contains("Subscription limit exceeded");
    }

    @Test
    @DisplayName("should use BIZ_QUOTA_EXCEEDED error code")
    void shouldUseQuotaExceededErrorCode() {
        SubscriptionLimitExceededException ex = new SubscriptionLimitExceededException(10, 10);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BIZ_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("should store LimitDetail as detail")
    void shouldStoreLimitDetailAsDetail() {
        SubscriptionLimitExceededException ex = new SubscriptionLimitExceededException(5, 10);

        assertThat(ex.getDetail()).isInstanceOf(SubscriptionLimitExceededException.LimitDetail.class);
        SubscriptionLimitExceededException.LimitDetail detail =
                (SubscriptionLimitExceededException.LimitDetail) ex.getDetail();
        assertThat(detail.current()).isEqualTo(5);
        assertThat(detail.max()).isEqualTo(10);
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        SubscriptionLimitExceededException ex = new SubscriptionLimitExceededException(1, 1);

        assertThat(ex).isInstanceOf(StreamException.class);
    }
}
