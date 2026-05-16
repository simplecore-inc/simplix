package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.impl.Headers;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NatsJetStreamPullSubscriberTest {

    private JetStream jetStream;
    private NatsConsumerGroupManager groupManager;
    private JetStreamSubscription jsSub;
    private NatsJetStreamPullSubscriber subscriber;

    @BeforeEach
    void setUp() throws Exception {
        jetStream = mock(JetStream.class);
        groupManager = mock(NatsConsumerGroupManager.class);
        jsSub = mock(JetStreamSubscription.class);

        when(groupManager.resolveStreamName("orders")).thenReturn("simplix-orders");
        when(groupManager.resolveSubject("orders")).thenReturn("simplix.orders");
        when(jetStream.subscribe(any(String.class), any(PullSubscribeOptions.class))).thenReturn(jsSub);

        // Default: fetch returns empty so poll loop spins without dispatching
        when(jsSub.fetch(anyInt(), any(Duration.class))).thenReturn(Collections.emptyList());

        subscriber = new NatsJetStreamPullSubscriber(jetStream, groupManager);
    }

    // ---------------------------------------------------------------
    // Test 1: subscribe calls ensureStream and ensureConsumerGroup when groupName is set
    // ---------------------------------------------------------------

    @Test
    void subscribe_ensuresStream_andEnsuresConsumerGroup_whenGroupSet() throws Exception {
        SubscribeRequest req = SubscribeRequest.builder()
                .channel("orders")
                .groupName("payments-group")
                .batchSize(5)
                .pollTimeout(Duration.ofMillis(100))
                .listener((msg, ack) -> {})
                .build();

        Subscription sub = subscriber.subscribe(req);
        sub.cancel();

        verify(groupManager).ensureStream("orders");
        verify(groupManager).ensureConsumerGroup("orders", "payments-group");
    }

    // ---------------------------------------------------------------
    // Test 2: subscribe skips ensureConsumerGroup when groupName is empty
    // ---------------------------------------------------------------

    @Test
    void subscribe_skipsConsumerGroup_whenGroupNameEmpty() throws Exception {
        SubscribeRequest req = SubscribeRequest.builder()
                .channel("orders")
                .groupName("")
                .batchSize(5)
                .pollTimeout(Duration.ofMillis(100))
                .listener((msg, ack) -> {})
                .build();

        Subscription sub = subscriber.subscribe(req);
        sub.cancel();

        verify(groupManager).ensureStream("orders");
        verify(groupManager, never()).ensureConsumerGroup(any(), any());
    }

    // ---------------------------------------------------------------
    // Test 3: polling loop calls fetch with configured batchSize and timeout
    // ---------------------------------------------------------------

    @Test
    void pollingLoop_callsFetch_withConfiguredBatchSizeAndTimeout() throws Exception {
        SubscribeRequest req = SubscribeRequest.builder()
                .channel("orders")
                .groupName("g1")
                .batchSize(7)
                .pollTimeout(Duration.ofMillis(50))
                .listener((msg, ack) -> {})
                .build();

        Subscription sub = subscriber.subscribe(req);

        // Allow the poller to call fetch at least once
        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        verify(jsSub, atLeastOnce()).fetch(eq(7), eq(Duration.ofMillis(50)));
                        return true;
                    } catch (AssertionError e) {
                        return false;
                    }
                });

        sub.cancel();

        verify(jsSub, atLeastOnce()).fetch(eq(7), eq(Duration.ofMillis(50)));
    }

    // ---------------------------------------------------------------
    // Test 4: dispatched message decodes payload and headers correctly
    // ---------------------------------------------------------------

    @Test
    void messageDispatched_decodesPayload_andHeaders() throws Exception {
        io.nats.client.Message natsMsg = mock(io.nats.client.Message.class);

        Headers natsHeaders = new Headers();
        natsHeaders.add(MessageHeaders.MESSAGE_ID, "msg-abc-123");
        natsHeaders.add(MessageHeaders.CORRELATION_ID, "corr-xyz");

        when(natsMsg.getData()).thenReturn("hello".getBytes());
        when(natsMsg.getHeaders()).thenReturn(natsHeaders);
        when(natsMsg.metaData()).thenReturn(null);
        when(natsMsg.getSubject()).thenReturn("simplix.orders");

        AtomicReference<Message<byte[]>> capturedMessage = new AtomicReference<>();
        AtomicReference<MessageAcknowledgment> capturedAck = new AtomicReference<>();

        MessageListener<byte[]> listener = (msg, ack) -> {
            capturedMessage.set(msg);
            capturedAck.set(ack);
        };

        // First call returns the real message, subsequent calls return empty
        when(jsSub.fetch(anyInt(), any(Duration.class)))
                .thenReturn(List.of(natsMsg))
                .thenReturn(Collections.emptyList());

        SubscribeRequest req = SubscribeRequest.builder()
                .channel("orders")
                .groupName("g1")
                .batchSize(10)
                .pollTimeout(Duration.ofMillis(100))
                .listener(listener)
                .build();

        Subscription sub = subscriber.subscribe(req);

        // Wait until listener is invoked
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .until(() -> capturedMessage.get() != null);

        sub.cancel();

        Message<byte[]> msg = capturedMessage.get();
        assertThat(msg).isNotNull();
        assertThat(msg.getPayload()).isEqualTo("hello".getBytes());
        assertThat(msg.getChannel()).isEqualTo("orders");
        assertThat(msg.getMessageId()).isEqualTo("msg-abc-123");
        assertThat(msg.getHeaders().get(MessageHeaders.CORRELATION_ID)).contains("corr-xyz");
    }

    // ---------------------------------------------------------------
    // Test 5: listener throws -> nak() is called on the NATS message
    // ---------------------------------------------------------------

    @Test
    void listenerThrows_callsNakOnNatsMessage() throws Exception {
        io.nats.client.Message natsMsg = mock(io.nats.client.Message.class);
        when(natsMsg.getData()).thenReturn("data".getBytes());
        when(natsMsg.getHeaders()).thenReturn(null);
        when(natsMsg.metaData()).thenReturn(null);
        when(natsMsg.getSubject()).thenReturn("simplix.orders");

        when(jsSub.fetch(anyInt(), any(Duration.class)))
                .thenReturn(List.of(natsMsg))
                .thenReturn(Collections.emptyList());

        MessageListener<byte[]> throwingListener = (msg, ack) -> {
            throw new RuntimeException("listener failure");
        };

        SubscribeRequest req = SubscribeRequest.builder()
                .channel("orders")
                .groupName("g1")
                .batchSize(10)
                .pollTimeout(Duration.ofMillis(100))
                .listener(throwingListener)
                .build();

        Subscription sub = subscriber.subscribe(req);

        // Wait for nak() to be called
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(natsMsg, atLeastOnce()).nak());

        sub.cancel();
    }

    // ---------------------------------------------------------------
    // Test 6: cancel() stops the polling loop within 1 second
    // ---------------------------------------------------------------

    @Test
    void subscriptionCancel_stopsPollingLoopWithin1Second() throws Exception {
        // fetch returns empty so loop spins but doesn't block long
        when(jsSub.fetch(anyInt(), any(Duration.class))).thenReturn(Collections.emptyList());

        SubscribeRequest req = SubscribeRequest.builder()
                .channel("orders")
                .groupName("g1")
                .batchSize(10)
                .pollTimeout(Duration.ofMillis(100))
                .listener((msg, ack) -> {})
                .build();

        Subscription sub = subscriber.subscribe(req);

        assertThat(sub.isActive()).isTrue();

        // Let the loop spin for a moment before cancelling
        Thread.sleep(100);

        sub.cancel();

        // Subscription should become inactive
        Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .until(() -> !sub.isActive());

        assertThat(sub.isActive()).isFalse();

        // unsubscribe must have been called during cancel()
        verify(jsSub, timeout(1000)).unsubscribe();
    }
}
