package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RedisStreamSubscriber inner classes and processing paths")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisStreamSubscriberInnerClassTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Nested
    @DisplayName("autoClaimStuckMessages (Base64) with stuck messages")
    class AutoClaimBase64WithMessagesTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should claim and process stuck messages")
        @SuppressWarnings("unchecked")
        void shouldClaimAndProcessStuck() {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            PendingMessage pm = mock(PendingMessage.class);
            when(pm.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofMinutes(10));
            when(pm.getId()).thenReturn(RecordId.of("1-0"));

            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(false);
            when(pendingMessages.stream()).thenReturn(java.util.stream.Stream.of(pm));

            String encodedPayload = Base64.getEncoder().encodeToString("claimed-data".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);
            fields.put(MessageHeaders.MESSAGE_ID, "claimed-msg");

            MapRecord<String, Object, Object> claimedRecord = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);
            when(streamOps.claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class)))
                    .thenReturn(List.of(claimedRecord));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5));

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo("claimed-data".getBytes());
        }

        @Test
        @DisplayName("should skip when no stuck messages meet idle threshold")
        @SuppressWarnings("unchecked")
        void shouldSkipWhenNoStuckMessages() {
            PendingMessage pm = mock(PendingMessage.class);
            when(pm.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(1)); // below threshold

            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(false);
            when(pendingMessages.stream()).thenReturn(java.util.stream.Stream.of(pm));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5));

            verify(streamOps, never()).claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class));
        }

        @Test
        @DisplayName("should handle null claimed result")
        @SuppressWarnings("unchecked")
        void shouldHandleNullClaimedResult() {
            PendingMessage pm = mock(PendingMessage.class);
            when(pm.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofMinutes(10));
            when(pm.getId()).thenReturn(RecordId.of("1-0"));

            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(false);
            when(pendingMessages.stream()).thenReturn(java.util.stream.Stream.of(pm));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);
            when(streamOps.claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class)))
                    .thenReturn(null);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle empty PendingMessages")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyPendingMessages() {
            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(true);

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle per-record processing error in auto-claim")
        @SuppressWarnings("unchecked")
        void shouldHandlePerRecordError() {
            PendingMessage pm = mock(PendingMessage.class);
            when(pm.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofMinutes(10));
            when(pm.getId()).thenReturn(RecordId.of("1-0"));

            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(false);
            when(pendingMessages.stream()).thenReturn(java.util.stream.Stream.of(pm));

            // Invalid base64 to trigger decode error
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", "!!invalid!!");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);
            when(streamOps.claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class)))
                    .thenReturn(List.of(record));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> { throw new RuntimeException("Processing failed"); })
                    .build();

            assertThatCode(() -> subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("autoClaimStuckMessages (Raw) with stuck messages")
    class AutoClaimRawWithMessagesTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "raw:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should handle empty PendingMessages in raw mode")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyPendingRaw() {
            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(true);

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should skip when no stuck messages meet idle threshold in raw mode")
        @SuppressWarnings("unchecked")
        void shouldSkipWhenNoStuckRaw() {
            PendingMessage pm = mock(PendingMessage.class);
            when(pm.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(1));

            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(false);
            when(pendingMessages.stream()).thenReturn(java.util.stream.Stream.of(pm));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5));

            verify(redisTemplate, never()).execute(any(RedisCallback.class));
        }

        @Test
        @DisplayName("should handle null claimed result in raw mode")
        @SuppressWarnings("unchecked")
        void shouldHandleNullClaimedRaw() {
            PendingMessage pm = mock(PendingMessage.class);
            when(pm.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofMinutes(10));
            when(pm.getId()).thenReturn(RecordId.of("1-0"));

            PendingMessages pendingMessages = mock(PendingMessages.class);
            when(pendingMessages.isEmpty()).thenReturn(false);
            when(pendingMessages.stream()).thenReturn(java.util.stream.Stream.of(pm));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenReturn(pendingMessages);
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle raw auto-claim outer exception")
        @SuppressWarnings("unchecked")
        void shouldHandleOuterException() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenThrow(new RuntimeException("Redis error"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.autoClaimStuckMessages(request, Duration.ofMinutes(5)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("recoverPendingMessages (Raw) with records")
    class RecoverPendingRawWithRecordsTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "raw:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should recover pending byte records")
        @SuppressWarnings("unchecked")
        void shouldRecoverPendingByteRecords() {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            Map<byte[], byte[]> rawFields = new LinkedHashMap<>();
            rawFields.put("payload".getBytes(StandardCharsets.UTF_8), "raw-payload".getBytes(StandardCharsets.UTF_8));
            rawFields.put(MessageHeaders.MESSAGE_ID.getBytes(StandardCharsets.UTF_8), "msg-raw-1".getBytes(StandardCharsets.UTF_8));

            ByteRecord record = StreamRecords.rawBytes(rawFields)
                    .withId(RecordId.of("1-0"))
                    .withStreamKey("raw:ch".getBytes(StandardCharsets.UTF_8));

            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of(record));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            subscriber.recoverPendingMessages(request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo("raw-payload".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("should handle per-record exception in raw recovery")
        @SuppressWarnings("unchecked")
        void shouldHandlePerRecordExceptionRaw() {
            Map<byte[], byte[]> rawFields = new LinkedHashMap<>();
            rawFields.put("payload".getBytes(StandardCharsets.UTF_8), "data".getBytes(StandardCharsets.UTF_8));

            ByteRecord record = StreamRecords.rawBytes(rawFields)
                    .withId(RecordId.of("1-0"))
                    .withStreamKey("raw:ch".getBytes(StandardCharsets.UTF_8));

            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of(record));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> { throw new RuntimeException("Handler error"); })
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("RedisSubscription cancel")
    class RedisSubscriptionCancelTests {

        @Test
        @DisplayName("should cancel subscription and stop container")
        void shouldCancelSubscription() {
            RedisStreamSubscriber subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);

            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.getStringSerializer()).thenReturn(new StringRedisSerializer());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = subscriber.subscribe(request);

            // Cancel should not throw
            assertThatCode(subscription::cancel).doesNotThrowAnyException();
            assertThat(subscription.isActive()).isFalse();

            // Double cancel should be safe
            assertThatCode(subscription::cancel).doesNotThrowAnyException();
        }
    }
}
