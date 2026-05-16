package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NatsMessageAcknowledgmentTest {

    private Message natsMsg;
    private InFlightTracker tracker;
    private NatsMessageAcknowledgment ack;

    @BeforeEach
    void setUp() {
        natsMsg = mock(Message.class);
        tracker = new InFlightTracker();
        tracker.put("m1", natsMsg);
        ack = new NatsMessageAcknowledgment(natsMsg, tracker, "m1");
    }

    @Test
    void ack_callsMessageAck_andRemovesFromTracker() {
        ack.ack();
        verify(natsMsg).ack();
        assertThat(tracker.size()).isEqualTo(0);
    }

    @Test
    void nack_callsMessageNak_andKeepsInTracker() {
        ack.nack(true);
        verify(natsMsg).nak();
        assertThat(tracker.size()).isEqualTo(1);
    }

    @Test
    void reject_callsMessageTerm_andRemovesFromTracker() {
        ack.reject("bad");
        verify(natsMsg).term();
        assertThat(tracker.size()).isEqualTo(0);
    }
}
