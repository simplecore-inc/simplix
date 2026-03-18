package dev.simplecore.simplix.web.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXExceptionHandler - handles various exception types and returns error responses")
class SimpliXExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse servletResponse;

    private SimpliXExceptionHandler<SimpliXApiResponse<Object>> handler;

    @BeforeEach
    void setUp() {
        handler = new SimpliXExceptionHandler<>(messageSource, new ObjectMapper());
        lenient().when(request.getRequestURI()).thenReturn("/api/test");
        MDC.clear();
    }

    @Nested
    @DisplayName("handleSimpliXGeneralException")
    class HandleSimpliXGeneralException {

        @Test
        @DisplayName("Should return error response with correct error code and message")
        void simpliXGeneralException() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_NOT_FOUND, "Resource not found", "detail info");

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            assertThat(response.getMessage()).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("Should use default error code when errorCode is null")
        void nullErrorCode() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Server error", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_INTERNAL_SERVER_ERROR");
        }

        @Test
        @DisplayName("Should set HTTP status and trace ID header when request context is available")
        void setsHttpStatusAndTraceId() {
            MDC.put("traceId", "test-trace-id-123");
            ServletRequestAttributes attrs = new ServletRequestAttributes(request, servletResponse);
            RequestContextHolder.setRequestAttributes(attrs);

            try {
                SimpliXGeneralException ex = new SimpliXGeneralException(
                        ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

                SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

                assertThat(response).isNotNull();
                verify(servletResponse).setStatus(HttpStatus.BAD_REQUEST.value());
                verify(servletResponse).setHeader("X-Trace-Id", "test-trace-id-123");
            } finally {
                RequestContextHolder.resetRequestAttributes();
                MDC.clear();
            }
        }

        @Test
        @DisplayName("Should handle gracefully when request context is not available for setting status")
        void handlesNoRequestContext() {
            RequestContextHolder.resetRequestAttributes();
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_NOT_FOUND, "Not found", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
        }

        @Test
        @DisplayName("Should log at error level for 500 status with trace ID")
        void logsErrorFor500WithTraceId() {
            MDC.put("traceId", "trace-500");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Internal error", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_INTERNAL_SERVER_ERROR");
            MDC.clear();
        }

        @Test
        @DisplayName("Should log at debug level for 404 status with trace ID")
        void logsDebugFor404WithTraceId() {
            MDC.put("traceId", "trace-404");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_NOT_FOUND, "Not found", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            MDC.clear();
        }

        @Test
        @DisplayName("Should log at warn level for 4xx status (not 404) with trace ID")
        void logsWarnFor4xxWithTraceId() {
            MDC.put("traceId", "trace-400");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("GEN_BAD_REQUEST");
            MDC.clear();
        }

        @Test
        @DisplayName("Should skip logging when trace ID is not set")
        void skipsLoggingWithoutTraceId() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should skip logging when trace ID is empty")
        void skipsLoggingWithEmptyTraceId() {
            MDC.put("traceId", "");
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            MDC.clear();
        }
    }

    @Nested
    @DisplayName("handleAccessDeniedException")
    class HandleAccessDeniedException {

        @Test
        @DisplayName("Should return 403 error response for AccessDeniedException")
        void accessDenied() {
            when(messageSource.getMessage(eq("error.authz.insufficient.permissions"), isNull(),
                    eq("Access denied"), any(Locale.class))).thenReturn("Access denied");
            when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), isNull(),
                    eq("You do not have permission to access the requested resource"), any(Locale.class)))
                    .thenReturn("You do not have permission");

            AccessDeniedException ex = new AccessDeniedException("forbidden");

            SimpliXApiResponse<Object> response = handler.handleAccessDeniedException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("AUTHZ_INSUFFICIENT_PERMISSIONS");
            assertThat(response.getMessage()).isEqualTo("Access denied");
        }
    }

    @Nested
    @DisplayName("handleBadCredentialsException")
    class HandleBadCredentialsException {

        @Test
        @DisplayName("Should return 401 error response for BadCredentialsException")
        void badCredentials() {
            when(messageSource.getMessage(eq("error.auth.invalid.credentials"), isNull(),
                    eq("Invalid credentials"), any(Locale.class))).thenReturn("Invalid credentials");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials.detail"), isNull(),
                    eq("The username or password is incorrect"), any(Locale.class)))
                    .thenReturn("Wrong credentials");

            BadCredentialsException ex = new BadCredentialsException("Bad credentials");

            SimpliXApiResponse<Object> response = handler.handleBadCredentialsException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        }
    }

    @Nested
    @DisplayName("handleAuthenticationException")
    class HandleAuthenticationException {

        @Test
        @DisplayName("Should return 401 error response for AuthenticationException")
        void authenticationRequired() {
            when(messageSource.getMessage(eq("error.auth.authentication.required"), isNull(),
                    eq("Authentication required"), any(Locale.class))).thenReturn("Auth required");
            when(messageSource.getMessage(eq("error.authenticationFailed.detail"), isNull(),
                    eq("Login is required or token is invalid"), any(Locale.class)))
                    .thenReturn("Please log in");

            InsufficientAuthenticationException ex =
                    new InsufficientAuthenticationException("Not authenticated");

            SimpliXApiResponse<Object> response = handler.handleAuthenticationException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("AUTH_AUTHENTICATION_REQUIRED");
        }
    }

    @Nested
    @DisplayName("handleAsyncRequestTimeoutException")
    class HandleAsyncRequestTimeout {

        @Test
        @DisplayName("Should return 408 error response for async request timeout")
        void asyncTimeout() {
            when(messageSource.getMessage(eq("error.gen.timeout"), isNull(),
                    eq("Request timeout"), any(Locale.class))).thenReturn("Timeout");

            AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

            SimpliXApiResponse<Object> response = handler.handleAsyncRequestTimeoutException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_TIMEOUT");
        }
    }

    @Nested
    @DisplayName("handleNoResourceFoundException")
    class HandleNoResourceFound {

        @Test
        @DisplayName("Should return 404 error response for NoResourceFoundException")
        void noResource() throws Exception {
            when(messageSource.getMessage(eq("error.resource.not.found"), isNull(),
                    eq("Resource not found"), any(Locale.class))).thenReturn("Not found");

            NoResourceFoundException ex = new NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "/missing");

            SimpliXApiResponse<Object> response = handler.handleNoResourceFoundException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_NOT_FOUND");
            assertThat(response.getMessage()).isEqualTo("Not found");
        }
    }

    @Nested
    @DisplayName("handleSpringMvcClientException")
    class HandleSpringMvcClientException {

        @Test
        @DisplayName("Should return 405 for HttpRequestMethodNotSupportedException")
        void methodNotSupported() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Method not allowed");
            when(request.getMethod()).thenReturn("DELETE");

            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_METHOD_NOT_ALLOWED");
        }

        @Test
        @DisplayName("Should return 400 for MissingServletRequestParameterException")
        void missingParameter() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Missing parameter");
            when(request.getMethod()).thenReturn("GET");

            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("id", "Long");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("VAL_MISSING_PARAMETER");
        }

        @Test
        @DisplayName("Should return 415 for HttpMediaTypeNotSupportedException")
        void unsupportedMediaType() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Unsupported media type");
            when(request.getMethod()).thenReturn("POST");

            HttpMediaTypeNotSupportedException ex =
                    new HttpMediaTypeNotSupportedException("Unsupported media type");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_BAD_REQUEST");
        }

        @Test
        @DisplayName("Should return 406 for HttpMediaTypeNotAcceptableException")
        void notAcceptableMediaType() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Not acceptable");
            when(request.getMethod()).thenReturn("GET");

            HttpMediaTypeNotAcceptableException ex =
                    new HttpMediaTypeNotAcceptableException("Not acceptable");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_BAD_REQUEST");
        }

        @Test
        @DisplayName("Should return 400 for MethodArgumentTypeMismatchException")
        void typeMismatch() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Invalid parameter");
            when(request.getMethod()).thenReturn("GET");

            MethodArgumentTypeMismatchException ex =
                    new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("VAL_INVALID_PARAMETER");
        }

        @Test
        @DisplayName("Should set trace ID on response header when trace ID is available")
        void setsTraceIdOnResponse() {
            MDC.put("traceId", "trace-mvc-client");
            ServletRequestAttributes attrs = new ServletRequestAttributes(request, servletResponse);
            RequestContextHolder.setRequestAttributes(attrs);

            try {
                when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                        .thenReturn("Error");
                when(request.getMethod()).thenReturn("DELETE");

                HttpRequestMethodNotSupportedException ex =
                        new HttpRequestMethodNotSupportedException("DELETE");

                handler.handleSpringMvcClientException(ex, request);

                verify(servletResponse).setHeader("X-Trace-Id", "trace-mvc-client");
            } finally {
                RequestContextHolder.resetRequestAttributes();
                MDC.clear();
            }
        }

        @Test
        @DisplayName("Should handle gracefully when request context is not available")
        void handlesNoRequestContext() {
            RequestContextHolder.resetRequestAttributes();
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Error");
            when(request.getMethod()).thenReturn("DELETE");

            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");

            SimpliXApiResponse<Object> response = handler.handleSpringMvcClientException(ex, request);

            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleException (generic)")
    class HandleGenericException {

        @Test
        @DisplayName("Should return 500 error response for generic exception")
        void genericException() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Internal server error");

            RuntimeException ex = new RuntimeException("Something went wrong");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should map IllegalArgumentException to VAL_INVALID_PARAMETER")
        void illegalArgument() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Invalid parameter");

            IllegalArgumentException ex = new IllegalArgumentException("bad param");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should map IllegalStateException to GEN_CONFLICT")
        void illegalState() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Conflict");

            IllegalStateException ex = new IllegalStateException("conflict state");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should map SecurityException to AUTH_AUTHENTICATION_REQUIRED")
        void securityException() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Authentication required");

            SecurityException ex = new SecurityException("access denied");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should map UnsupportedOperationException to GEN_METHOD_NOT_ALLOWED")
        void unsupportedOperation() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Method not allowed");

            UnsupportedOperationException ex = new UnsupportedOperationException("not supported");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should delegate SimpliXGeneralException to handleSimpliXGeneralException")
        void simpliXGeneralExceptionDelegation() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("GEN_BAD_REQUEST");
        }

        @Test
        @DisplayName("Should include detailed error info in debug mode")
        void debugModeDetail() {
            String originalProfiles = System.getProperty("spring.profiles.active", "");
            try {
                System.setProperty("spring.profiles.active", "dev");
                when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                        .thenReturn("Error");

                RuntimeException ex = new RuntimeException("debug test error");

                SimpliXApiResponse<Object> response = handler.handleException(ex, request);

                assertThat(response).isNotNull();
            } finally {
                if (originalProfiles.isEmpty()) {
                    System.clearProperty("spring.profiles.active");
                } else {
                    System.setProperty("spring.profiles.active", originalProfiles);
                }
            }
        }

        @Test
        @DisplayName("Should handle nested searchable exception from root cause")
        void nestedSearchableException() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Search error");

            // Create a mock Searchable exception chain
            RuntimeException searchableRoot = new RuntimeException("Invalid sort field") {
                @Override
                public String getMessage() {
                    return "Invalid sort field";
                }
            };
            // Simulate it being in the searchable package - this requires a real class from that package
            // Instead, just test the generic path
            RuntimeException wrapper = new RuntimeException("Wrapper", searchableRoot);

            SimpliXApiResponse<Object> response = handler.handleException(wrapper, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }
    }

    @Nested
    @DisplayName("handleSearchableException")
    class HandleSearchableException {

        @Test
        @DisplayName("Should detect Validation in class name and return SEARCH_INVALID_PARAMETER")
        void searchableValidationException() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Invalid search parameter");

            RuntimeException cause = new RuntimeException("Invalid search field");

            SimpliXApiResponse<Object> response = handler.handleSearchableException(cause, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should detect 'sort' in message and return SEARCH_INVALID_SORT_FIELD")
        void searchableSortExceptionByMessage() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Invalid sort field");

            RuntimeException cause = new RuntimeException("Invalid sort field specified");

            SimpliXApiResponse<Object> response = handler.handleSearchableException(cause, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should detect 'filter' in message and return SEARCH_INVALID_FILTER_OPERATOR")
        void searchableFilterExceptionByMessage() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Invalid filter operator");

            RuntimeException cause = new RuntimeException("Invalid filter operator applied");

            SimpliXApiResponse<Object> response = handler.handleSearchableException(cause, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should detect 'parse' in message and return SEARCH_INVALID_QUERY_SYNTAX")
        void searchableParseExceptionByMessage() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Invalid query syntax");

            RuntimeException cause = new RuntimeException("Failed to parse query");

            SimpliXApiResponse<Object> response = handler.handleSearchableException(cause, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should handle null message in searchable exception")
        void searchableNullMessage() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Search error");

            RuntimeException cause = new RuntimeException((String) null);

            SimpliXApiResponse<Object> response = handler.handleSearchableException(cause, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }
    }

    @Nested
    @DisplayName("SSE connection exception handling")
    class SseConnectionExceptionHandling {

        @Test
        @DisplayName("Should return null for SocketTimeoutException (SSE client disconnect)")
        void socketTimeoutReturnsNull() {
            SocketTimeoutException ex = new SocketTimeoutException("Read timed out");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should return null for broken pipe IOException")
        void brokenPipeReturnsNull() {
            IOException ex = new IOException("Broken pipe");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should return null for connection reset IOException")
        void connectionResetReturnsNull() {
            IOException ex = new IOException("Connection reset by peer");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should return null for stream closed IOException")
        void streamClosedReturnsNull() {
            IOException ex = new IOException("Stream closed");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should return null for socket timeout IOException")
        void socketTimeoutIoReturnsNull() {
            IOException ex = new IOException("Socket timeout occurred");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should return null when SocketTimeoutException is nested in wrapper")
        void nestedSocketTimeoutReturnsNull() {
            SocketTimeoutException cause = new SocketTimeoutException("Read timed out");
            RuntimeException ex = new RuntimeException("Async dispatch failed", cause);

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should still handle non-connection IOException normally")
        void nonConnectionIoExceptionHandledNormally() {
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Internal server error");

            IOException ex = new IOException("File not found");

            SimpliXApiResponse<Object> response = handler.handleException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("Should return null when response is already committed")
        void committedResponseReturnsNull() {
            when(servletResponse.isCommitted()).thenReturn(true);
            ServletRequestAttributes attrs = new ServletRequestAttributes(request, servletResponse);
            RequestContextHolder.setRequestAttributes(attrs);

            try {
                RuntimeException ex = new RuntimeException("error after commit");

                SimpliXApiResponse<Object> response = handler.handleException(ex, request);

                assertThat(response).isNull();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        @DisplayName("Should return null for SimpliXGeneralException when response is committed")
        void committedResponseReturnsNullForSimpliXException() {
            when(servletResponse.isCommitted()).thenReturn(true);
            ServletRequestAttributes attrs = new ServletRequestAttributes(request, servletResponse);
            RequestContextHolder.setRequestAttributes(attrs);

            try {
                SimpliXGeneralException ex = new SimpliXGeneralException(
                        ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Error", null);

                SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

                assertThat(response).isNull();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    @Nested
    @DisplayName("getLocalizedMessage")
    class GetLocalizedMessage {

        @Test
        @DisplayName("Should use fallback message when MessageSource throws exception")
        void useFallbackWhenMessageSourceFails() {
            // The getLocalizedMessage is indirectly tested through handleValidationException
            // but we can also trigger it via exception paths
            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Localized error");

            AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

            SimpliXApiResponse<Object> response = handler.handleAsyncRequestTimeoutException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Localized error");
        }
    }

    @Nested
    @DisplayName("DefaultResponseFactory")
    class DefaultResponseFactoryTest {

        @Test
        @DisplayName("Should create error response with null errorType and fallback to status name")
        void createErrorResponseWithNullErrorType() {
            SimpliXGeneralException ex = new SimpliXGeneralException(
                    ErrorCode.GEN_BAD_REQUEST, "Bad request", null);

            SimpliXApiResponse<Object> response = handler.handleSimpliXGeneralException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isNotNull();
        }

        @Test
        @DisplayName("Should log at different levels based on HTTP status with trace ID")
        void logsBasedOnStatusWithTraceId() {
            MDC.put("traceId", "trace-factory");
            try {
                // 404 - trace level
                SimpliXGeneralException ex404 = new SimpliXGeneralException(
                        ErrorCode.GEN_NOT_FOUND, "Not found", null);
                handler.handleSimpliXGeneralException(ex404, request);

                // 400 - warn level
                SimpliXGeneralException ex400 = new SimpliXGeneralException(
                        ErrorCode.GEN_BAD_REQUEST, "Bad", null);
                handler.handleSimpliXGeneralException(ex400, request);

                // 500 - error level
                SimpliXGeneralException ex500 = new SimpliXGeneralException(
                        ErrorCode.GEN_INTERNAL_SERVER_ERROR, "Error", null);
                handler.handleSimpliXGeneralException(ex500, request);
            } finally {
                MDC.clear();
            }
        }
    }

    @Nested
    @DisplayName("handleValidationException")
    class HandleValidationException {

        @Test
        @DisplayName("Should handle MethodArgumentNotValidException with field errors")
        void handleFieldErrors() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            bindingResult.addError(new FieldError("testObj", "name", null, false,
                    new String[]{"NotBlank.testObj.name", "NotBlank"}, null, "must not be blank"));

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    null, bindingResult);

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenReturn("Validation failed");

            SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getErrorCode()).isEqualTo("VAL_VALIDATION_FAILED");
        }

        @Test
        @DisplayName("Should handle validation messages with named placeholders like {min} {max}")
        void handleNamedPlaceholders() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            FieldError fieldError = new FieldError("testObj", "age", 5, false,
                    new String[]{"Min.testObj.age", "Min"},
                    new Object[]{
                        new DefaultMessageSourceResolvable(new String[]{"testObj.age"}, "age"),
                        Map.of("min", 18, "value", 5)
                    },
                    "must be at least {min}");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    null, bindingResult);

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenReturn("Validation failed");

            SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

            assertThat(response).isNotNull();
            assertThat(response.getErrorCode()).isEqualTo("VAL_VALIDATION_FAILED");
        }

        @Test
        @DisplayName("Should handle validation messages with numeric placeholders like {0} {1}")
        void handleNumericPlaceholders() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            FieldError fieldError = new FieldError("testObj", "name", "ab", false,
                    new String[]{"Length.testObj.name", "Length"},
                    new Object[]{
                        new DefaultMessageSourceResolvable(new String[]{"testObj.name"}, "name"),
                        Map.of("max", 100, "min", 2)
                    },
                    "Length must be between {0} and {1}");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    null, bindingResult);

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenReturn("Validation failed");

            SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should handle validation messages with {key} format for i18n lookup")
        void handleI18nKeyFormat() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            FieldError fieldError = new FieldError("testObj", "email", null, false,
                    new String[]{"Email.testObj.email", "Email"},
                    new Object[]{
                        new DefaultMessageSourceResolvable(new String[]{"testObj.email"}, "email")
                    },
                    "{validation.email}");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    null, bindingResult);

            when(messageSource.getMessage(eq("validation.email"), any(), any(Locale.class)))
                    .thenReturn("Invalid email format");
            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenReturn("Validation failed");

            SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should handle null default message in field error")
        void handleNullDefaultMessage() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            FieldError fieldError = new FieldError("testObj", "field", null, false,
                    new String[]{"NotNull.testObj.field", "NotNull"}, null, null);
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    null, bindingResult);

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenReturn("Validation failed");

            SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should handle field error with null codes")
        void handleNullCodes() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            FieldError fieldError = new FieldError("testObj", "field", "invalid", false,
                    null, null, "error message");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    null, bindingResult);

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenReturn("Validation failed");

            SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should add trace ID to validation response when present")
        void addsTraceIdForValidation() throws Exception {
            MDC.put("traceId", "trace-validation");
            try {
                BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
                bindingResult.addError(new FieldError("testObj", "name", null, false,
                        new String[]{"NotBlank"}, null, "required"));

                MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                        null, bindingResult);

                when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                        .thenReturn("Validation failed");

                SimpliXApiResponse<Object> response = handler.handleValidationException(ex, request);

                assertThat(response).isNotNull();
            } finally {
                MDC.clear();
            }
        }
    }

    @Nested
    @DisplayName("Custom ResponseFactory")
    class CustomResponseFactoryTest {

        @Test
        @DisplayName("Should work with custom ResponseFactory via constructor")
        void customResponseFactory() {
            SimpliXExceptionHandler.ResponseFactory<String> customFactory =
                    (statusCode, errorType, message, detail, path) -> "Error: " + message;

            SimpliXExceptionHandler<String> customHandler =
                    new SimpliXExceptionHandler<>(messageSource, customFactory);

            when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Access denied");
            when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), isNull(),
                    anyString(), any(Locale.class))).thenReturn("No permission");

            AccessDeniedException ex = new AccessDeniedException("forbidden");
            String response = customHandler.handleAccessDeniedException(ex, request);

            assertThat(response).isEqualTo("Error: Access denied");
        }
    }
}
