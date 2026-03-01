package dev.simplecore.simplix.messaging.core;

/**
 * Handle for acknowledging or rejecting a consumed message.
 *
 * <p>Implementations are provided by the broker strategy and are bound to
 * a specific message within a consumer group context.
 */
public interface MessageAcknowledgment {

    /**
     * Acknowledge successful processing.
     * The message is removed from the pending entries list.
     */
    void ack();

    /**
     * Reject the message, optionally requesting requeue for retry.
     *
     * @param requeue if {@code true}, the message may be redelivered to another consumer
     */
    void nack(boolean requeue);

    /**
     * Reject the message and route it to the dead letter channel.
     * No further retry attempts will be made.
     *
     * @param reason the reason for rejection
     */
    void reject(String reason);

    /**
     * No-op acknowledgment for fire-and-forget or auto-ack scenarios.
     */
    MessageAcknowledgment NOOP = new MessageAcknowledgment() {
        @Override
        public void ack() {
            // no-op
        }

        @Override
        public void nack(boolean requeue) {
            // no-op
        }

        @Override
        public void reject(String reason) {
            // no-op
        }
    };
}
