package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;

/**
 * Exception thrown when a stream session has expired.
 */
public class SessionExpiredException extends StreamException {

    public SessionExpiredException(String sessionId) {
        super(ErrorCode.AUTH_SESSION_EXPIRED,
                "Stream session expired: " + sessionId,
                sessionId);
    }
}
