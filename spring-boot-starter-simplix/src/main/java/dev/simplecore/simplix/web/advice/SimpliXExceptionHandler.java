package dev.simplecore.simplix.web.advice;

import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *     private LocalDateTime timestamp;
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
 *         response.setTimestamp(LocalDateTime.now());
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
        return responseFactory.createErrorResponse(
                ex.getStatusCode(),
                "SimpliXGeneralException",
                ex.getMessage(),
                ex.getDetail(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @Order(1)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public T handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            Map<String, String> fieldError = new HashMap<>();
            fieldError.put("field", error.getField());
            fieldError.put("message", error.getDefaultMessage());
            errors.add(fieldError);
        });
        
        return responseFactory.createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "ValidationException",
            "Validation failed",
            errors,  // 객체 그대로 전달
            request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    @Order(Integer.MAX_VALUE)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public T handleException(Exception ex, HttpServletRequest request) {
        log.error("Exception occurred: ", ex);
        
        // 이미 SimpliXGeneralException인 경우 그대로 처리
        if (ex instanceof SimpliXGeneralException) {
            return handleSimpliXGeneralException((SimpliXGeneralException) ex, request);
        }

        // HTTP 상태 코드와 메시지 결정
        HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getMessage();
        // 상세 에러 정보 수집
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String error = String.format("%s: %s\n%s", 
            ex.getClass().getName(), 
            ex.getMessage(),
            sw.toString()
        );

        String errorType = "SimpliXGeneralException";
            
        // 예외 타입별 상태 코드와 메시지 매핑
        if (ex instanceof IllegalArgumentException) {
            errorType = "IllegalArgumentException";
            statusCode = HttpStatus.BAD_REQUEST;
            message = messageSource.getMessage("error.illegal_argument", null, 
                "Invalid argument", LocaleContextHolder.getLocale());
        } else if (ex instanceof SecurityException) {
            errorType = "IllegalArgumentException";
            statusCode = HttpStatus.UNAUTHORIZED;
            message = messageSource.getMessage("error.unauthorized", null, 
                "Unauthorized", LocaleContextHolder.getLocale());
        } else {
            errorType = "SimpliXGeneralException";
            // 기본 메시지가 없는 경우 처리
            message = message != null ? message : 
                messageSource.getMessage("error.internal", null, 
                "Internal server error", LocaleContextHolder.getLocale());
        }

        
        // 예외를 SimpliXGeneralException으로 래핑
        SimpliXGeneralException wrappedException = new SimpliXGeneralException(
            message != null ? message : "An error occurred",
            ex, 
            errorType,
            statusCode, 
            error, 
            request.getRequestURI()
        );
        
        return handleSimpliXGeneralException(wrappedException, request);
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
        @Override
        public SimpliXApiResponse<Object> createErrorResponse(HttpStatus statusCode, String errorType, String message, Object detail, String path) {
            return SimpliXApiResponse.error(message, errorType, detail);
        }
    }
}