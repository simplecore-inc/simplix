package dev.simplecore.simplix.auth.exception;

import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.exception.ErrorCode;
import lombok.Getter;

@Getter
public class TokenValidationException extends SimpliXGeneralException {
    
    public TokenValidationException(String message, String detail) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message, detail);
    }
    
    public TokenValidationException(ErrorCode errorCode, String message, String detail) {
        super(errorCode, message, detail);
    }
    
    public TokenValidationException(String message, Throwable cause, String detail) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message, cause, detail);
    }
} 