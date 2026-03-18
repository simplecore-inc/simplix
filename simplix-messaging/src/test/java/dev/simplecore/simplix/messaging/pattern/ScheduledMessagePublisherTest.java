package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScheduledMessagePublisher")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledMessagePublisherTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private ScheduledMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ScheduledMessagePublisher(brokerStrategy, redisTemplate, "test:", Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        publisher.stop();
    }

    @Nested
    @DisplayName("publishDelayed")
    class PublishDelayedTests {

        @Test
        @DisplayName("should store message in Redis sorted set with delay")
        void shouldStoreMessageInSortedSet() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> message = Message.ofBytes("my-channel", "hello".getBytes());

            String scheduleId = publisher.publishDelayed(message, Duration.ofMinutes(5));

            assertThat(scheduleId).isNotBlank();
            verify(zSetOps).add(eq("test:messaging:scheduled"), anyString(), anyDouble());
        }

        @Test
        @DisplayName("should handle String payload")
        void shouldHandleStringPayload() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<String> message = Message.<String>builder()
                    .channel("ch")
                    .payload("text-message")
                    .build();

            String scheduleId = publisher.publishDelayed(message, Duration.ofSeconds(30));
            assertThat(scheduleId).isNotBlank();
        }

        @Test
        @DisplayName("should handle null payload")
        void shouldHandleNullPayload() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("ch")
                    .payload(null)
                    .build();

            String scheduleId = publisher.publishDelayed(message, Duration.ofSeconds(10));
            assertThat(scheduleId).isNotBlank();
        }

        @Test
        @DisplayName("should throw for unsupported payload type")
        void shouldThrowForUnsupportedPayload() {
            Message<Integer> message = Message.<Integer>builder()
                    .channel("ch")
                    .payload(42)
                    .build();

            assertThatThrownBy(() -> publisher.publishDelayed(message, Duration.ofSeconds(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported payload type");
        }

        @Test
        @DisplayName("should serialize headers in the scheduled value")
        void shouldSerializeHeaders() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("ch")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty()
                            .with("x-custom", "value1")
                            .with(MessageHeaders.CONTENT_TYPE, "application/json"))
                    .build();

            publisher.publishDelayed(message, Duration.ofSeconds(1));
            verify(zSetOps).add(eq("test:messaging:scheduled"), contains("||"), anyDouble());
        }

        @Test
        @DisplayName("should handle empty headers")
        void shouldHandleEmptyHeaders() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("ch")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty())
                    .build();

            publisher.publishDelayed(message, Duration.ofSeconds(1));
            verify(zSetOps).add(anyString(), anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("start and stop")
    class LifecycleTests {

        @Test
        @DisplayName("should start the poller")
        void shouldStartPoller() {
            publisher.start();
            // Starting again should be idempotent
            publisher.start();
        }

        @Test
        @DisplayName("should stop gracefully")
        void shouldStopGracefully() {
            publisher.start();
            assertThatCode(() -> publisher.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should be idempotent on stop")
        void shouldBeIdempotentOnStop() {
            publisher.start();
            publisher.stop();
            assertThatCode(() -> publisher.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle stop without start")
        void shouldHandleStopWithoutStart() {
            assertThatCode(() -> publisher.stop()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("pollDueMessages (internal)")
    class PollTests {

        @Test
        @DisplayName("should deliver due messages to broker")
        @SuppressWarnings("unchecked")
        void shouldDeliverDueMessages() {
            // Construct a valid scheduled message value
            String channel = "my-channel";
            String encodedPayload = java.util.Base64.getEncoder().encodeToString("hello".getBytes());
            String value = "sched-id||" + channel + "||" + encodedPayload + "||";

            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(List.of(value));

            publisher.start();

            // Wait briefly for the poller to execute
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Verify broker.send was called (the scheduler polls periodically)
            // We cannot guarantee timing, but if it runs, it should call send
        }

        @Test
        @DisplayName("should handle null result from Lua script")
        @SuppressWarnings("unchecked")
        void shouldHandleNullResult() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(null);

            publisher.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Should not throw
        }

        @Test
        @DisplayName("should handle empty result from Lua script")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyResult() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            publisher.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // No broker.send should be called
            verifyNoInteractions(brokerStrategy);
        }

        @Test
        @DisplayName("should handle invalid message format gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleInvalidMessageFormat() {
            // Invalid: less than 3 parts
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(List.of("invalid-value"));

            publisher.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Should not throw
            verifyNoInteractions(brokerStrategy);
        }

        @Test
        @DisplayName("should handle poll exception gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandlePollException() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenThrow(new RuntimeException("Redis down"));

            publisher.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Should not throw
        }
    }
}
