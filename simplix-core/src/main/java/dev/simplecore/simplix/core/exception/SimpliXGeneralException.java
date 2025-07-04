package dev.simplecore.simplix.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SimpliXGeneralException extends RuntimeException {
    
    private ErrorCode errorCode;
    
    private HttpStatus statusCode;
    
    private Object detail;
    
    private String path;

    // Primary constructor using ErrorCode
    public SimpliXGeneralException(ErrorCode errorCode, String message, Object detail) {
        super(message != null ? message : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.statusCode = errorCode.getHttpStatus();
        this.detail = detail;
        this.path = "";
    }

    public SimpliXGeneralException(ErrorCode errorCode, String message, Throwable cause, Object detail) {
        super(message != null ? message : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.statusCode = errorCode.getHttpStatus();
        this.detail = detail;
        this.path = "";
    }

    // Legacy constructors for backward compatibility
    @Deprecated
    public SimpliXGeneralException(String message, Throwable cause, String errorType, HttpStatus statusCode, Object detail, String path) {
        super(message, cause);
        this.errorCode = ErrorCode.fromCode(errorType);
        this.statusCode = statusCode;
        this.detail = detail;
        this.path = path;
    }

    @Deprecated
    public SimpliXGeneralException(String message, String errorType) {
        super(message);
        this.errorCode = ErrorCode.fromCode(errorType);
        this.statusCode = this.errorCode.getHttpStatus();
    }

    @Deprecated
    public SimpliXGeneralException(String message, Throwable cause, String errorType) {
        super(message, cause);
        this.errorCode = ErrorCode.fromCode(errorType);
        this.statusCode = this.errorCode.getHttpStatus();
    }

    @Deprecated
    public SimpliXGeneralException(String message, Throwable cause, String errorType, HttpStatus statusCode) {
        super(message, cause);
        this.errorCode = ErrorCode.fromCode(errorType);
        this.statusCode = statusCode;
    }

    // Backward compatibility getter
    @Deprecated
    public String getErrorType() {
        return errorCode != null ? errorCode.getCode() : "INTERNAL_SERVER_ERROR";
    }

}
