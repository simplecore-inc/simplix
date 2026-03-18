package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamException.
 */
@DisplayName("StreamException")
class StreamExceptionTest {

    @Nested
    @DisplayName("constructor with errorCode, message")
    class TwoArgConstructor {

        @Test
        @DisplayName("should create exception with error code and message")
        void shouldCreateExceptionWithErrorCodeAndMessage() {
            StreamException ex = new StreamException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Something went wrong");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
            assertThat(ex.getMessage()).isEqualTo("Something went wrong");
            assertThat(ex.getDetail()).isNull();
            assertThat(ex.getCause()).isNull();
        }
    }

    @Nested
    @DisplayName("constructor with errorCode, message, detail")
    class ThreeArgConstructor {

        @Test
        @DisplayName("should create exception with error code, message, and detail")
        void shouldCreateExceptionWithDetail() {
            StreamException ex = new StreamException(
                    ErrorCode.GEN_NOT_FOUND, "Not found", "detail-info");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_NOT_FOUND);
            assertThat(ex.getMessage()).isEqualTo("Not found");
            assertThat(ex.getDetail()).isEqualTo("detail-info");
        }
    }

    @Nested
    @DisplayName("constructor with errorCode, message, cause")
    class ConstructorWithCause {

        @Test
        @DisplayName("should create exception with cause")
        void shouldCreateExceptionWithCause() {
            RuntimeException cause = new RuntimeException("root cause");
            StreamException ex = new StreamException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Error occurred", cause);

            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getMessage()).isEqualTo("Error occurred");
        }
    }

    @Nested
    @DisplayName("constructor with errorCode, message, cause, detail")
    class FullConstructor {

        @Test
        @DisplayName("should create exception with all fields")
        void shouldCreateExceptionWithAllFields() {
            RuntimeException cause = new RuntimeException("root");
            StreamException ex = new StreamException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Full error", cause, "full-detail");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
            assertThat(ex.getMessage()).isEqualTo("Full error");
            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getDetail()).isEqualTo("full-detail");
        }
    }

    @Nested
    @DisplayName("inheritance")
    class Inheritance {

        @Test
        @DisplayName("should be a RuntimeException")
        void shouldBeRuntimeException() {
            StreamException ex = new StreamException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "test");

            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
