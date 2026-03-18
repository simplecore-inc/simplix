package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionExpiredException.
 */
@DisplayName("SessionExpiredException")
class SessionExpiredExceptionTest {

    @Test
    @DisplayName("should include session ID in message")
    void shouldIncludeSessionIdInMessage() {
        SessionExpiredException ex = new SessionExpiredException("sess-expired-1");

        assertThat(ex.getMessage()).contains("sess-expired-1");
        assertThat(ex.getMessage()).contains("Stream session expired");
    }

    @Test
    @DisplayName("should use AUTH_SESSION_EXPIRED error code")
    void shouldUseSessionExpiredErrorCode() {
        SessionExpiredException ex = new SessionExpiredException("sess-1");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("should store session ID as detail")
    void shouldStoreSessionIdAsDetail() {
        SessionExpiredException ex = new SessionExpiredException("sess-detail");

        assertThat(ex.getDetail()).isEqualTo("sess-detail");
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        SessionExpiredException ex = new SessionExpiredException("sess-1");

        assertThat(ex).isInstanceOf(StreamException.class);
    }
}
