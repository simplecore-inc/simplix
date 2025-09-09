package dev.simplecore.simplix.web.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * Generic exception handler that can be extended to use custom response types and handle additional exceptions.
 *
 * <p>To use a custom response type:
 *
 * <p>1. Create your custom response class:
 * <pre>{@code
 * public class CustomApiResponse {
 *     private int code;
 *     private String message;
 *     private String detail;
 *     private OffsetDateTime timestamp;
 *
 *     // constructors, getters, setters...
 * }
 * }</pre>
 *
 * <p>2. Create a factory for your response type by implementing {@link ResponseFactory}:
 * <pre>{@code
 * public class CustomResponseFactory implements SimpliXExceptionHandler.ResponseFactory<CustomApiResponse> {
 *     @Override
 *     public CustomApiResponse createErrorResponse(int statusCode, String message, String error, String path) {
 *         CustomApiResponse response = new CustomApiResponse();
 *         response.setCode(statusCode);
 *         response.setMessage(message);
 *         response.setDetail(error);
 *         response.setTimestamp(OffsetDateTime.now());
 *         return response;
 *     }
 * }
 * }</pre>
 *
 * <p>3. Create your exception handler by extending this class:
 * <pre>{@code
 * @RestControllerAdvice
 * public class CustomExceptionHandler extends SimpliXExceptionHandler<CustomApiResponse> {
 *     public CustomExceptionHandler(MessageSource messageSource) {
 *         super(messageSource, new CustomResponseFactory());
 *     }
 * }
 * }</pre>
 *
 * <p>4. Configure in Spring Boot:
 * <pre>{@code
 * @Configuration
 * public class WebConfig {
 *     @Bean
 *     public CustomExceptionHandler customExceptionHandler(MessageSource messageSource) {
 *         return new CustomExceptionHandler(messageSource);
 *     }
 * }
 * }</pre>
 *
 * @param <T> The response type to be used. Defaults to {@link SimpliXApiResponse} if not specified
 * @see ResponseFactory
 */
@RequiredArgsConstructor
public class SimpliXExceptionHandler<T> {

    private static final Logger log = LoggerFactory.getLogger(SimpliXExceptionHandler.class);

    private final MessageSource messageSource;
    protected final ResponseFactory<T> responseFactory;

    @SuppressWarnings("unchecked")
    public SimpliXExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        this.messageSource = messageSource;
        this.responseFactory = (ResponseFactory<T>) new DefaultResponseFactory();
    }

    @ExceptionHandler(SimpliXGeneralException.class)
    @Order(0)
    public T handleSimpliXGeneralException(SimpliXGeneralException ex, HttpServletRequest request) {
        T errorResponse = responseFactory.createErrorResponse(
                ex.getStatusCode(),
                ex.getErrorCode() != null ? ex.getErrorCode().getCode() : ErrorCode.GEN_INTERNAL_SERVER_ERROR.getCode(),
                ex.getMessage(),
                ex.getDetail(),
                request.getRequestURI()
        );
        
        // Set HTTP status code and trace ID header
        try {
            HttpServletResponse response = ((org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getResponse();
            if (response != null) {
                response.setStatus(ex.getStatusCode().value());
                
                // Add trace ID to response header from MDC
                String traceId = MDC.get("traceId");
                if (traceId != null && !traceId.isEmpty()) {
                    response.setHeader("X-Trace-Id", traceId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to set HTTP status: {}", e.getMessage());
        }
        
        // Log exception with trace ID
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            log.error("SimpliXGeneralException - TraceId: {}, ErrorCode: {}, Path: {}",
                traceId,
                ex.getErrorCode() != null ? ex.getErrorCode().getCode() : ErrorCode.GEN_INTERNAL_SERVER_ERROR.getCode(),
                request.getRequestURI(),
                ex);
        }
        
        return errorResponse;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @Order(1)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public T handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Locale currentLocale = LocaleContextHolder.getLocale();
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            Map<String, String> fieldError = new HashMap<>();
            fieldError.put("field", error.getField());
            
            String message = error.getDefaultMessage();
            
            // If message is in {key} format, try to translate with proper arguments
            if (message != null && message.startsWith("{") && message.endsWith("}")) {
                String messageKey = message.substring(1, message.length() - 1);
                try {
                    // Get constraint annotation attributes for parameter substitution
                    Object[] messageArguments = ValidationArgumentProcessor.processArguments(error);
                    log.debug("Original error arguments: {}", Arrays.toString(error.getArguments()));
                    log.debug("Processed message arguments: {}", Arrays.toString(messageArguments));
                    message = messageSource.getMessage(messageKey, messageArguments, currentLocale);
                    log.debug("Translated validation message key '{}' with args {} to: '{}'", messageKey, Arrays.toString(messageArguments), message);
                } catch (Exception e) {
                    log.debug("Failed to translate validation message key '{}': {}", messageKey, e.getMessage());
                    // Keep the original message if translation fails
                }
            }
            
            fieldError.put("message", message);
            
            log.debug("Field validation - Field: {}, Message: {}", error.getField(), message);
            errors.add(fieldError);
        });
        
        T errorResponse = responseFactory.createErrorResponse(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VAL_VALIDATION_FAILED.getCode(),
            getLocalizedMessage("error.val.validation.failed", "Validation failed"),
            errors,
            request.getRequestURI()
        );
        
        // Add trace ID to response header and MDC for logging
        addTraceIdToResponse(errorResponse, request);
        
        // Log validation errors as WARN level without stack trace
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            log.warn("Validation failed - TraceId: {}, Path: {}, Fields: {}", 
                traceId, request.getRequestURI(), 
                errors.stream().map(error -> error.get("field")).collect(java.util.stream.Collectors.toList()));
        }
        
        return errorResponse;
    }



    /**
     * Translate validation message from {key} format to localized message with parameter substitution
     */
    @SuppressWarnings("unused")
    private String translateValidationMessage(String originalMessage, Object[] messageArguments) {
        if (originalMessage == null) {
            return null;
        }
        
        // Check if message is in {key} format
        if (originalMessage.startsWith("{") && originalMessage.endsWith("}")) {
            String messageKey = originalMessage.substring(1, originalMessage.length() - 1);
            try {
                // Try to get the message without arguments first (for simple messages)
                String resolvedMessage = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
                log.debug("Translated validation message key '{}' to: '{}'", messageKey, resolvedMessage);
                return resolvedMessage;
            } catch (Exception e) {
                log.debug("Failed to translate validation message key '{}': {}", messageKey, e.getMessage());
                return originalMessage;
            }
        }
        
        // If not in {key} format, return as is
        return originalMessage;
    }


    /**
     * Get localized message with fallback to English default
     */
    private String getLocalizedMessage(String messageKey, String englishDefault) {
        try {
            Locale currentLocale = LocaleContextHolder.getLocale();
            String resolvedMessage = messageSource.getMessage(messageKey, null, currentLocale);
            log.debug("Resolved message key '{}' for locale '{}': '{}'", messageKey, currentLocale, resolvedMessage);
            return resolvedMessage;
        } catch (Exception e) {
            Locale currentLocale = LocaleContextHolder.getLocale();
            log.debug("Failed to resolve message key '{}' for locale '{}', using default: '{}'. Error: {}", 
                messageKey, currentLocale, englishDefault, e.getMessage());
            return englishDefault;
        }
    }

    @ExceptionHandler(AccessDeniedException.class)
    @Order(2)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public T handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for request: {} - {}", request.getRequestURI(), ex.getMessage());
        
        String message = messageSource.getMessage(
            "error.authz.insufficient.permissions", 
            null, 
            "Access denied", 
            LocaleContextHolder.getLocale()
        );
        
        String detail = messageSource.getMessage(
            "error.insufficientPermissions.detail", 
            null, 
            "You do not have permission to access the requested resource", 
            LocaleContextHolder.getLocale()
        );
        
        T errorResponse = responseFactory.createErrorResponse(
            HttpStatus.FORBIDDEN,
            ErrorCode.AUTHZ_INSUFFICIENT_PERMISSIONS.getCode(),
            message,
            detail,
            request.getRequestURI()
        );
        
        // Add trace ID to response header and MDC for logging
        addTraceIdToResponse(errorResponse, request);
        
        return errorResponse;
    }

    @ExceptionHandler(AuthenticationException.class)
    @Order(3)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public T handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed for request: {} - {}", request.getRequestURI(), ex.getMessage());
        
        String message = messageSource.getMessage(
            "error.auth.authentication.required", 
            null, 
            "Authentication required", 
            LocaleContextHolder.getLocale()
        );
        
        String detail = messageSource.getMessage(
            "error.authenticationFailed.detail", 
            null, 
            "Login is required or token is invalid", 
            LocaleContextHolder.getLocale()
        );
        
        T errorResponse = responseFactory.createErrorResponse(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.AUTH_AUTHENTICATION_REQUIRED.getCode(),
            message,
            detail,
            request.getRequestURI()
        );
        
        // Add trace ID to response header and MDC for logging
        addTraceIdToResponse(errorResponse, request);
        
        return errorResponse;
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @Order(4)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    public T handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex, HttpServletRequest request) {
        log.warn("Async request timeout for: {}", request.getRequestURI());
        
        String message = messageSource.getMessage(
            "error.gen.timeout", 
            null, 
            "Request timeout", 
            LocaleContextHolder.getLocale()
        );
        
        T errorResponse = responseFactory.createErrorResponse(
            HttpStatus.REQUEST_TIMEOUT,
            ErrorCode.GEN_TIMEOUT.getCode(),
            message,
            "The request took too long to process",
            request.getRequestURI()
        );
        
        // Add trace ID to response header and MDC for logging
        addTraceIdToResponse(errorResponse, request);
        
        return errorResponse;
    }

    @SuppressWarnings("unused")
    @ExceptionHandler(Exception.class)
    @Order(Integer.MAX_VALUE)
    public T handleException(Exception ex, HttpServletRequest request) {
        log.error("Exception occurred: ", ex);
        
        // Check for nested Searchable exceptions first
        Throwable rootCause = getRootCause(ex);
        
        // Handle Searchable exceptions by checking package name
        String rootClassFullName = rootCause.getClass().getName();
        if (rootClassFullName.startsWith("dev.simplecore.searchable.core.exception")) {
            return handleSearchableException(rootCause, request);
        }

        // If it's already a SimpliXGeneralException, handle it as is
        if (ex instanceof SimpliXGeneralException) {
            return handleSimpliXGeneralException((SimpliXGeneralException) ex, request);
        }

        // Determine HTTP status code and message
        HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getMessage();

        // Include detailed error information only in development environments
        String errorDetail;
        if (isDebugMode()) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            errorDetail = String.format("%s: %s", ex.getClass().getName(), ex.getMessage());
        } else {
            errorDetail = "An error occurred while processing your request";
        }

        String errorType = "SimpliXGeneralException";

        // Map exception types to error codes
        ErrorCode errorCode;
        if (ex instanceof IllegalArgumentException) {
            errorCode = ErrorCode.VAL_INVALID_PARAMETER;
        } else if (ex instanceof SecurityException) {
            errorCode = ErrorCode.AUTH_AUTHENTICATION_REQUIRED;
        } else if (ex instanceof IllegalStateException) {
            errorCode = ErrorCode.GEN_CONFLICT;
        } else if (ex instanceof UnsupportedOperationException) {
            errorCode = ErrorCode.GEN_METHOD_NOT_ALLOWED;
        } else {
            errorCode = ErrorCode.GEN_INTERNAL_SERVER_ERROR;
        }

        // Process message
        String localizedMessage = messageSource.getMessage(
            "error." + errorCode.getCode().toLowerCase().replace("_", "."),
            null,
            errorCode.getDefaultMessage(),
            LocaleContextHolder.getLocale()
        );

        // Wrap the exception in a SimpliXGeneralException
        SimpliXGeneralException wrappedException = new SimpliXGeneralException(
            errorCode,
            localizedMessage,
            ex,
            errorDetail
        );

        return handleSimpliXGeneralException(wrappedException, request);
    }
    
    /**
     * Handle Searchable library exceptions
     */
    protected T handleSearchableException(Throwable rootCause, HttpServletRequest request) {
        String simpleClassName = rootCause.getClass().getSimpleName();
        log.warn("Searchable exception occurred: {} - {}", simpleClassName, rootCause.getMessage());
        
        // Determine specific error code based on exception type
        ErrorCode errorCode = determineSearchableErrorCode(rootCause);
        
        String message = messageSource.getMessage(
            "error." + errorCode.getCode().toLowerCase().replace("_", "."), 
            null, 
            errorCode.getDefaultMessage(), 
            LocaleContextHolder.getLocale()
        );
        
        T errorResponse = responseFactory.createErrorResponse(
            errorCode.getHttpStatus(),
            errorCode.getCode(),
            message,
            rootCause.getMessage(),
            request.getRequestURI()
        );
        
        // Add trace ID to response header and MDC for logging
        addTraceIdToResponse(errorResponse, request);
        
        return errorResponse;
    }
    
    /**
     * Get the root cause of an exception
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
    
    /**
     * Determine the appropriate error code for Searchable exceptions
     */
    private ErrorCode determineSearchableErrorCode(Throwable rootCause) {
        String simpleClassName = rootCause.getClass().getSimpleName();
        
        // Try to determine by class name
        if (simpleClassName.contains("Validation")) {
            return ErrorCode.SEARCH_INVALID_PARAMETER;
        } else if (simpleClassName.contains("Sort")) {
            return ErrorCode.SEARCH_INVALID_SORT_FIELD;
        } else if (simpleClassName.contains("Filter") || simpleClassName.contains("Operator")) {
            return ErrorCode.SEARCH_INVALID_FILTER_OPERATOR;
        } else if (simpleClassName.contains("Parse") || simpleClassName.contains("Syntax")) {
            return ErrorCode.SEARCH_INVALID_QUERY_SYNTAX;
        }
        
        // Try to determine by message content
        String message = rootCause.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("sort")) {
                return ErrorCode.SEARCH_INVALID_SORT_FIELD;
            } else if (lowerMessage.contains("filter") || lowerMessage.contains("operator")) {
                return ErrorCode.SEARCH_INVALID_FILTER_OPERATOR;
            } else if (lowerMessage.contains("parse") || lowerMessage.contains("syntax")) {
                return ErrorCode.SEARCH_INVALID_QUERY_SYNTAX;
            }
        }
        
        // Default to invalid search parameter
        return ErrorCode.SEARCH_INVALID_PARAMETER;
    }

    /**
     * Check if application is running in debug mode
     */
    private boolean isDebugMode() {
        // Check Spring profiles or system properties
        String activeProfiles = System.getProperty("spring.profiles.active", "");
        return activeProfiles.contains("dev") || activeProfiles.contains("local") || activeProfiles.contains("debug");
    }

    /**
     * Add trace ID to response header and MDC for logging
     */
    private void addTraceIdToResponse(T errorResponse, HttpServletRequest request) {
        try {
            HttpServletResponse response = ((org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getResponse();
            if (response != null) {
                String traceId = MDC.get("traceId");
                if (traceId != null && !traceId.isEmpty()) {
                    response.setHeader("X-Trace-Id", traceId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to add trace ID to response: {}", e.getMessage());
        }
    }

    /**
     * Interface for creating response objects
     */
    public interface ResponseFactory<T> {
        T createErrorResponse(HttpStatus statusCode, String errorType, String message, Object detail, String path);
    }

    /**
     * Default implementation using SimpliXApiResponse
     */
    private static class DefaultResponseFactory implements ResponseFactory<SimpliXApiResponse<Object>> {
        private static final Logger log = LoggerFactory.getLogger(DefaultResponseFactory.class);
        
        @Override
        public SimpliXApiResponse<Object> createErrorResponse(HttpStatus statusCode, String errorType, String message, Object detail, String path) {
            // Use error code based on HTTP status and error type
            String errorCode = errorType != null ? errorType : statusCode.name();
            SimpliXApiResponse<Object> response = SimpliXApiResponse.error(message, errorCode, detail);
            
            // Log error with trace ID from MDC
            String traceId = MDC.get("traceId");
            if (traceId != null && !traceId.isEmpty()) {
                log.error("Error response created - TraceId: {}, Code: {}, Path: {}, Message: {}", 
                    traceId, errorCode, path, message);
            }
            
            return response;
        }
    }
}