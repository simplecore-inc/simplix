package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;

/**
 * Exception thrown when subscription limit is exceeded.
 */
public class SubscriptionLimitExceededException extends StreamException {

    public SubscriptionLimitExceededException(int current, int max) {
        super(ErrorCode.BIZ_QUOTA_EXCEEDED,
                String.format("Subscription limit exceeded: %d/%d", current, max),
                new LimitDetail(current, max));
    }

    public record LimitDetail(int current, int max) {
    }
}
