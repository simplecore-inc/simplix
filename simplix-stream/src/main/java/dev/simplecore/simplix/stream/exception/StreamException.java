package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;

/**
 * Base exception for stream module errors.
 */
public class StreamException extends SimpliXGeneralException {

    public StreamException(ErrorCode errorCode, String message) {
        super(errorCode, message, null);
    }

    public StreamException(ErrorCode errorCode, String message, Object detail) {
        super(errorCode, message, detail);
    }

    public StreamException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause, null);
    }

    public StreamException(ErrorCode errorCode, String message, Throwable cause, Object detail) {
        super(errorCode, message, cause, detail);
    }
}
