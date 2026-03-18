package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuthorizationDeniedException.
 */
@DisplayName("AuthorizationDeniedException")
class AuthorizationDeniedExceptionTest {

    @Nested
    @DisplayName("constructor with resource and reason")
    class TwoArgConstructor {

        @Test
        @DisplayName("should include resource and reason in message")
        void shouldIncludeResourceAndReasonInMessage() {
            AuthorizationDeniedException ex = new AuthorizationDeniedException(
                    "stock-price", "Insufficient permissions");

            assertThat(ex.getMessage()).contains("stock-price");
            assertThat(ex.getMessage()).contains("Insufficient permissions");
            assertThat(ex.getMessage()).contains("Access denied to resource");
        }

        @Test
        @DisplayName("should use AUTHZ_ACCESS_DENIED error code")
        void shouldUseAccessDeniedErrorCode() {
            AuthorizationDeniedException ex = new AuthorizationDeniedException(
                    "test", "denied");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTHZ_ACCESS_DENIED);
        }

        @Test
        @DisplayName("should store DenialDetail with null params")
        void shouldStoreDenialDetailWithNullParams() {
            AuthorizationDeniedException ex = new AuthorizationDeniedException(
                    "stock-price", "No permission");

            assertThat(ex.getDetail()).isInstanceOf(AuthorizationDeniedException.DenialDetail.class);
            AuthorizationDeniedException.DenialDetail detail =
                    (AuthorizationDeniedException.DenialDetail) ex.getDetail();
            assertThat(detail.resource()).isEqualTo("stock-price");
            assertThat(detail.reason()).isEqualTo("No permission");
            assertThat(detail.params()).isNull();
        }
    }

    @Nested
    @DisplayName("constructor with resource, reason, and params")
    class ThreeArgConstructor {

        @Test
        @DisplayName("should store DenialDetail with params")
        void shouldStoreDenialDetailWithParams() {
            Map<String, Object> params = Map.of("symbol", "AAPL");
            AuthorizationDeniedException ex = new AuthorizationDeniedException(
                    "stock-price", "Not authorized for symbol", params);

            AuthorizationDeniedException.DenialDetail detail =
                    (AuthorizationDeniedException.DenialDetail) ex.getDetail();
            assertThat(detail.resource()).isEqualTo("stock-price");
            assertThat(detail.reason()).isEqualTo("Not authorized for symbol");
            assertThat(detail.params()).containsEntry("symbol", "AAPL");
        }
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        AuthorizationDeniedException ex = new AuthorizationDeniedException("res", "reason");

        assertThat(ex).isInstanceOf(StreamException.class);
    }
}
