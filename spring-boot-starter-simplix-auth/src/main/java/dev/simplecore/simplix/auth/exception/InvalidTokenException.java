package dev.simplecore.simplix.auth.exception;

import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends SimpliXGeneralException {
    
    public InvalidTokenException(String message, Throwable cause, HttpStatus statusCode, String error, String path) {
        super(message, cause, "InvalidTokenException", statusCode, error, path);

    }

    public InvalidTokenException(String message) {
        super(message, "InvalidTokenException");
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause, "InvalidTokenException");
    }
}