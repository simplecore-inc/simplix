package dev.simplecore.simplix.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXGeneralException")
class SimpliXGeneralExceptionTest {

    @Nested
    @DisplayName("Primary constructor with ErrorCode")
    class PrimaryConstructor {

        @Test
        @DisplayName("should create exception with ErrorCode and custom message")
        void shouldCreateWithCustomMessage() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Custom message", null);

            assertThat(ex.getMessage()).isEqualTo("Custom message");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_BAD_REQUEST);
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getDetail()).isNull();
            assertThat(ex.getPath()).isEmpty();
        }

        @Test
        @DisplayName("should use default message when message is null")
        void shouldUseDefaultMessage() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_NOT_FOUND, null, "detail-data");

            assertThat(ex.getMessage()).isEqualTo("Resource not found");
            assertThat(ex.getDetail()).isEqualTo("detail-data");
        }

        @Test
        @DisplayName("should create exception with cause")
        void shouldCreateWithCause() {
            RuntimeException cause = new RuntimeException("root cause");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.DB_DATABASE_ERROR, "DB error", cause, null);

            assertThat(ex.getMessage()).isEqualTo("DB error");
            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should use default message with cause when message is null")
        void shouldUseDefaultMessageWithCause() {
            RuntimeException cause = new RuntimeException("root cause");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.AUTH_TOKEN_EXPIRED, null, cause, null);

            assertThat(ex.getMessage()).isEqualTo("Token expired");
        }
    }

    @SuppressWarnings("deprecation")
    @Nested
    @DisplayName("Legacy constructors")
    class LegacyConstructors {

        @Test
        @DisplayName("should create with full legacy constructor")
        void shouldCreateWithFullLegacyConstructor() {
            RuntimeException cause = new RuntimeException("cause");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    "Error occurred", cause, "GEN_BAD_REQUEST",
                    HttpStatus.BAD_REQUEST, "some-detail", "/api/test");

            assertThat(ex.getMessage()).isEqualTo("Error occurred");
            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_BAD_REQUEST);
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getDetail()).isEqualTo("some-detail");
            assertThat(ex.getPath()).isEqualTo("/api/test");
        }

        @Test
        @DisplayName("should create with two-arg legacy constructor")
        void shouldCreateWithTwoArgConstructor() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    "Something failed", "GEN_NOT_FOUND");

            assertThat(ex.getMessage()).isEqualTo("Something failed");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_NOT_FOUND);
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should create with three-arg legacy constructor")
        void shouldCreateWithThreeArgConstructor() {
            RuntimeException cause = new RuntimeException("root");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    "Error", cause, "AUTH_TOKEN_EXPIRED");

            assertThat(ex.getMessage()).isEqualTo("Error");
            assertThat(ex.getCause()).isEqualTo(cause);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("should create with four-arg legacy constructor")
        void shouldCreateWithFourArgConstructor() {
            RuntimeException cause = new RuntimeException("root");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    "Custom error", cause, "VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);

            assertThat(ex.getMessage()).isEqualTo("Custom error");
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("should fallback to INTERNAL_SERVER_ERROR for unknown error type")
        void shouldFallbackForUnknownType() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    "Unknown", "NONEXISTENT_TYPE");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
        }
    }

    @SuppressWarnings("deprecation")
    @Nested
    @DisplayName("getErrorType")
    class GetErrorType {

        @Test
        @DisplayName("should return error code string")
        void shouldReturnErrorCodeString() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.VAL_VALIDATION_FAILED, "Validation error", null);

            assertThat(ex.getErrorType()).isEqualTo("VAL_VALIDATION_FAILED");
        }
    }
}
