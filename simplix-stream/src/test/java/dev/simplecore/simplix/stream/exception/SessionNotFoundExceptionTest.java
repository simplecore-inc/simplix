package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionNotFoundException.
 */
@DisplayName("SessionNotFoundException")
class SessionNotFoundExceptionTest {

    @Test
    @DisplayName("should include session ID in message")
    void shouldIncludeSessionIdInMessage() {
        SessionNotFoundException ex = new SessionNotFoundException("sess-abc-123");

        assertThat(ex.getMessage()).contains("sess-abc-123");
        assertThat(ex.getMessage()).contains("Stream session not found");
    }

    @Test
    @DisplayName("should use GEN_NOT_FOUND error code")
    void shouldUseNotFoundErrorCode() {
        SessionNotFoundException ex = new SessionNotFoundException("sess-1");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_NOT_FOUND);
    }

    @Test
    @DisplayName("should store session ID as detail")
    void shouldStoreSessionIdAsDetail() {
        SessionNotFoundException ex = new SessionNotFoundException("sess-detail");

        assertThat(ex.getDetail()).isEqualTo("sess-detail");
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        SessionNotFoundException ex = new SessionNotFoundException("sess-1");

        assertThat(ex).isInstanceOf(StreamException.class);
    }

    @Test
    @DisplayName("should be a SimpliXGeneralException for exception handler compatibility")
    void shouldBeSimplixGeneralException() {
        SessionNotFoundException ex = new SessionNotFoundException("sess-1");

        assertThat(ex).isInstanceOf(SimpliXGeneralException.class);
    }

    @Test
    @DisplayName("should map to HTTP 404 status code for proper log level handling")
    void shouldMapToHttp404Status() {
        SessionNotFoundException ex = new SessionNotFoundException("sess-expired");

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getStatusCode().value()).isEqualTo(404);
        assertThat(ex.getErrorCode().isClientError()).isTrue();
        assertThat(ex.getErrorCode().isServerError()).isFalse();
    }
}
