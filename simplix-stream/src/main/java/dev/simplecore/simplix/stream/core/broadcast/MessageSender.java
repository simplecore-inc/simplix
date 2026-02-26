package dev.simplecore.simplix.stream.core.broadcast;

import dev.simplecore.simplix.stream.core.model.StreamMessage;

/**
 * Interface for sending messages to a client session.
 * <p>
 * Implemented by transport-specific session classes (SSE, WebSocket).
 */
public interface MessageSender {

    /**
     * Send a message to the client.
     *
     * @param message the message to send
     * @return true if sent successfully
     */
    boolean send(StreamMessage message);

    /**
     * Check if the sender is still active.
     *
     * @return true if active
     */
    boolean isActive();

    /**
     * Close the sender.
     */
    void close();
}
