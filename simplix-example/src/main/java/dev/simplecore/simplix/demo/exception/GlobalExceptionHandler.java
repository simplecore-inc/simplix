package dev.simplecore.simplix.demo.exception;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Primary
@Getter
public class GlobalExceptionHandler<T> extends SimpliXExceptionHandler<SimpliXApiResponse<T>> {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        super(messageSource, objectMapper);
        this.messageSource = messageSource;
    }

} 