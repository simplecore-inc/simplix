package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SchedulerException.
 */
@DisplayName("SchedulerException")
class SchedulerExceptionTest {

    private final SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

    @Nested
    @DisplayName("constructor with key and message")
    class TwoArgConstructor {

        @Test
        @DisplayName("should include key and message in exception message")
        void shouldIncludeKeyAndMessageInExceptionMessage() {
            SchedulerException ex = new SchedulerException(key, "Execution failed");

            assertThat(ex.getMessage()).contains(key.toKeyString());
            assertThat(ex.getMessage()).contains("Execution failed");
            assertThat(ex.getMessage()).contains("Scheduler error");
        }

        @Test
        @DisplayName("should use GEN_INTERNAL_SERVER_ERROR error code")
        void shouldUseInternalServerErrorCode() {
            SchedulerException ex = new SchedulerException(key, "error");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should store key string as detail")
        void shouldStoreKeyStringAsDetail() {
            SchedulerException ex = new SchedulerException(key, "error");

            assertThat(ex.getDetail()).isEqualTo(key.toKeyString());
        }
    }

    @Nested
    @DisplayName("constructor with key, message, and cause")
    class ThreeArgConstructor {

        @Test
        @DisplayName("should include cause")
        void shouldIncludeCause() {
            RuntimeException cause = new RuntimeException("underlying error");
            SchedulerException ex = new SchedulerException(key, "Failed", cause);

            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getMessage()).contains(key.toKeyString());
            assertThat(ex.getMessage()).contains("Failed");
        }
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        SchedulerException ex = new SchedulerException(key, "test");

        assertThat(ex).isInstanceOf(StreamException.class);
    }
}
