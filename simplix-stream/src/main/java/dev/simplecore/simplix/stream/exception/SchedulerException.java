package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;

/**
 * Exception thrown when scheduler execution fails.
 */
public class SchedulerException extends StreamException {

    public SchedulerException(SubscriptionKey key, String message) {
        super(ErrorCode.GEN_INTERNAL_SERVER_ERROR,
                "Scheduler error for " + key.toKeyString() + ": " + message,
                key.toKeyString());
    }

    public SchedulerException(SubscriptionKey key, String message, Throwable cause) {
        super(ErrorCode.GEN_INTERNAL_SERVER_ERROR,
                "Scheduler error for " + key.toKeyString() + ": " + message,
                cause,
                key.toKeyString());
    }
}
