package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import io.nats.client.Message;

/**
 * {@link MessageAcknowledgment} implementation backed by a NATS JetStream message.
 *
 * <p>{@link #ack()} acknowledges the message; {@link #nack(boolean)} requests
 * redelivery via {@code Message.nak()}; {@link #reject(String)} terminates the
 * message via {@code Message.term()} so the broker stops redelivering it
 * regardless of {@code MaxDeliver}.
 */
public final class NatsMessageAcknowledgment implements MessageAcknowledgment {

    private final Message natsMsg;
    private final InFlightTracker tracker;
    private final String messageId;

    public NatsMessageAcknowledgment(Message natsMsg, InFlightTracker tracker, String messageId) {
        this.natsMsg = natsMsg;
        this.tracker = tracker;
        this.messageId = messageId;
    }

    @Override
    public void ack() {
        natsMsg.ack();
        tracker.remove(messageId);
    }

    @Override
    public void nack(boolean requeue) {
        natsMsg.nak();
    }

    @Override
    public void reject(String reason) {
        natsMsg.term();
        tracker.remove(messageId);
    }
}
