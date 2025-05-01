package dev.simplecore.simplix.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SimpliXGeneralException extends RuntimeException {
    
    private String errorType = "SimpliXGeneralException";
    
    private HttpStatus statusCode = HttpStatus.BAD_REQUEST;

    private Object detail = "";
    
    private String path = "";

    public SimpliXGeneralException(String message, Throwable cause, String errorType, HttpStatus statusCode, Object detail, String path) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
        this.detail = detail;
        this.path = path;
    }


    public SimpliXGeneralException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    public SimpliXGeneralException(String message, Throwable cause, String errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public SimpliXGeneralException(String message, Throwable cause, String errorType, HttpStatus statusCode) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

}
