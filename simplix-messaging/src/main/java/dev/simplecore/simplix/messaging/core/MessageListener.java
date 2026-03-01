package dev.simplecore.simplix.messaging.core;

/**
 * Functional interface for receiving messages from a channel.
 *
 * @param <T> the payload type
 */
@FunctionalInterface
public interface MessageListener<T> {

    /**
     * Called when a message is received.
     *
     * @param message        the received message
     * @param acknowledgment the acknowledgment handle for this message
     */
    void onMessage(Message<T> message, MessageAcknowledgment acknowledgment);
}
