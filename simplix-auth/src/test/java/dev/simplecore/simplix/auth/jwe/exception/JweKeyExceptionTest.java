package dev.simplecore.simplix.auth.jwe.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JweKeyException")
class JweKeyExceptionTest {

    @Test
    @DisplayName("should create with message only")
    void shouldCreateWithMessageOnly() {
        JweKeyException exception = new JweKeyException("Key not found");

        assertThat(exception.getMessage()).isEqualTo("Key not found");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("should create with message and cause")
    void shouldCreateWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("underlying error");
        JweKeyException exception = new JweKeyException("Key load failed", cause);

        assertThat(exception.getMessage()).isEqualTo("Key load failed");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should create with custom error code")
    void shouldCreateWithCustomErrorCode() {
        JweKeyException exception = new JweKeyException(
                ErrorCode.AUTH_TOKEN_INVALID, "Invalid key format");

        assertThat(exception.getMessage()).isEqualTo("Invalid key format");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("should create with custom error code and cause")
    void shouldCreateWithCustomErrorCodeAndCause() {
        RuntimeException cause = new RuntimeException("parse error");
        JweKeyException exception = new JweKeyException(
                ErrorCode.AUTH_TOKEN_INVALID, "Key parse failed", cause);

        assertThat(exception.getMessage()).isEqualTo("Key parse failed");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }
}
