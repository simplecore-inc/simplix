package dev.simplecore.simplix.auth.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenValidationException")
class TokenValidationExceptionTest {

    @Test
    @DisplayName("should create with default error code AUTH_TOKEN_INVALID")
    void shouldCreateWithDefaultErrorCode() {
        TokenValidationException exception = new TokenValidationException(
                "Token is invalid", "Detailed reason");

        assertThat(exception.getMessage()).isEqualTo("Token is invalid");
        assertThat(exception.getDetail()).isEqualTo("Detailed reason");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("should create with custom error code")
    void shouldCreateWithCustomErrorCode() {
        TokenValidationException exception = new TokenValidationException(
                ErrorCode.AUTH_TOKEN_EXPIRED, "Token expired", "Token has passed its TTL");

        assertThat(exception.getMessage()).isEqualTo("Token expired");
        assertThat(exception.getDetail()).isEqualTo("Token has passed its TTL");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("should create with cause")
    void shouldCreateWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        TokenValidationException exception = new TokenValidationException(
                "Parsing failed", cause, "Cannot parse JWE token");

        assertThat(exception.getMessage()).isEqualTo("Parsing failed");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getDetail()).isEqualTo("Cannot parse JWE token");
    }
}
