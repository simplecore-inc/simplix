package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BroadcastException.
 */
@DisplayName("BroadcastException")
class BroadcastExceptionTest {

    @Nested
    @DisplayName("constructor with message")
    class SingleArgConstructor {

        @Test
        @DisplayName("should include message with prefix")
        void shouldIncludeMessageWithPrefix() {
            BroadcastException ex = new BroadcastException("Redis unavailable");

            assertThat(ex.getMessage()).contains("Broadcast failed");
            assertThat(ex.getMessage()).contains("Redis unavailable");
        }

        @Test
        @DisplayName("should use GEN_INTERNAL_SERVER_ERROR error code")
        void shouldUseInternalServerErrorCode() {
            BroadcastException ex = new BroadcastException("error");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should have null detail")
        void shouldHaveNullDetail() {
            BroadcastException ex = new BroadcastException("error");

            assertThat(ex.getDetail()).isNull();
        }
    }

    @Nested
    @DisplayName("constructor with message and cause")
    class TwoArgConstructor {

        @Test
        @DisplayName("should include cause")
        void shouldIncludeCause() {
            RuntimeException cause = new RuntimeException("connection refused");
            BroadcastException ex = new BroadcastException("Send failed", cause);

            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getMessage()).contains("Send failed");
        }
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        BroadcastException ex = new BroadcastException("test");

        assertThat(ex).isInstanceOf(StreamException.class);
    }
}
