package dev.simplecore.simplix.demo.exception;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Application-specific exception handler.
 * 
 * Currently, all common exceptions are handled by the library-level SimpliXExceptionHandler:
 * - Spring Security exceptions (AccessDeniedException, AuthenticationException)
 * - Validation exceptions
 * - Searchable library exceptions
 * - General exceptions
 * 
 * This class can be used to override specific exception handling behavior
 * or add application-specific exception handlers.
 */
@RestControllerAdvice
@Primary
@Getter
public class GlobalExceptionHandler<T> extends SimpliXExceptionHandler<SimpliXApiResponse<T>> {

    public GlobalExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        super(messageSource, objectMapper);
    }
    
    // Add application-specific exception handlers here if needed
    // Example:
    // @ExceptionHandler(CustomBusinessException.class)
    // public SimpliXApiResponse<T> handleCustomException(CustomBusinessException ex, HttpServletRequest request) {
    //     return SimpliXApiResponse.error(ex.getMessage(), "CUSTOM_ERROR", ex.getDetail());
    // }
} 