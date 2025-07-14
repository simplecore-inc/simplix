package dev.simplecore.simplix.demo.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Application-specific exception handler.
 * 
 * Common exceptions are handled by the library-level SimpliXExceptionHandler:
 * - Spring Security exceptions (AccessDeniedException, AuthenticationException)
 * - Validation exceptions (MethodArgumentNotValidException)
 * - Searchable library exceptions
 * - General exceptions
 * 
 * This class can be used to add application-specific exception handlers.
 */
@RestControllerAdvice
@Primary
@Getter
@Slf4j
public class GlobalExceptionHandler<T> extends SimpliXExceptionHandler<SimpliXApiResponse<T>> {

    public GlobalExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        super(messageSource, objectMapper);
        log.info("GlobalExceptionHandler initialized - validation messages will be translated by SimpliXExceptionHandler");
    }
    
    // Add application-specific exception handlers here if needed
    // Example:
    // @ExceptionHandler(CustomBusinessException.class)
    // public SimpliXApiResponse<T> handleCustomException(CustomBusinessException ex, HttpServletRequest request) {
    //     return SimpliXApiResponse.error(ex.getMessage(), "CUSTOM_ERROR", ex.getDetail());
    // }
} 