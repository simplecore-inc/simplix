package dev.simplecore.simplix.messaging.core;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for publishing messages to a channel.
 *
 * <p>Implementations delegate to the active {@link dev.simplecore.simplix.messaging.broker.BrokerStrategy}.
 */
public interface MessagePublisher {

    /**
     * Publish a message synchronously (fire-and-forget).
     *
     * @param message the message to publish
     * @return the publish result containing the broker-assigned record ID
     */
    PublishResult publish(Message<?> message);

    /**
     * Publish a message asynchronously.
     *
     * @param message the message to publish
     * @return a future that completes with the publish result
     */
    CompletableFuture<PublishResult> publishAsync(Message<?> message);

    /**
     * Check whether the publisher is currently available.
     *
     * @return {@code true} if the underlying broker is ready
     */
    boolean isAvailable();
}
