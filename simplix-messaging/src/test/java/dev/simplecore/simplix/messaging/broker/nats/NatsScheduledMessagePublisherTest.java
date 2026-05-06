package dev.simplecore.simplix.messaging.broker.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NatsScheduledMessagePublisherTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private BrokerStrategy broker;
    private KeyValue kv;
    private NatsLeaderElection leader;
    private NatsScheduledMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        broker = mock(BrokerStrategy.class);
        kv = mock(KeyValue.class);
        leader = mock(NatsLeaderElection.class);
        publisher = new NatsScheduledMessagePublisher(broker, kv, leader, Duration.ofMinutes(10));
    }

    // -----------------------------------------------------------------------
    // Test 1
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("publishDelayed writes KV entry with 20-digit zero-padded epochMillis prefix key")
    void publishDelayed_writesKvWithTimePrefixedKey() throws Exception {
        when(kv.put(any(String.class), any(byte[].class))).thenReturn(1L);

        byte[] payload = "hello".getBytes();
        Message<byte[]> message = Message.<byte[]>builder()
                .channel("test-channel")
                .payload(payload)
                .headers(MessageHeaders.of(Map.of("x-source", "unit-test")))
                .build();

        long beforeEpoch = Instant.now().toEpochMilli();
        String scheduleId = publisher.publishDelayed(message, Duration.ofMinutes(5));
        long afterEpoch = Instant.now().toEpochMilli();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(kv).put(keyCaptor.capture(), valueCaptor.capture());

        String key = keyCaptor.getValue();
        // Key must match <20-digit-zero-padded-epochMillis>.<uuid>
        assertThat(key).matches("\\d{20}\\." + scheduleId);

        long epochInKey = Long.parseLong(key.substring(0, 20));
        long deliveryMinMillis = beforeEpoch + Duration.ofMinutes(5).toMillis();
        long deliveryMaxMillis = afterEpoch + Duration.ofMinutes(5).toMillis();
        assertThat(epochInKey).isBetween(deliveryMinMillis, deliveryMaxMillis);

        // Value must be a JSON-serialized ScheduledRecord containing channel, payloadB64, headers
        NatsScheduledMessagePublisher.ScheduledRecord record =
                JSON.readValue(valueCaptor.getValue(), NatsScheduledMessagePublisher.ScheduledRecord.class);
        assertThat(record.channel()).isEqualTo("test-channel");
        assertThat(record.scheduleId()).isEqualTo(scheduleId);
        byte[] decodedPayload = Base64.getDecoder().decode(record.payloadB64());
        assertThat(decodedPayload).isEqualTo(payload);
        assertThat(record.headers()).contains("x-source");
    }

    // -----------------------------------------------------------------------
    // Test 2
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("cancel deletes the KV entry and returns true when key suffix matches scheduleId")
    void cancel_deletesEntry_andReturnsTrue_whenKeyMatches() throws Exception {
        String scheduleId = "my-schedule-id";
        String matchingKey = String.format("%020d.%s", Instant.now().toEpochMilli(), scheduleId);
        when(kv.keys()).thenReturn(List.of(matchingKey));

        boolean result = publisher.cancel(scheduleId);

        assertThat(result).isTrue();
        verify(kv).delete(matchingKey);
    }

    // -----------------------------------------------------------------------
    // Test 3
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("cancel returns false when scheduleId not found in KV keys")
    void cancel_returnsFalse_whenIdNotFound() throws Exception {
        when(kv.keys()).thenReturn(List.of());

        boolean result = publisher.cancel("non-existent-id");

        assertThat(result).isFalse();
        verify(kv, never()).delete(any());
    }

    // -----------------------------------------------------------------------
    // Test 4
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("poll is no-op when not leader — no KV operations occur")
    void poll_isNoOp_whenNotLeader() throws Exception {
        when(leader.isLeader()).thenReturn(false);

        publisher.poll();

        verify(kv, never()).keys();
        verify(broker, never()).send(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Test 5
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("poll delivers due messages and deletes KV entries when leader")
    void poll_deliversDueMessages_andDeletesKv_whenLeader() throws Exception {
        when(leader.isLeader()).thenReturn(true);

        long pastEpoch1 = Instant.now().minusSeconds(60).toEpochMilli();
        long pastEpoch2 = Instant.now().minusSeconds(30).toEpochMilli();
        String id1 = "sched-id-1";
        String id2 = "sched-id-2";
        String key1 = String.format("%020d.%s", pastEpoch1, id1);
        String key2 = String.format("%020d.%s", pastEpoch2, id2);
        when(kv.keys()).thenReturn(List.of(key1, key2));

        NatsScheduledMessagePublisher.ScheduledRecord record1 = new NatsScheduledMessagePublisher.ScheduledRecord(
                id1, "channel-a", pastEpoch1, Base64.getEncoder().encodeToString("payload1".getBytes()), "");
        NatsScheduledMessagePublisher.ScheduledRecord record2 = new NatsScheduledMessagePublisher.ScheduledRecord(
                id2, "channel-b", pastEpoch2, Base64.getEncoder().encodeToString("payload2".getBytes()), "");

        KeyValueEntry entry1 = mock(KeyValueEntry.class);
        when(entry1.getValue()).thenReturn(JSON.writeValueAsBytes(record1));
        KeyValueEntry entry2 = mock(KeyValueEntry.class);
        when(entry2.getValue()).thenReturn(JSON.writeValueAsBytes(record2));
        when(kv.get(key1)).thenReturn(entry1);
        when(kv.get(key2)).thenReturn(entry2);

        when(broker.send(any(), any(), any())).thenReturn(new PublishResult("r1", "channel-a", Instant.now()));

        publisher.poll();

        verify(broker, times(2)).send(any(), any(), any());
        verify(kv).delete(key1);
        verify(kv).delete(key2);
    }

    // -----------------------------------------------------------------------
    // Test 6
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("poll skips keys with deliveryAt in the future")
    void poll_skipsKeysWithDeliveryAtInFuture() throws Exception {
        when(leader.isLeader()).thenReturn(true);

        long futureEpoch = Instant.now().plusSeconds(3600).toEpochMilli();
        String futureKey = String.format("%020d.future-id", futureEpoch);
        when(kv.keys()).thenReturn(List.of(futureKey));

        publisher.poll();

        // No KV.get called because the entry is not yet due
        verify(kv, never()).get(any());
        verify(broker, never()).send(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Test 7
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("poll leaves KV entry when broker.send fails, allowing retry on next cycle")
    void poll_leavesKvEntry_whenBrokerSendFails() throws Exception {
        when(leader.isLeader()).thenReturn(true);

        long pastEpoch = Instant.now().minusSeconds(10).toEpochMilli();
        String schedId = "fail-sched-id";
        String key = String.format("%020d.%s", pastEpoch, schedId);
        when(kv.keys()).thenReturn(List.of(key));

        NatsScheduledMessagePublisher.ScheduledRecord record = new NatsScheduledMessagePublisher.ScheduledRecord(
                schedId, "channel-fail", pastEpoch, Base64.getEncoder().encodeToString("data".getBytes()), "");
        KeyValueEntry entry = mock(KeyValueEntry.class);
        when(entry.getValue()).thenReturn(JSON.writeValueAsBytes(record));
        when(kv.get(key)).thenReturn(entry);

        when(broker.send(any(), any(), any())).thenThrow(new RuntimeException("broker unavailable"));

        publisher.poll();

        // KV.delete must NOT be called when delivery fails
        verify(kv, never()).delete(any());
    }

    // -----------------------------------------------------------------------
    // Test 8
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("poll sets MESSAGE_ID header to scheduleId for native dedup on retry")
    void poll_setsNatsMsgIdHeaderToScheduleId() throws Exception {
        when(leader.isLeader()).thenReturn(true);

        long pastEpoch = Instant.now().minusSeconds(5).toEpochMilli();
        String schedId = "dedup-sched-id";
        String key = String.format("%020d.%s", pastEpoch, schedId);
        when(kv.keys()).thenReturn(List.of(key));

        NatsScheduledMessagePublisher.ScheduledRecord record = new NatsScheduledMessagePublisher.ScheduledRecord(
                schedId, "channel-dedup", pastEpoch, Base64.getEncoder().encodeToString("payload".getBytes()), "");
        KeyValueEntry entry = mock(KeyValueEntry.class);
        when(entry.getValue()).thenReturn(JSON.writeValueAsBytes(record));
        when(kv.get(key)).thenReturn(entry);
        when(broker.send(any(), any(), any())).thenReturn(new PublishResult("r1", "channel-dedup", Instant.now()));

        publisher.poll();

        ArgumentCaptor<MessageHeaders> headersCaptor = ArgumentCaptor.forClass(MessageHeaders.class);
        verify(broker).send(eq("channel-dedup"), any(byte[].class), headersCaptor.capture());

        MessageHeaders capturedHeaders = headersCaptor.getValue();
        assertThat(capturedHeaders.get(MessageHeaders.MESSAGE_ID)).hasValue(schedId);
    }

    // -----------------------------------------------------------------------
    // Test 9
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("start acquires leadership and schedules polling; stop releases leadership and stops poller")
    void start_acquiresLeadership_andSchedulesPolling_stop_releasesLeadership() {
        when(leader.tryAcquireLeadership()).thenReturn(true);

        publisher.start();

        verify(leader).tryAcquireLeadership();

        publisher.stop();

        verify(leader).releaseLeadership();
    }
}
