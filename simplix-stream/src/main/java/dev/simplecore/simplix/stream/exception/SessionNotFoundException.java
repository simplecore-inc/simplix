package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;

/**
 * Exception thrown when a stream session is not found.
 */
public class SessionNotFoundException extends StreamException {

    public SessionNotFoundException(String sessionId) {
        super(ErrorCode.GEN_NOT_FOUND,
                "Stream session not found: " + sessionId,
                sessionId);
    }
}
