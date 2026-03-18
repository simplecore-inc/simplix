package dev.simplecore.simplix.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
@DisplayName("ErrorCode")
class ErrorCodeTest {

    @Nested
    @DisplayName("fromCode")
    class FromCode {

        @Test
        @DisplayName("should return matching ErrorCode for valid code")
        void shouldReturnMatchingErrorCode() {
            ErrorCode result = ErrorCode.fromCode("GEN_BAD_REQUEST");
            assertThat(result).isEqualTo(ErrorCode.GEN_BAD_REQUEST);
        }

        @Test
        @DisplayName("should return GEN_INTERNAL_SERVER_ERROR for unknown code")
        void shouldReturnDefaultForUnknownCode() {
            ErrorCode result = ErrorCode.fromCode("UNKNOWN_CODE");
            assertThat(result).isEqualTo(ErrorCode.GEN_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should find legacy error codes")
        void shouldFindLegacyCodes() {
            assertThat(ErrorCode.fromCode("INTERNAL_SERVER_ERROR")).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
            assertThat(ErrorCode.fromCode("AUTHENTICATION_FAILED")).isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
            assertThat(ErrorCode.fromCode("NOT_FOUND")).isEqualTo(ErrorCode.NOT_FOUND);
            assertThat(ErrorCode.fromCode("TOKEN_EXPIRED")).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        }
    }

    @Nested
    @DisplayName("isAuthenticationError")
    class IsAuthenticationError {

        @Test
        @DisplayName("should return true for authentication errors")
        void shouldReturnTrueForAuthErrors() {
            assertThat(ErrorCode.AUTH_AUTHENTICATION_REQUIRED.isAuthenticationError()).isTrue();
            assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.isAuthenticationError()).isTrue();
            assertThat(ErrorCode.AUTH_TOKEN_EXPIRED.isAuthenticationError()).isTrue();
            assertThat(ErrorCode.AUTH_TOKEN_INVALID.isAuthenticationError()).isTrue();
            assertThat(ErrorCode.AUTH_SESSION_EXPIRED.isAuthenticationError()).isTrue();
        }

        @Test
        @DisplayName("should return false for non-authentication errors")
        void shouldReturnFalseForNonAuthErrors() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.isAuthenticationError()).isFalse();
            assertThat(ErrorCode.AUTHZ_ACCESS_DENIED.isAuthenticationError()).isFalse();
        }
    }

    @Nested
    @DisplayName("isAuthorizationError")
    class IsAuthorizationError {

        @Test
        @DisplayName("should return true for authorization errors")
        void shouldReturnTrueForAuthzErrors() {
            assertThat(ErrorCode.AUTHZ_INSUFFICIENT_PERMISSIONS.isAuthorizationError()).isTrue();
            assertThat(ErrorCode.AUTHZ_ACCESS_DENIED.isAuthorizationError()).isTrue();
            assertThat(ErrorCode.AUTHZ_RESOURCE_FORBIDDEN.isAuthorizationError()).isTrue();
        }

        @Test
        @DisplayName("should return false for non-authorization errors")
        void shouldReturnFalseForNonAuthzErrors() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.isAuthorizationError()).isFalse();
            assertThat(ErrorCode.AUTH_TOKEN_EXPIRED.isAuthorizationError()).isFalse();
        }
    }

    @Nested
    @DisplayName("isClientError")
    class IsClientError {

        @Test
        @DisplayName("should return true for 4xx errors")
        void shouldReturnTrueFor4xxErrors() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.isClientError()).isTrue();
            assertThat(ErrorCode.GEN_NOT_FOUND.isClientError()).isTrue();
            assertThat(ErrorCode.AUTH_AUTHENTICATION_REQUIRED.isClientError()).isTrue();
            assertThat(ErrorCode.AUTHZ_ACCESS_DENIED.isClientError()).isTrue();
            assertThat(ErrorCode.VAL_VALIDATION_FAILED.isClientError()).isTrue();
        }

        @Test
        @DisplayName("should return false for 5xx errors")
        void shouldReturnFalseFor5xxErrors() {
            assertThat(ErrorCode.GEN_INTERNAL_SERVER_ERROR.isClientError()).isFalse();
            assertThat(ErrorCode.DB_DATABASE_ERROR.isClientError()).isFalse();
        }
    }

    @Nested
    @DisplayName("isServerError")
    class IsServerError {

        @Test
        @DisplayName("should return true for 5xx errors")
        void shouldReturnTrueFor5xxErrors() {
            assertThat(ErrorCode.GEN_INTERNAL_SERVER_ERROR.isServerError()).isTrue();
            assertThat(ErrorCode.DB_DATABASE_ERROR.isServerError()).isTrue();
            assertThat(ErrorCode.DB_TRANSACTION_FAILED.isServerError()).isTrue();
            assertThat(ErrorCode.EXT_SERVICE_ERROR.isServerError()).isTrue();
            assertThat(ErrorCode.EXT_SERVICE_UNAVAILABLE.isServerError()).isTrue();
        }

        @Test
        @DisplayName("should return false for 4xx errors")
        void shouldReturnFalseFor4xxErrors() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.isServerError()).isFalse();
            assertThat(ErrorCode.GEN_NOT_FOUND.isServerError()).isFalse();
        }
    }

    @Nested
    @DisplayName("getters")
    class Getters {

        @Test
        @DisplayName("should return correct code")
        void shouldReturnCorrectCode() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.getCode()).isEqualTo("GEN_BAD_REQUEST");
        }

        @Test
        @DisplayName("should return correct default message")
        void shouldReturnCorrectDefaultMessage() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.getDefaultMessage()).isEqualTo("Bad request");
        }

        @Test
        @DisplayName("should return correct http status")
        void shouldReturnCorrectHttpStatus() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ErrorCode.GEN_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return correct category")
        void shouldReturnCorrectCategory() {
            assertThat(ErrorCode.GEN_BAD_REQUEST.getCategory()).isEqualTo(ErrorCode.ErrorCategory.GENERAL);
            assertThat(ErrorCode.AUTH_TOKEN_EXPIRED.getCategory()).isEqualTo(ErrorCode.ErrorCategory.AUTHENTICATION);
            assertThat(ErrorCode.AUTHZ_ACCESS_DENIED.getCategory()).isEqualTo(ErrorCode.ErrorCategory.AUTHORIZATION);
            assertThat(ErrorCode.VAL_VALIDATION_FAILED.getCategory()).isEqualTo(ErrorCode.ErrorCategory.VALIDATION);
            assertThat(ErrorCode.SEARCH_INVALID_PARAMETER.getCategory()).isEqualTo(ErrorCode.ErrorCategory.SEARCH);
            assertThat(ErrorCode.BIZ_BUSINESS_LOGIC_ERROR.getCategory()).isEqualTo(ErrorCode.ErrorCategory.BUSINESS);
            assertThat(ErrorCode.DB_DATABASE_ERROR.getCategory()).isEqualTo(ErrorCode.ErrorCategory.DATABASE);
            assertThat(ErrorCode.EXT_SERVICE_ERROR.getCategory()).isEqualTo(ErrorCode.ErrorCategory.EXTERNAL);
        }
    }

    @Nested
    @DisplayName("ErrorCategory")
    class ErrorCategoryTests {

        @Test
        @DisplayName("should have all expected categories")
        void shouldHaveAllCategories() {
            ErrorCode.ErrorCategory[] categories = ErrorCode.ErrorCategory.values();
            assertThat(categories).hasSize(8);
            assertThat(ErrorCode.ErrorCategory.valueOf("GENERAL")).isEqualTo(ErrorCode.ErrorCategory.GENERAL);
            assertThat(ErrorCode.ErrorCategory.valueOf("AUTHENTICATION")).isEqualTo(ErrorCode.ErrorCategory.AUTHENTICATION);
            assertThat(ErrorCode.ErrorCategory.valueOf("AUTHORIZATION")).isEqualTo(ErrorCode.ErrorCategory.AUTHORIZATION);
            assertThat(ErrorCode.ErrorCategory.valueOf("VALIDATION")).isEqualTo(ErrorCode.ErrorCategory.VALIDATION);
            assertThat(ErrorCode.ErrorCategory.valueOf("SEARCH")).isEqualTo(ErrorCode.ErrorCategory.SEARCH);
            assertThat(ErrorCode.ErrorCategory.valueOf("BUSINESS")).isEqualTo(ErrorCode.ErrorCategory.BUSINESS);
            assertThat(ErrorCode.ErrorCategory.valueOf("DATABASE")).isEqualTo(ErrorCode.ErrorCategory.DATABASE);
            assertThat(ErrorCode.ErrorCategory.valueOf("EXTERNAL")).isEqualTo(ErrorCode.ErrorCategory.EXTERNAL);
        }
    }

    @Test
    @DisplayName("should cover all enum constants via values()")
    void shouldCoverAllValues() {
        ErrorCode[] values = ErrorCode.values();
        assertThat(values.length).isGreaterThan(30);
        for (ErrorCode code : values) {
            assertThat(code.getCode()).isNotBlank();
            assertThat(code.getDefaultMessage()).isNotBlank();
            assertThat(code.getHttpStatus()).isNotNull();
            assertThat(code.getCategory()).isNotNull();
        }
    }
}
