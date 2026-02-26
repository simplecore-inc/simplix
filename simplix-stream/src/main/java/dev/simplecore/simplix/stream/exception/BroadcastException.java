package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;

/**
 * Exception thrown when message broadcast fails.
 */
public class BroadcastException extends StreamException {

    public BroadcastException(String message) {
        super(ErrorCode.GEN_INTERNAL_SERVER_ERROR,
                "Broadcast failed: " + message,
                null);
    }

    public BroadcastException(String message, Throwable cause) {
        super(ErrorCode.GEN_INTERNAL_SERVER_ERROR,
                "Broadcast failed: " + message,
                cause,
                null);
    }
}
