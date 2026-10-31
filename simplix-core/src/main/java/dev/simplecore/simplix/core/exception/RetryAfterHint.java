package dev.simplecore.simplix.core.exception;

/**
 * An exception that refuses a request for now and knows when to ask again.
 *
 * <p>{@code SimpliXExceptionHandler} writes {@link #retryAfterSeconds()} to the {@code Retry-After}
 * header of the error response when it is above zero, so a client backs off by the value the
 * server chose rather than by a guess.
 */
public interface RetryAfterHint {

    /**
     * @return seconds the client waits before sending the request again, zero when waiting does
     *         not help
     */
    int retryAfterSeconds();
}
