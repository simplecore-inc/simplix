package dev.simplecore.simplix.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Verifies that SimpliXExceptionHandler resolves the CORRECT message keys
 * for each exception type, using a real ResourceBundleMessageSource loaded
 * with simplix_core messages. Each scenario is tested in both EN and KO locales.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptionHandler message resolution with real MessageSource")
class ExceptionHandlerMessageResolutionTest {

    private ResourceBundleMessageSource messageSource;
    private SimpliXExceptionHandler<SimpliXApiResponse<Object>> handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/simplix_core");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        handler = new SimpliXExceptionHandler<>(messageSource, new ObjectMapper());
        lenient().when(request.getRequestURI()).thenReturn("/api/test");
        lenient().when(request.getMethod()).thenReturn("GET");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // =========================================================================
    // SimpliXGeneralException with ErrorCode.GEN_NOT_FOUND
    // =========================================================================

    @Nested
    @DisplayName("SimpliXGeneralException with GEN_NOT_FOUND")
    class GenNotFound {

        @Test
        @DisplayName("EN locale: message from exception, errorCode = GEN_NOT_FOUND")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_NOT_FOUND, "Resource not found", "detail");

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            // The handler passes ex.getMessage() directly, which is "Resource not found"
            assertThat(response.getMessage()).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("KO locale: message from exception, errorCode = GEN_NOT_FOUND")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            // When creating SimpliXGeneralException, the message is set at construction time.
            // The handler passes the exception's own message, not a resolved one.
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_NOT_FOUND,
                    messageSource.getMessage("error.gen.not.found", null, Locale.KOREAN),
                    "detail");

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            // Korean message should be different from English
            assertThat(response.getMessage()).isNotEqualTo("Resource not found");
        }
    }

    // =========================================================================
    // SimpliXGeneralException with ErrorCode.GEN_BAD_REQUEST
    // =========================================================================

    @Nested
    @DisplayName("SimpliXGeneralException with GEN_BAD_REQUEST")
    class GenBadRequest {

        @Test
        @DisplayName("EN locale: message from exception, errorCode = GEN_BAD_REQUEST")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_BAD_REQUEST");
            assertThat(response.getMessage()).isEqualTo("Bad request");
        }

        @Test
        @DisplayName("KO locale: message from exception, errorCode = GEN_BAD_REQUEST")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST,
                    messageSource.getMessage("error.gen.bad.request", null, Locale.KOREAN),
                    null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_BAD_REQUEST");
            assertThat(response.getMessage()).isNotEqualTo("Bad request");
        }
    }

    // =========================================================================
    // AccessDeniedException -> error.authz.insufficient.permissions
    // =========================================================================

    @Nested
    @DisplayName("AccessDeniedException uses error.authz.insufficient.permissions")
    class AccessDenied {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            AccessDeniedException ex = new AccessDeniedException("forbidden");

            SimpliXApiResponse<Object> response = handler.handleAccessDeniedException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("AUTHZ_INSUFFICIENT_PERMISSIONS");
            // The handler calls messageSource.getMessage("error.authz.insufficient.permissions", ...)
            assertThat(response.getMessage()).isEqualTo("Insufficient permissions");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            AccessDeniedException ex = new AccessDeniedException("forbidden");

            SimpliXApiResponse<Object> response = handler.handleAccessDeniedException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("AUTHZ_INSUFFICIENT_PERMISSIONS");
            // Korean translation should be different from English
            assertThat(response.getMessage()).isNotEqualTo("Insufficient permissions");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // BadCredentialsException -> error.auth.invalid.credentials
    // =========================================================================

    @Nested
    @DisplayName("BadCredentialsException uses error.auth.invalid.credentials")
    class BadCredentials {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            BadCredentialsException ex = new BadCredentialsException("Bad credentials");

            SimpliXApiResponse<Object> response = handler.handleBadCredentialsException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
            assertThat(response.getMessage()).isEqualTo("Invalid credentials");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            BadCredentialsException ex = new BadCredentialsException("Bad credentials");

            SimpliXApiResponse<Object> response = handler.handleBadCredentialsException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
            assertThat(response.getMessage()).isNotEqualTo("Invalid credentials");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // AuthenticationException -> error.auth.authentication.required
    // =========================================================================

    @Nested
    @DisplayName("AuthenticationException uses error.auth.authentication.required")
    class AuthenticationRequired {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            InsufficientAuthenticationException ex =
                    new InsufficientAuthenticationException("Not authenticated");

            SimpliXApiResponse<Object> response = handler.handleAuthenticationException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("AUTH_AUTHENTICATION_REQUIRED");
            assertThat(response.getMessage()).isEqualTo("Authentication required");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            InsufficientAuthenticationException ex =
                    new InsufficientAuthenticationException("Not authenticated");

            SimpliXApiResponse<Object> response = handler.handleAuthenticationException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("AUTH_AUTHENTICATION_REQUIRED");
            assertThat(response.getMessage()).isNotEqualTo("Authentication required");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // AsyncRequestTimeoutException -> error.gen.timeout
    // =========================================================================

    @Nested
    @DisplayName("AsyncRequestTimeoutException uses error.gen.timeout")
    class AsyncTimeout {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

            SimpliXApiResponse<Object> response = handler.handleAsyncRequestTimeoutException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_TIMEOUT");
            assertThat(response.getMessage()).isEqualTo("Request timeout");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

            SimpliXApiResponse<Object> response = handler.handleAsyncRequestTimeoutException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_TIMEOUT");
            assertThat(response.getMessage()).isNotEqualTo("Request timeout");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // NoResourceFoundException -> error.resource.not.found (with default fallback)
    // =========================================================================

    @Nested
    @DisplayName("NoResourceFoundException uses error.resource.not.found")
    class NoResourceFound {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing");

            SimpliXApiResponse<Object> response = handler.handleNoResourceFoundException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            assertThat(response.getMessage()).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing");

            SimpliXApiResponse<Object> response = handler.handleNoResourceFoundException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            // error.resource.not.found is now in simplix_core_ko.properties
            assertThat(response.getMessage()).isNotEqualTo("Resource not found");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // HttpRequestMethodNotSupportedException -> error.gen.method.not.allowed
    // =========================================================================

    @Nested
    @DisplayName("HttpRequestMethodNotSupportedException uses error.gen.method.not.allowed")
    class MethodNotAllowed {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_METHOD_NOT_ALLOWED");
            assertThat(response.getMessage()).isEqualTo("Method not allowed");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_METHOD_NOT_ALLOWED");
            assertThat(response.getMessage()).isNotEqualTo("Method not allowed");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // MissingServletRequestParameterException -> error.val.missing.parameter
    // =========================================================================

    @Nested
    @DisplayName("MissingServletRequestParameterException uses error.val.missing.parameter")
    class MissingParameter {

        @Test
        @DisplayName("EN locale: resolves English message from simplix_core")
        void enLocale() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("id", "Long");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("VAL_MISSING_PARAMETER");
            assertThat(response.getMessage()).isEqualTo("Missing required parameter");
        }

        @Test
        @DisplayName("KO locale: resolves Korean message from simplix_core_ko")
        void koLocale() {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("id", "Long");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("VAL_MISSING_PARAMETER");
            assertThat(response.getMessage()).isNotEqualTo("Missing required parameter");
            assertThat(response.getMessage()).isNotBlank();
        }
    }

    // =========================================================================
    // Message key derivation pattern verification
    // =========================================================================

    @Nested
    @DisplayName("Message key derivation pattern")
    class KeyDerivationPattern {

        @Test
        @DisplayName("handleSpringMvcClientException derives key as error. + errorCode.getCode().toLowerCase().replace('_', '.')")
        void derivationPatternIsCorrect() {
            // The handler uses: "error." + errorCode.getCode().toLowerCase().replace("_", ".")
            // For GEN_METHOD_NOT_ALLOWED -> error.gen.method.not.allowed
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            String derivedKey = "error." + ErrorCode.GEN_METHOD_NOT_ALLOWED.getCode().toLowerCase().replace("_", ".");
            assertThat(derivedKey).isEqualTo("error.gen.method.not.allowed");

            // Verify the key resolves in the MessageSource
            String message = messageSource.getMessage(derivedKey, null, null, Locale.ENGLISH);
            assertThat(message)
                    .as("Derived key '%s' should resolve to a non-null message", derivedKey)
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("All ErrorCode values used in handleSpringMvcClientException have valid message keys")
        void allMvcExceptionErrorCodesResolve() {
            // These are the error codes used inside handleSpringMvcClientException
            ErrorCode[] usedCodes = {
                    ErrorCode.GEN_METHOD_NOT_ALLOWED,
                    ErrorCode.GEN_BAD_REQUEST,
                    ErrorCode.VAL_MISSING_PARAMETER,
                    ErrorCode.VAL_INVALID_PARAMETER
            };

            for (ErrorCode code : usedCodes) {
                String key = "error." + code.getCode().toLowerCase().replace("_", ".");
                String enMessage = messageSource.getMessage(key, null, null, Locale.ENGLISH);
                String koMessage = messageSource.getMessage(key, null, null, Locale.KOREAN);

                assertThat(enMessage)
                        .as("EN message for key '%s' (ErrorCode: %s)", key, code.name())
                        .isNotNull()
                        .isNotBlank();
                assertThat(koMessage)
                        .as("KO message for key '%s' (ErrorCode: %s)", key, code.name())
                        .isNotNull()
                        .isNotBlank();
                assertThat(enMessage)
                        .as("EN and KO messages should differ for key '%s'", key)
                        .isNotEqualTo(koMessage);
            }
        }
    }
}
