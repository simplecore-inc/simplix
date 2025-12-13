package dev.simplecore.simplix.auth.jwe.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;

/**
 * Exception for JWE key management errors.
 * Thrown when key loading, decryption, rotation, or provider operations fail.
 */
public class JweKeyException extends SimpliXGeneralException {

    public JweKeyException(String message) {
        super(ErrorCode.GEN_INTERNAL_SERVER_ERROR, message, null);
    }

    public JweKeyException(String message, Throwable cause) {
        super(ErrorCode.GEN_INTERNAL_SERVER_ERROR, message, cause, null);
    }

    public JweKeyException(ErrorCode errorCode, String message) {
        super(errorCode, message, null);
    }

    public JweKeyException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause, null);
    }
}
