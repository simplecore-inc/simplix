package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NatsJetStreamPublisherTest {

    private JetStream jetStream;
    private NatsConsumerGroupManager groupManager;
    private MessagingProperties properties;
    private NatsJetStreamPublisher publisher;

    @BeforeEach
    void setUp() {
        jetStream = mock(JetStream.class);
        groupManager = mock(NatsConsumerGroupManager.class);
        when(groupManager.resolveStreamName("orders")).thenReturn("simplix-orders");
        when(groupManager.resolveSubject("orders")).thenReturn("simplix.orders");
        properties = new MessagingProperties();
        publisher = new NatsJetStreamPublisher(jetStream, groupManager, properties);
    }

    @Test
    void send_publishesToResolvedSubject() throws Exception {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(42L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        publisher.send("orders", "hello".getBytes(), MessageHeaders.empty());

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(msgCaptor.capture(), any(PublishOptions.class));
        assertThat(msgCaptor.getValue().getSubject()).isEqualTo("simplix.orders");
    }

    @Test
    void send_addsNatsMsgIdHeader_fromMessageHeader() throws Exception {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(1L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        MessageHeaders headers = MessageHeaders.empty()
                .with(MessageHeaders.MESSAGE_ID, "test-msg-id-123");

        publisher.send("orders", "payload".getBytes(), headers);

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(msgCaptor.capture(), any(PublishOptions.class));

        Headers natsHeaders = msgCaptor.getValue().getHeaders();
        assertThat(natsHeaders).isNotNull();
        List<String> natsMsgId = natsHeaders.get("Nats-Msg-Id");
        assertThat(natsMsgId).containsExactly("test-msg-id-123");
    }

    @Test
    void send_returnsRecordIdAsStreamDashSeqno() throws Exception {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(42L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        PublishResult result = publisher.send("orders", "data".getBytes(), MessageHeaders.empty());

        assertThat(result.recordId()).isEqualTo("simplix-orders-42");
    }

    @Test
    void send_propagatesAllHeaders() throws Exception {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(7L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        MessageHeaders headers = MessageHeaders.empty()
                .with(MessageHeaders.CORRELATION_ID, "corr-abc")
                .with(MessageHeaders.SOURCE, "order-service");

        publisher.send("orders", "payload".getBytes(), headers);

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(msgCaptor.capture(), any(PublishOptions.class));

        Headers natsHeaders = msgCaptor.getValue().getHeaders();
        assertThat(natsHeaders).isNotNull();
        assertThat(natsHeaders.getFirst(MessageHeaders.CORRELATION_ID)).isEqualTo("corr-abc");
        assertThat(natsHeaders.getFirst(MessageHeaders.SOURCE)).isEqualTo("order-service");
    }

    @Test
    void send_throwsIllegalStateException_onJetStreamApiException() throws Exception {
        JetStreamApiException apiEx = mock(JetStreamApiException.class);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenThrow(apiEx);

        assertThatThrownBy(() ->
                publisher.send("orders", "data".getBytes(), MessageHeaders.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(apiEx);
    }

    @Test
    void send_throwsIllegalStateException_onIOException() throws Exception {
        IOException ioEx = new IOException("connection reset");
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenThrow(ioEx);

        assertThatThrownBy(() ->
                publisher.send("orders", "data".getBytes(), MessageHeaders.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(ioEx);
    }

    // ---------------------------------------------------------------
    // Follow-up: auto-message-id + duplicate detection
    // ---------------------------------------------------------------

    @Test
    void send_doesNotAddNatsMsgId_whenAutoMessageIdDisabledAndCallerOmits() throws Exception {
        // Default: publisher.auto-message-id = false (matches historical behavior)
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(1L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        publisher.send("orders", "payload".getBytes(), MessageHeaders.empty());

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(msgCaptor.capture(), any(PublishOptions.class));
        Headers natsHeaders = msgCaptor.getValue().getHeaders();
        assertThat(natsHeaders == null || natsHeaders.get("Nats-Msg-Id") == null
                || natsHeaders.get("Nats-Msg-Id").isEmpty()).isTrue();
    }

    @Test
    void send_assignsFreshMessageId_whenAutoMessageIdEnabledAndCallerOmits() throws Exception {
        properties.getPublisher().setAutoMessageId(true);
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(1L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        publisher.send("orders", "payload".getBytes(), MessageHeaders.empty());

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(msgCaptor.capture(), any(PublishOptions.class));
        Headers natsHeaders = msgCaptor.getValue().getHeaders();
        assertThat(natsHeaders).isNotNull();
        List<String> auto = natsHeaders.get("Nats-Msg-Id");
        assertThat(auto).hasSize(1);
        assertThat(auto.get(0)).isNotBlank();
        assertThat(natsHeaders.getFirst(MessageHeaders.MESSAGE_ID)).isEqualTo(auto.get(0));
    }

    @Test
    void send_preservesCallerProvidedId_whenAutoMessageIdEnabled() throws Exception {
        properties.getPublisher().setAutoMessageId(true);
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(1L);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        MessageHeaders headers = MessageHeaders.empty()
                .with(MessageHeaders.MESSAGE_ID, "caller-id-42");
        publisher.send("orders", "payload".getBytes(), headers);

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(msgCaptor.capture(), any(PublishOptions.class));
        Headers natsHeaders = msgCaptor.getValue().getHeaders();
        assertThat(natsHeaders.getFirst("Nats-Msg-Id")).isEqualTo("caller-id-42");
    }

    @Test
    void send_returnsDuplicateTrue_whenAckIsDuplicate() throws Exception {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(7L);
        when(ack.isDuplicate()).thenReturn(true);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        PublishResult result = publisher.send("orders", "data".getBytes(),
                MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-1"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.recordId()).isEqualTo("simplix-orders-7");
    }

    @Test
    void send_returnsDuplicateFalse_whenAckIsNotDuplicate() throws Exception {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getStream()).thenReturn("simplix-orders");
        when(ack.getSeqno()).thenReturn(8L);
        when(ack.isDuplicate()).thenReturn(false);
        when(jetStream.publish(any(Message.class), any(PublishOptions.class))).thenReturn(ack);

        PublishResult result = publisher.send("orders", "data".getBytes(),
                MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-2"));

        assertThat(result.duplicate()).isFalse();
    }
}
