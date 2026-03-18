package dev.simplecore.simplix.web.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import jakarta.servlet.RequestDispatcher;
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
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXErrorController - handles error page rendering and JSON error responses")
class SimpliXErrorControllerTest {

    @Mock
    private ErrorAttributes errorAttributes;

    @Mock
    private SimpliXExceptionHandler<SimpliXApiResponse<Object>> exceptionHandler;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ServerProperties serverProperties;

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private SimpliXErrorController controller;

    @BeforeEach
    void setUp() {
        controller = new SimpliXErrorController(
                errorAttributes, exceptionHandler, objectMapper, serverProperties, messageSource);
        MDC.clear();
    }

    @Nested
    @DisplayName("handleEventStreamError")
    class HandleEventStreamError {

        @Test
        @DisplayName("Should set status OK and content type for SSE errors")
        void handleSseError() {
            controller.handleEventStreamError(request, response);

            verify(response).setStatus(HttpStatus.OK.value());
            verify(response).setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        }
    }

    @Nested
    @DisplayName("handleErrorHtml")
    class HandleErrorHtml {

        @Test
        @DisplayName("Should create ModelAndView with error status and message")
        void basicHtmlError() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Not Found");

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getViewName()).isEqualTo("error");
            assertThat(mav.getModel().get("status")).isEqualTo(404);
            assertThat(mav.getModel().get("error")).isEqualTo("Not Found");
            assertThat(mav.getModel().get("message")).isEqualTo("Not Found");
            verify(response).setStatus(404);
        }

        @Test
        @DisplayName("Should include error messages from exception chain")
        void exceptionChainMessages() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            RuntimeException rootCause = new RuntimeException("root cause");
            RuntimeException exception = new RuntimeException("wrapper message", rootCause);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.NEVER);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("message")).isEqualTo("wrapper message");
            assertThat(mav.getModel().get("errorMessages")).isNotNull();
            @SuppressWarnings("unchecked")
            List<String> errorMessages = (List<String>) mav.getModel().get("errorMessages");
            assertThat(errorMessages).contains("wrapper message", "root cause");
        }

        @Test
        @DisplayName("Should include stack trace when configured with ALWAYS")
        void includeStackTrace() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/error-test");
            RuntimeException exception = new RuntimeException("test error");
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.ALWAYS);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("trace")).isNotNull();
            assertThat(mav.getModel().get("trace").toString()).contains("test error");
        }

        @Test
        @DisplayName("Should include stack trace when ON_PARAM and trace parameter is present")
        void includeStackTraceOnParam() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            RuntimeException exception = new RuntimeException("param test error");
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);
            when(request.getParameter("trace")).thenReturn("true");

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.ON_PARAM);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("trace")).isNotNull();
        }

        @Test
        @DisplayName("Should not include stack trace when ON_PARAM and trace parameter is absent")
        void noStackTraceOnParamMissing() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            RuntimeException exception = new RuntimeException("no param error");
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);
            when(request.getParameter("trace")).thenReturn(null);

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.ON_PARAM);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("trace")).isNull();
        }

        @Test
        @DisplayName("Should default to 500 when status code attribute is null")
        void defaultStatusCode() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("status")).isEqualTo(500);
            verify(response).setStatus(500);
        }

        @Test
        @DisplayName("Should use 'No message available' when message is null and no exception")
        void noMessageAvailable() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("message")).isEqualTo("No message available");
        }

        @Test
        @DisplayName("Should use first error message when ERROR_MESSAGE is null but exception has message")
        void useExceptionMessageWhenErrorMessageNull() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("");
            RuntimeException exception = new RuntimeException("Exception message");
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.NEVER);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            assertThat(mav.getModel().get("message")).isEqualTo("Exception message");
        }

        @Test
        @DisplayName("Should deduplicate exception messages in chain")
        void deduplicateExceptionMessages() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            RuntimeException rootCause = new RuntimeException("same message");
            RuntimeException middle = new RuntimeException("same message", rootCause);
            RuntimeException top = new RuntimeException("different message", middle);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(top);

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.NEVER);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            @SuppressWarnings("unchecked")
            List<String> errorMessages = (List<String>) mav.getModel().get("errorMessages");
            assertThat(errorMessages).hasSize(2);
            assertThat(errorMessages).containsExactly("different message", "same message");
        }

        @Test
        @DisplayName("Should handle exception with null messages in chain")
        void exceptionChainWithNullMessages() {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            RuntimeException rootCause = new RuntimeException((String) null);
            RuntimeException exception = new RuntimeException("top message", rootCause);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);

            ErrorProperties errorProperties = new ErrorProperties();
            errorProperties.setIncludeStacktrace(ErrorProperties.IncludeAttribute.NEVER);
            when(serverProperties.getError()).thenReturn(errorProperties);

            ModelAndView mav = controller.handleErrorHtml(request, response);

            @SuppressWarnings("unchecked")
            List<String> errorMessages = (List<String>) mav.getModel().get("errorMessages");
            assertThat(errorMessages).hasSize(1);
            assertThat(errorMessages).containsExactly("top message");
        }
    }

    @Nested
    @DisplayName("handleErrorJson")
    class HandleErrorJson {

        @Test
        @DisplayName("Should handle 403 error with access denied response")
        void handle403Error() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(403);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(messageSource.getMessage(eq("error.insufficientPermissions"), isNull(), any(Locale.class)))
                    .thenReturn("Insufficient permissions");
            when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), isNull(), any(Locale.class)))
                    .thenReturn("You do not have permission");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(403);
            verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
            verify(response).setCharacterEncoding("UTF-8");
            verify(objectMapper).writeValue(any(PrintWriter.class), any(SimpliXApiResponse.class));
        }

        @Test
        @DisplayName("Should handle 404 error with not found response")
        void handle404Error() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/missing");
            when(request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME)).thenReturn(null);
            when(messageSource.getMessage(eq("error.notFound"), isNull(), any(Locale.class)))
                    .thenReturn("Not found");
            when(messageSource.getMessage(eq("error.notFound.detail"), any(Object[].class), any(Locale.class)))
                    .thenReturn("Resource not found: /missing");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(404);
            verify(objectMapper).writeValue(any(PrintWriter.class), any(SimpliXApiResponse.class));
        }

        @Test
        @DisplayName("Should handle 404 without request URI")
        void handle404WithoutRequestUri() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(null);
            when(messageSource.getMessage(eq("error.notFound"), isNull(), any(Locale.class)))
                    .thenReturn("Not found");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(404);
        }

        @Test
        @DisplayName("Should delegate to exception handler when throwable is present")
        void delegateToExceptionHandler() throws IOException {
            RuntimeException exception = new RuntimeException("test error");
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);
            when(exceptionHandler.handleException(any(Exception.class), eq(request)))
                    .thenReturn(SimpliXApiResponse.error("Error"));

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(exceptionHandler).handleException(any(Exception.class), eq(request));
            verify(response).setStatus(500);
        }

        @Test
        @DisplayName("Should default to 500 when status code is null")
        void defaultStatusCode() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(messageSource.getMessage(eq("error.unknownError"), isNull(), any(Locale.class)))
                    .thenReturn("Unknown error");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(500);
        }

        @Test
        @DisplayName("Should handle 401 with invalid credentials message")
        void handle401InvalidCredentials() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Invalid credentials");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials"), isNull(), any(Locale.class)))
                    .thenReturn("Invalid credentials");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials.detail"), isNull(), any(Locale.class)))
                    .thenReturn("Wrong password");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("Should handle 401 with generic authentication required message")
        void handle401AuthRequired() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Unauthorized");
            when(messageSource.getMessage(eq("error.authenticationFailed"), isNull(), any(Locale.class)))
                    .thenReturn("Authentication required");
            when(messageSource.getMessage(eq("error.authenticationFailed.detail"), isNull(), any(Locale.class)))
                    .thenReturn("Please log in");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("Should handle BadCredentialsException in exception chain")
        void handleBadCredentialsInChain() throws IOException {
            BadCredentialsException rootCause = new BadCredentialsException("Bad creds");
            RuntimeException wrapper = new RuntimeException("Wrapper", rootCause);
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(wrapper);
            when(messageSource.getMessage(eq("error.auth.invalid.credentials"), isNull(),
                    eq("Invalid credentials"), any(Locale.class))).thenReturn("Invalid credentials");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials.detail"), isNull(),
                    eq("The username or password is incorrect"), any(Locale.class)))
                    .thenReturn("Wrong credentials");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(401);
            verify(objectMapper).writeValue(any(PrintWriter.class), any(SimpliXApiResponse.class));
        }

        @Test
        @DisplayName("Should handle direct BadCredentialsException throwable")
        void handleDirectBadCredentials() throws IOException {
            BadCredentialsException ex = new BadCredentialsException("Bad creds");
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(ex);
            when(messageSource.getMessage(eq("error.auth.invalid.credentials"), isNull(),
                    eq("Invalid credentials"), any(Locale.class))).thenReturn("Invalid credentials");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials.detail"), isNull(),
                    eq("The username or password is incorrect"), any(Locale.class)))
                    .thenReturn("Wrong credentials");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("Should handle non-Exception throwable by wrapping in Exception")
        void handleNonExceptionThrowable() throws IOException {
            Error throwableError = new Error("some error");
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(throwableError);
            when(exceptionHandler.handleException(any(Exception.class), eq(request)))
                    .thenReturn(SimpliXApiResponse.error("Error"));

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(exceptionHandler).handleException(any(Exception.class), eq(request));
        }

        @Test
        @DisplayName("Should add trace ID to response header when available")
        void addTraceIdToHeader() throws IOException {
            MDC.put("traceId", "test-trace-123");
            try {
                when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
                when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
                when(messageSource.getMessage(eq("error.unknownError"), isNull(), any(Locale.class)))
                        .thenReturn("Unknown error");

                StringWriter sw = new StringWriter();
                when(response.getWriter()).thenReturn(new PrintWriter(sw));

                controller.handleErrorJson(request, response);

                verify(response).setHeader("X-Trace-Id", "test-trace-123");
            } finally {
                MDC.clear();
            }
        }

        @Test
        @DisplayName("Should handle 500 with error message from request attribute")
        void handle500WithErrorMessage() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Custom error message");
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/test");

            when(messageSource.getMessage(eq("error.unknownError.detail"), any(Object[].class), any(Locale.class)))
                    .thenReturn("Error detail for /api/test");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(500);
        }

        @Test
        @DisplayName("Should handle 500 without request URI")
        void handle500WithoutRequestUri() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(null);
            when(messageSource.getMessage(eq("error.unknownError"), isNull(), any(Locale.class)))
                    .thenReturn("Unknown error");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(500);
        }

        @Test
        @DisplayName("Should handle 401 with password-related message for invalid credentials detection")
        void handle401WithPasswordMessage() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Bad password provided");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials"), isNull(), any(Locale.class)))
                    .thenReturn("Invalid credentials");
            when(messageSource.getMessage(eq("error.auth.invalid.credentials.detail"), isNull(), any(Locale.class)))
                    .thenReturn("Wrong password");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("Should handle 401 with null error message")
        void handle401WithNullMessage() throws IOException {
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(messageSource.getMessage(eq("error.authenticationFailed"), isNull(), any(Locale.class)))
                    .thenReturn("Authentication required");
            when(messageSource.getMessage(eq("error.authenticationFailed.detail"), isNull(), any(Locale.class)))
                    .thenReturn("Please log in");

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            controller.handleErrorJson(request, response);

            verify(response).setStatus(401);
        }
    }
}
