package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
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
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RedisStreamSubscriber processing paths")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisStreamSubscriberProcessingTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Nested
    @DisplayName("processBase64Record via reflection")
    class ProcessBase64Tests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should process base64 record and dispatch to listener")
        void shouldProcessBase64Record() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            String encodedPayload = Base64.getEncoder().encodeToString("test-data".getBytes());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);
            fields.put(MessageHeaders.MESSAGE_ID, "msg-1");

            MapRecord<String, String, String> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processBase64Record", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo("test-data".getBytes());
        }

        @Test
        @DisplayName("should handle null payload field in base64 record")
        void shouldHandleNullPayload() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            Map<String, String> fields = new LinkedHashMap<>();
            // No "payload" key
            fields.put(MessageHeaders.MESSAGE_ID, "msg-2");

            MapRecord<String, String, String> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("2-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processBase64Record", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            assertThat(received.get().getPayload()).isEmpty();
        }

        @Test
        @DisplayName("should ack on processing exception")
        void shouldAckOnProcessingException() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("payload", "!!!invalid-base64!!!");

            MapRecord<String, String, String> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("3-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processBase64Record", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            // Should acknowledge unrecoverable message
            verify(streamOps).acknowledge("test:ch", "grp", "3-0");
        }
    }

    @Nested
    @DisplayName("processRawRecord via reflection")
    class ProcessRawTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "raw:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should process raw record and dispatch to listener")
        void shouldProcessRawRecord() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            Map<String, byte[]> fields = new LinkedHashMap<>();
            fields.put("payload", "raw-data".getBytes(StandardCharsets.UTF_8));
            fields.put(MessageHeaders.MESSAGE_ID, "msg-raw".getBytes(StandardCharsets.UTF_8));

            MapRecord<String, String, byte[]> record = MapRecord.create("raw:ch", fields)
                    .withId(RecordId.of("1-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processRawRecord", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo("raw-data".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("should handle missing payload in raw record")
        void shouldHandleMissingPayload() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            Map<String, byte[]> fields = new LinkedHashMap<>();
            fields.put(MessageHeaders.MESSAGE_ID, "msg-raw".getBytes(StandardCharsets.UTF_8));

            MapRecord<String, String, byte[]> record = MapRecord.create("raw:ch", fields)
                    .withId(RecordId.of("2-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processRawRecord", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            assertThat(received.get().getPayload()).isEmpty();
        }

        @Test
        @DisplayName("should ack on processing exception in raw mode")
        void shouldAckOnExceptionRaw() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            Map<String, byte[]> fields = new LinkedHashMap<>();
            fields.put("payload", "data".getBytes(StandardCharsets.UTF_8));

            MapRecord<String, String, byte[]> record = MapRecord.create("raw:ch", fields)
                    .withId(RecordId.of("3-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> { throw new RuntimeException("Handler error"); })
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processRawRecord", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            verify(streamOps).acknowledge("raw:ch", "grp", "3-0");
        }
    }

    @Nested
    @DisplayName("RedisMessageAcknowledgment via dispatch")
    class RedisAckTests {

        private RedisStreamSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisStreamSubscriber(redisTemplate, "test:",
                    Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should ack message via XACK")
        void shouldAckViaXack() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);

            MapRecord<String, String, String> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processBase64Record", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            // Test ack
            receivedAck.get().ack();
            verify(streamOps).acknowledge("test:ch", "grp", "1-0");
        }

        @Test
        @DisplayName("should nack message (log warning, keep in PEL)")
        void shouldNack() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);

            MapRecord<String, String, String> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("2-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processBase64Record", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            // nack should not throw
            assertThatCode(() -> receivedAck.get().nack(true)).doesNotThrowAnyException();
            assertThatCode(() -> receivedAck.get().nack(false)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should reject message via XACK")
        void shouldReject() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);

            MapRecord<String, String, String> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("3-0"));

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method processMethod = RedisStreamSubscriber.class.getDeclaredMethod(
                    "processBase64Record", MapRecord.class, SubscribeRequest.class);
            processMethod.setAccessible(true);
            processMethod.invoke(subscriber, record, request);

            receivedAck.get().reject("bad message");
            verify(streamOps).acknowledge("test:ch", "grp", "3-0");
        }
    }
}
