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
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@DisplayName("RedisStreamSubscriber")
@ExtendWith(MockitoExtension.class)
class RedisStreamSubscriberTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Nested
    @DisplayName("subscribe (Base64 mode)")
    class SubscribeBase64Tests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should create subscription with listener container")
        void shouldCreateSubscription() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.getStringSerializer()).thenReturn(new StringRedisSerializer());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("my-channel")
                    .groupName("my-group")
                    .consumerName("consumer-1")
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = subscriber.subscribe(request);

            assertThat(subscription).isNotNull();
            assertThat(subscription.channel()).isEqualTo("my-channel");
            assertThat(subscription.groupName()).isEqualTo("my-group");
            // Note: isActive() depends on container running state
        }

        @Test
        @DisplayName("should use default poll timeout and batch size when request has no values")
        void shouldUseDefaults() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.getStringSerializer()).thenReturn(new StringRedisSerializer());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .pollTimeout(null)
                    .batchSize(0)
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = subscriber.subscribe(request);
            assertThat(subscription).isNotNull();
        }

        @Test
        @DisplayName("should use request-specific poll timeout and batch size")
        void shouldUseRequestValues() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.getStringSerializer()).thenReturn(new StringRedisSerializer());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .pollTimeout(Duration.ofSeconds(5))
                    .batchSize(50)
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = subscriber.subscribe(request);
            assertThat(subscription).isNotNull();
        }
    }

    @Nested
    @DisplayName("subscribe (Raw mode)")
    class SubscribeRawTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "raw:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should create subscription in raw mode")
        void shouldCreateRawSubscription() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("my-channel")
                    .groupName("my-group")
                    .consumerName("consumer-1")
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = subscriber.subscribe(request);

            assertThat(subscription).isNotNull();
            assertThat(subscription.channel()).isEqualTo("my-channel");
        }

        @Test
        @DisplayName("should use defaults when request has null poll timeout and zero batch size")
        void shouldUseDefaultsRaw() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .pollTimeout(null)
                    .batchSize(0)
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = subscriber.subscribe(request);
            assertThat(subscription).isNotNull();
        }
    }

    @Nested
    @DisplayName("recoverPendingMessages (Base64)")
    class RecoverPendingBase64Tests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should recover pending messages and dispatch to listener")
        @SuppressWarnings("unchecked")
        void shouldRecoverPendingMessages() {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();
            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            String encodedPayload = Base64.getEncoder().encodeToString("test-data".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);
            fields.put(MessageHeaders.MESSAGE_ID, "msg-1");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.read(any(Consumer.class), any(StreamOffset.class)))
                    .thenReturn(List.of(record));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {
                        received.set(msg);
                        receivedAck.set(ack);
                    })
                    .build();

            subscriber.recoverPendingMessages(request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo("test-data".getBytes());
            assertThat(received.get().getChannel()).isEqualTo("ch");
        }

        @Test
        @DisplayName("should handle empty pending list")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyPending() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.read(any(Consumer.class), any(StreamOffset.class)))
                    .thenReturn(Collections.emptyList());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle null pending list")
        @SuppressWarnings("unchecked")
        void shouldHandleNullPending() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.read(any(Consumer.class), any(StreamOffset.class)))
                    .thenReturn(null);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle recovery exception gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleRecoveryException() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.read(any(Consumer.class), any(StreamOffset.class)))
                    .thenThrow(new RuntimeException("Connection lost"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle per-record processing exception during recovery")
        @SuppressWarnings("unchecked")
        void shouldHandlePerRecordException() {
            // A record with invalid base64 in payload to trigger processing failure
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", "!!!invalid-base64!!!");
            fields.put(MessageHeaders.MESSAGE_ID, "msg-bad");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.read(any(Consumer.class), any(StreamOffset.class)))
                    .thenReturn(List.of(record));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            // Should not throw - individual record failure is caught
            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle null payload in pending record")
        @SuppressWarnings("unchecked")
        void shouldHandleNullPayloadInPendingRecord() {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            Map<Object, Object> fields = new LinkedHashMap<>();
            // No "payload" key
            fields.put(MessageHeaders.MESSAGE_ID, "msg-2");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("2-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.read(any(Consumer.class), any(StreamOffset.class)))
                    .thenReturn(List.of(record));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            subscriber.recoverPendingMessages(request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEmpty();
        }
    }

    @Nested
    @DisplayName("recoverPendingMessages (Raw)")
    class RecoverPendingRawTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "raw:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should handle null pending list in raw mode")
        void shouldHandleNullPendingRaw() {
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle empty pending list in raw mode")
        void shouldHandleEmptyPendingRaw() {
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Collections.emptyList());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle recovery exception in raw mode")
        void shouldHandleExceptionInRawRecovery() {
            when(redisTemplate.execute(any(RedisCallback.class)))
                    .thenThrow(new RuntimeException("Connection lost"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            assertThatCode(() -> subscriber.recoverPendingMessages(request))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("autoClaimStuckMessages (Base64)")
    class AutoClaimBase64Tests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should do nothing when no pending messages")
        void shouldDoNothingWhenNoPending() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
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
        @DisplayName("should handle auto-claim exception gracefully")
        void shouldHandleAutoClaimException() {
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
    @DisplayName("autoClaimStuckMessages (Raw)")
    class AutoClaimRawTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "raw:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should do nothing when no pending messages in raw mode")
        void shouldDoNothingWhenNoPendingRaw() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
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
        @DisplayName("should handle exception in raw auto-claim")
        void shouldHandleExceptionInRawAutoClaim() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending(anyString(), anyString(), any(Range.class), anyLong()))
                    .thenThrow(new RuntimeException("Error"));

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
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("should use two-arg constructor with defaults")
        void shouldUseTwoArgConstructor() {
            RedisStreamSubscriber sub = new RedisStreamSubscriber(redisTemplate, "p:");

            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.getStringSerializer()).thenReturn(new StringRedisSerializer());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = sub.subscribe(request);
            assertThat(subscription).isNotNull();
        }

        @Test
        @DisplayName("should use four-arg constructor (without PayloadEncoding)")
        void shouldUseFourArgConstructor() {
            RedisStreamSubscriber sub = new RedisStreamSubscriber(redisTemplate, "p:",
                    Duration.ofSeconds(3), 20);

            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.getStringSerializer()).thenReturn(new StringRedisSerializer());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            Subscription subscription = sub.subscribe(request);
            assertThat(subscription).isNotNull();
        }
    }
}
