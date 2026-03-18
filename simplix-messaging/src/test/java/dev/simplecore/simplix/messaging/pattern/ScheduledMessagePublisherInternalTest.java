package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScheduledMessagePublisher internal methods")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledMessagePublisherInternalTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ScheduledMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ScheduledMessagePublisher(brokerStrategy, redisTemplate, "test:", Duration.ofSeconds(60));
    }

    @Nested
    @DisplayName("deliverScheduledMessage via reflection")
    class DeliverTests {

        @Test
        @DisplayName("should deliver a scheduled message with channel and payload")
        void shouldDeliverMessage() throws Exception {
            String channel = "my-channel";
            String encodedPayload = Base64.getEncoder().encodeToString("hello".getBytes());
            String value = "sched-1||" + channel + "||" + encodedPayload + "||";

            when(brokerStrategy.send(anyString(), any(byte[].class), any(MessageHeaders.class)))
                    .thenReturn(new PublishResult("r1", channel, Instant.now()));

            Method deliverMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deliverScheduledMessage", String.class);
            deliverMethod.setAccessible(true);
            deliverMethod.invoke(publisher, value);

            verify(brokerStrategy).send(eq(channel), eq("hello".getBytes()), any(MessageHeaders.class));
        }

        @Test
        @DisplayName("should deliver message with headers")
        void shouldDeliverWithHeaders() throws Exception {
            String channel = "my-channel";
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            String headers = "x-content-type=application%2Fjson&x-custom=value";
            String value = "sched-2||" + channel + "||" + encodedPayload + "||" + headers;

            when(brokerStrategy.send(anyString(), any(byte[].class), any(MessageHeaders.class)))
                    .thenReturn(new PublishResult("r2", channel, Instant.now()));

            Method deliverMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deliverScheduledMessage", String.class);
            deliverMethod.setAccessible(true);
            deliverMethod.invoke(publisher, value);

            verify(brokerStrategy).send(eq(channel), any(byte[].class), any(MessageHeaders.class));
        }

        @Test
        @DisplayName("should handle invalid format (less than 3 parts)")
        void shouldHandleInvalidFormat() throws Exception {
            Method deliverMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deliverScheduledMessage", String.class);
            deliverMethod.setAccessible(true);

            // Only 2 parts
            assertThatCode(() -> deliverMethod.invoke(publisher, "id||channel"))
                    .doesNotThrowAnyException();

            // No delimiter
            assertThatCode(() -> deliverMethod.invoke(publisher, "no-delimiter"))
                    .doesNotThrowAnyException();

            verifyNoInteractions(brokerStrategy);
        }

        @Test
        @DisplayName("should deliver message without headers (3 parts only)")
        void shouldDeliverWithoutHeaders() throws Exception {
            String channel = "ch";
            String encodedPayload = Base64.getEncoder().encodeToString("payload".getBytes());
            String value = "sched-3||" + channel + "||" + encodedPayload;

            when(brokerStrategy.send(anyString(), any(byte[].class), any(MessageHeaders.class)))
                    .thenReturn(new PublishResult("r3", channel, Instant.now()));

            Method deliverMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deliverScheduledMessage", String.class);
            deliverMethod.setAccessible(true);
            deliverMethod.invoke(publisher, value);

            verify(brokerStrategy).send(eq(channel), eq("payload".getBytes()), any(MessageHeaders.class));
        }
    }

    @Nested
    @DisplayName("pollDueMessages via reflection")
    class PollTests {

        @Test
        @DisplayName("should poll and deliver due messages")
        @SuppressWarnings("unchecked")
        void shouldPollAndDeliver() throws Exception {
            String channel = "poll-channel";
            String encodedPayload = Base64.getEncoder().encodeToString("polled".getBytes());
            String value = "sched-poll||" + channel + "||" + encodedPayload + "||";

            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(List.of(value));
            when(brokerStrategy.send(anyString(), any(byte[].class), any(MessageHeaders.class)))
                    .thenReturn(new PublishResult("r1", channel, Instant.now()));

            // Start and trigger poll
            publisher.start();
            Method pollMethod = ScheduledMessagePublisher.class.getDeclaredMethod("pollDueMessages");
            pollMethod.setAccessible(true);
            pollMethod.invoke(publisher);

            verify(brokerStrategy).send(eq(channel), any(byte[].class), any(MessageHeaders.class));
            publisher.stop();
        }

        @Test
        @DisplayName("should handle null poll result")
        @SuppressWarnings("unchecked")
        void shouldHandleNullPollResult() throws Exception {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(null);

            publisher.start();
            Method pollMethod = ScheduledMessagePublisher.class.getDeclaredMethod("pollDueMessages");
            pollMethod.setAccessible(true);
            pollMethod.invoke(publisher);

            verifyNoInteractions(brokerStrategy);
            publisher.stop();
        }

        @Test
        @DisplayName("should handle empty poll result")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyPollResult() throws Exception {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            publisher.start();
            Method pollMethod = ScheduledMessagePublisher.class.getDeclaredMethod("pollDueMessages");
            pollMethod.setAccessible(true);
            pollMethod.invoke(publisher);

            verifyNoInteractions(brokerStrategy);
            publisher.stop();
        }

        @Test
        @DisplayName("should handle poll exception")
        @SuppressWarnings("unchecked")
        void shouldHandlePollException() throws Exception {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenThrow(new RuntimeException("Redis down"));

            publisher.start();
            Method pollMethod = ScheduledMessagePublisher.class.getDeclaredMethod("pollDueMessages");
            pollMethod.setAccessible(true);

            assertThatCode(() -> pollMethod.invoke(publisher)).doesNotThrowAnyException();
            publisher.stop();
        }

        @Test
        @DisplayName("should handle delivery exception for individual messages")
        @SuppressWarnings("unchecked")
        void shouldHandleDeliveryException() throws Exception {
            String value = "id||ch||" + Base64.getEncoder().encodeToString("x".getBytes()) + "||";

            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(List.of(value));
            when(brokerStrategy.send(anyString(), any(byte[].class), any(MessageHeaders.class)))
                    .thenThrow(new RuntimeException("Send failed"));

            publisher.start();
            Method pollMethod = ScheduledMessagePublisher.class.getDeclaredMethod("pollDueMessages");
            pollMethod.setAccessible(true);

            assertThatCode(() -> pollMethod.invoke(publisher)).doesNotThrowAnyException();
            publisher.stop();
        }

        @Test
        @DisplayName("should skip poll when not running")
        @SuppressWarnings("unchecked")
        void shouldSkipWhenNotRunning() throws Exception {
            // Don't start the publisher
            Method pollMethod = ScheduledMessagePublisher.class.getDeclaredMethod("pollDueMessages");
            pollMethod.setAccessible(true);
            pollMethod.invoke(publisher);

            verifyNoInteractions(brokerStrategy);
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    @DisplayName("serializeHeaders / deserializeHeaders")
    class HeaderSerializationTests {

        @Test
        @DisplayName("should round-trip headers through serialization")
        void shouldRoundTripHeaders() throws Exception {
            Method serializeMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "serializeHeaders", MessageHeaders.class);
            serializeMethod.setAccessible(true);

            Method deserializeMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deserializeHeaders", String.class);
            deserializeMethod.setAccessible(true);

            MessageHeaders original = MessageHeaders.empty()
                    .with("key1", "value1")
                    .with("key2", "value with spaces");

            String serialized = (String) serializeMethod.invoke(publisher, original);
            assertThat(serialized).isNotBlank();

            MessageHeaders deserialized = (MessageHeaders) deserializeMethod.invoke(publisher, serialized);
            assertThat(deserialized.get("key1")).hasValue("value1");
            assertThat(deserialized.get("key2")).hasValue("value with spaces");
        }

        @Test
        @DisplayName("should return empty headers for null input")
        void shouldReturnEmptyForNull() throws Exception {
            Method deserializeMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deserializeHeaders", String.class);
            deserializeMethod.setAccessible(true);

            MessageHeaders result = (MessageHeaders) deserializeMethod.invoke(publisher, (String) null);
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should return empty headers for blank input")
        void shouldReturnEmptyForBlank() throws Exception {
            Method deserializeMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "deserializeHeaders", String.class);
            deserializeMethod.setAccessible(true);

            MessageHeaders result = (MessageHeaders) deserializeMethod.invoke(publisher, "  ");
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should serialize empty headers to empty string")
        void shouldSerializeEmptyHeaders() throws Exception {
            Method serializeMethod = ScheduledMessagePublisher.class.getDeclaredMethod(
                    "serializeHeaders", MessageHeaders.class);
            serializeMethod.setAccessible(true);

            String result = (String) serializeMethod.invoke(publisher, MessageHeaders.empty());
            assertThat(result).isEmpty();
        }
    }
}
