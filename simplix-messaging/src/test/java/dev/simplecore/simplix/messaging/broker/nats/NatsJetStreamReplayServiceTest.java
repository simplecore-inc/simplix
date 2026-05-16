package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.core.MessageListener;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NatsJetStreamReplayServiceTest {

    private JetStream jetStream;
    private NatsConsumerGroupManager groupManager;
    private NatsJetStreamReplayService replayService;

    @BeforeEach
    void setUp() {
        jetStream = mock(JetStream.class);
        groupManager = mock(NatsConsumerGroupManager.class);
        when(groupManager.resolveStreamName("orders")).thenReturn("simplix-orders");
        when(groupManager.resolveSubject("orders")).thenReturn("simplix.orders");
        replayService = new NatsJetStreamReplayService(jetStream, groupManager);
    }

    @Test
    @DisplayName("replay by ID builds ByStartSequence consumer and delivers all messages")
    void replayById_buildsByStartSequenceConsumer() throws Exception {
        JetStreamSubscription sub = mock(JetStreamSubscription.class);
        Message m1 = messageWithSeq(1L);
        Message m2 = messageWithSeq(2L);
        Message m3 = messageWithSeq(3L);
        when(sub.nextMessage(any(Duration.class))).thenReturn(m1, m2, m3, null);
        when(jetStream.subscribe(eq("simplix.orders"), any(PushSubscribeOptions.class))).thenReturn(sub);

        MessageListener<byte[]> listener = mock(MessageListener.class);
        long count = replayService.replay("orders", "0", "+", listener);

        // Capture and assert subscribe options
        ArgumentCaptor<PushSubscribeOptions> optsCaptor = ArgumentCaptor.forClass(PushSubscribeOptions.class);
        verify(jetStream).subscribe(eq("simplix.orders"), optsCaptor.capture());
        ConsumerConfiguration cc = optsCaptor.getValue().getConsumerConfiguration();
        assertThat(cc.getDeliverPolicy()).isEqualTo(DeliverPolicy.ByStartSequence);
        assertThat(cc.getStartSequence()).isEqualTo(1L);

        assertThat(count).isEqualTo(3L);
        verify(listener, times(3)).onMessage(any(), any());
    }

    @Test
    @DisplayName("replay by ID stops at toSeq and does not invoke listener beyond it")
    void replayById_stopsAtToSeq() throws Exception {
        JetStreamSubscription sub = mock(JetStreamSubscription.class);
        // Messages with seq 1-5; toId = "stream-3" means toSeq=3
        Message m1 = messageWithSeq(1L);
        Message m2 = messageWithSeq(2L);
        Message m3 = messageWithSeq(3L);
        Message m4 = messageWithSeq(4L);
        Message m5 = messageWithSeq(5L);
        // nextMessage called 4 times: m1, m2, m3, m4 (seq 4 > 3, breaks before listener call)
        when(sub.nextMessage(any(Duration.class))).thenReturn(m1, m2, m3, m4, m5, null);
        when(jetStream.subscribe(eq("simplix.orders"), any(PushSubscribeOptions.class))).thenReturn(sub);

        MessageListener<byte[]> listener = mock(MessageListener.class);
        long count = replayService.replay("orders", "0", "stream-3", listener);

        assertThat(count).isEqualTo(3L);
        verify(listener, times(3)).onMessage(any(), any());
    }

    @Test
    @DisplayName("replay by time builds ByStartTime consumer with correct startTime")
    void replayByTime_buildsByStartTimeConsumer() throws Exception {
        JetStreamSubscription sub = mock(JetStreamSubscription.class);
        when(sub.nextMessage(any(Duration.class))).thenReturn(null);
        when(jetStream.subscribe(eq("simplix.orders"), any(PushSubscribeOptions.class))).thenReturn(sub);

        Instant fromInstant = Instant.parse("2024-01-15T10:00:00Z");
        Instant toInstant = Instant.parse("2024-01-15T11:00:00Z");
        MessageListener<byte[]> listener = mock(MessageListener.class);

        replayService.replay("orders", fromInstant, toInstant, listener);

        ArgumentCaptor<PushSubscribeOptions> optsCaptor = ArgumentCaptor.forClass(PushSubscribeOptions.class);
        verify(jetStream).subscribe(eq("simplix.orders"), optsCaptor.capture());
        ConsumerConfiguration cc = optsCaptor.getValue().getConsumerConfiguration();
        assertThat(cc.getDeliverPolicy()).isEqualTo(DeliverPolicy.ByStartTime);
        assertThat(cc.getStartTime().toInstant()).isEqualTo(fromInstant);
    }

    @Test
    @DisplayName("replayPaginated delegates to replay; counts messages correctly")
    void replayPaginated_isAliasForReplayWithSamePageSizeBehavior() throws Exception {
        JetStreamSubscription sub = mock(JetStreamSubscription.class);
        Message m1 = messageWithSeq(1L);
        Message m2 = messageWithSeq(2L);
        when(sub.nextMessage(any(Duration.class))).thenReturn(m1, m2, null);
        when(jetStream.subscribe(eq("simplix.orders"), any(PushSubscribeOptions.class))).thenReturn(sub);

        MessageListener<byte[]> listener = mock(MessageListener.class);
        long count = replayService.replayPaginated("orders", "0", "+", listener, 50);

        assertThat(count).isEqualTo(2L);
        verify(listener, times(2)).onMessage(any(), any());
    }

    @Test
    @DisplayName("replay unsubscribes the ephemeral consumer after completion")
    void replay_unsubscribesEphemeralConsumer_atEnd() throws Exception {
        JetStreamSubscription sub = mock(JetStreamSubscription.class);
        Message m1 = messageWithSeq(1L);
        Message m2 = messageWithSeq(2L);
        Message m3 = messageWithSeq(3L);
        when(sub.nextMessage(any(Duration.class))).thenReturn(m1, m2, m3, null);
        when(jetStream.subscribe(eq("simplix.orders"), any(PushSubscribeOptions.class))).thenReturn(sub);

        MessageListener<byte[]> listener = mock(MessageListener.class);
        replayService.replay("orders", "0", "+", listener);

        verify(sub, times(1)).unsubscribe();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Creates a mocked NATS Message whose metaData().streamSequence() returns the given seq.
     * getData() and getHeaders() return null by default — the impl handles both.
     */
    private static Message messageWithSeq(long seq) {
        Message msg = mock(Message.class);
        NatsJetStreamMetaData meta = mock(NatsJetStreamMetaData.class);
        when(meta.streamSequence()).thenReturn(seq);
        when(msg.metaData()).thenReturn(meta);
        return msg;
    }
}
