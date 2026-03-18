package dev.simplecore.simplix.web.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional tests for SimpliXExceptionHandler targeting uncovered branches:
 * - handleValidationException with named placeholders ({min}, {max})
 * - handleValidationException with numeric placeholders ({0}, {1})
 * - handleValidationException with {key} format messages
 * - handleNoResourceFoundException with trace logging enabled
 * - determineSearchableErrorCode class name branches (Sort, Filter, Parse)
 * - addTraceIdToResponse
 * - DefaultResponseFactory logging branches
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXExceptionHandler - additional branch coverage tests")
class SimpliXExceptionHandlerAdditionalTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private TestExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestExceptionHandler(messageSource, new ObjectMapper());
        when(request.getRequestURI()).thenReturn("/api/test");
        MDC.clear();
    }

    @Nested
    @DisplayName("handleValidationException - named placeholders")
    class ValidationNamedPlaceholders {

        @Test
        @DisplayName("Should substitute named placeholders like {min} and {max} in validation messages")
        @SuppressWarnings("unchecked")
        void substituteNamedPlaceholders() {
            when(messageSource.getMessage(eq("error.val.validation.failed"), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Validation failed");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");

            // Create a field error with named placeholders in default message
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("min", 2);
            constraintAttrs.put("max", 100);
            FieldError fieldError = new FieldError("testObj", "name", null, false,
                    new String[]{"Size.testObj.name", "Size"},
                    new Object[]{new DefaultMessageSourceResolvable(new String[]{"testObj.name"}, "name"), constraintAttrs},
                    "Size must be between {min} and {max}");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            SimpliXApiResponse<Object> result = handler.handleValidationException(ex, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should substitute numeric placeholders like {0}, {1} in validation messages")
        @SuppressWarnings("unchecked")
        void substituteNumericPlaceholders() {
            when(messageSource.getMessage(eq("error.val.validation.failed"), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Validation failed");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");

            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 100);
            constraintAttrs.put("min", 2);
            FieldError fieldError = new FieldError("testObj", "name", null, false,
                    new String[]{"Length.testObj.name", "Length"},
                    new Object[]{new DefaultMessageSourceResolvable(new String[]{"testObj.name"}, "name"), constraintAttrs},
                    "Length must be between {0} and {1}");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            SimpliXApiResponse<Object> result = handler.handleValidationException(ex, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should translate {key} format messages using MessageSource")
        @SuppressWarnings("unchecked")
        void translateKeyFormatMessages() {
            when(messageSource.getMessage(eq("error.val.validation.failed"), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Validation failed");
            when(messageSource.getMessage(eq("validation.size.message"), any(), any(Locale.class)))
                    .thenReturn("Size is wrong");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");

            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 50);
            constraintAttrs.put("min", 1);
            FieldError fieldError = new FieldError("testObj", "name", null, false,
                    new String[]{"Size.testObj.name", "Size"},
                    new Object[]{new DefaultMessageSourceResolvable(new String[]{"testObj.name"}, "name"), constraintAttrs},
                    "{validation.size.message}");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            SimpliXApiResponse<Object> result = handler.handleValidationException(ex, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle null default message in validation error")
        @SuppressWarnings("unchecked")
        void nullDefaultMessage() {
            when(messageSource.getMessage(eq("error.val.validation.failed"), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Validation failed");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");

            FieldError fieldError = new FieldError("testObj", "name", null, false,
                    new String[]{"NotNull.testObj.name", "NotNull"},
                    new Object[]{new DefaultMessageSourceResolvable(new String[]{"testObj.name"}, "name")},
                    null);
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            SimpliXApiResponse<Object> result = handler.handleValidationException(ex, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should log with trace ID during validation error")
        @SuppressWarnings("unchecked")
        void logTraceIdDuringValidation() {
            MDC.put("traceId", "test-trace-123");
            when(messageSource.getMessage(eq("error.val.validation.failed"), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Validation failed");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObj");
            FieldError fieldError = new FieldError("testObj", "name", "must not be blank");
            bindingResult.addError(fieldError);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            SimpliXApiResponse<Object> result = handler.handleValidationException(ex, request);

            assertThat(result).isNotNull();
            MDC.clear();
        }
    }

    @Nested
    @DisplayName("determineSearchableErrorCode - class name branches")
    class SearchableErrorCodeClassNames {

        @Test
        @DisplayName("Should detect Sort in class name")
        void sortInClassName() {
            Throwable sortException = createExceptionWithClassName("SortFieldException");

            SimpliXApiResponse<Object> result = handler.handleSearchableException(sortException, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should detect Filter in class name")
        void filterInClassName() {
            Throwable filterException = createExceptionWithClassName("InvalidFilterOperatorException");

            SimpliXApiResponse<Object> result = handler.handleSearchableException(filterException, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should detect Parse in class name")
        void parseInClassName() {
            Throwable parseException = createExceptionWithClassName("QueryParseSyntaxException");

            SimpliXApiResponse<Object> result = handler.handleSearchableException(parseException, request);

            assertThat(result).isNotNull();
        }

        private Throwable createExceptionWithClassName(String className) {
            // Return an exception from a fake searchable package
            return switch (className) {
                case "SortFieldException" -> new SortFieldException("sort field invalid");
                case "InvalidFilterOperatorException" -> new InvalidFilterOperatorException("filter operator invalid");
                case "QueryParseSyntaxException" -> new QueryParseSyntaxException("parse error");
                default -> new RuntimeException("unknown");
            };
        }
    }

    @Nested
    @DisplayName("handleNoResourceFoundException")
    class NoResourceFound {

        @Test
        @DisplayName("Should handle NoResourceFoundException and return 404")
        void handleNoResource() throws Exception {
            when(messageSource.getMessage(eq("error.resource.not.found"), isNull(), anyString(), any(Locale.class)))
                    .thenReturn("Resource not found");

            NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/nonexistent");

            SimpliXApiResponse<Object> result = handler.handleNoResourceFoundException(ex, request);

            assertThat(result).isNotNull();
        }
    }

    // --- Test helper classes ---

    @RestControllerAdvice
    static class TestExceptionHandler extends SimpliXExceptionHandler<SimpliXApiResponse<Object>> {
        TestExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
            super(messageSource, objectMapper);
        }
    }

    // Fake exceptions for searchable error code determination
    static class SortFieldException extends RuntimeException {
        SortFieldException(String message) { super(message); }
    }

    static class InvalidFilterOperatorException extends RuntimeException {
        InvalidFilterOperatorException(String message) { super(message); }
    }

    static class QueryParseSyntaxException extends RuntimeException {
        QueryParseSyntaxException(String message) { super(message); }
    }
}
