package dev.simplecore.simplix.auth.exception;

import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import lombok.Getter;

@Getter
public class TokenValidationException extends SimpliXGeneralException {
    private final String detail;

    public TokenValidationException(String message, String detail) {
        super(message, "InvalidTokenException");
        this.detail = detail;
    }
} 