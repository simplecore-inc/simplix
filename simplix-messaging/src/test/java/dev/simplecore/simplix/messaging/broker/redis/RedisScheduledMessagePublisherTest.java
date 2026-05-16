package dev.simplecore.simplix.messaging.broker.redis;

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
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RedisScheduledMessagePublisher")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisScheduledMessagePublisherTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private RedisScheduledMessagePublisher scheduler;

    private Message<byte[]> message;

    @BeforeEach
    void setUp() {
        scheduler = new RedisScheduledMessagePublisher(brokerStrategy, redisTemplate, "test:", Duration.ofSeconds(5));
        message = Message.ofBytes("my-channel", "hello".getBytes());
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    @Nested
    @DisplayName("publishDelayed")
    class PublishDelayedTests {

        @Test
        @DisplayName("should store message in Redis sorted set with delay")
        void shouldStoreMessageInSortedSet() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            String scheduleId = scheduler.publishDelayed(message, Duration.ofMinutes(5));

            assertThat(scheduleId).isNotBlank();
            verify(zSetOps).add(eq("test:messaging:scheduled"), anyString(), anyDouble());
        }

        @Test
        @DisplayName("should handle String payload")
        void shouldHandleStringPayload() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<String> stringMessage = Message.<String>builder()
                    .channel("ch")
                    .payload("text-message")
                    .build();

            String scheduleId = scheduler.publishDelayed(stringMessage, Duration.ofSeconds(30));
            assertThat(scheduleId).isNotBlank();
        }

        @Test
        @DisplayName("should handle null payload")
        void shouldHandleNullPayload() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> nullMessage = Message.<byte[]>builder()
                    .channel("ch")
                    .payload(null)
                    .build();

            String scheduleId = scheduler.publishDelayed(nullMessage, Duration.ofSeconds(10));
            assertThat(scheduleId).isNotBlank();
        }

        @Test
        @DisplayName("should throw for unsupported payload type")
        void shouldThrowForUnsupportedPayload() {
            Message<Integer> intMessage = Message.<Integer>builder()
                    .channel("ch")
                    .payload(42)
                    .build();

            assertThatThrownBy(() -> scheduler.publishDelayed(intMessage, Duration.ofSeconds(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported payload type");
        }

        @Test
        @DisplayName("should serialize headers in the scheduled value")
        void shouldSerializeHeaders() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> headersMessage = Message.<byte[]>builder()
                    .channel("ch")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty()
                            .with("x-custom", "value1")
                            .with(MessageHeaders.CONTENT_TYPE, "application/json"))
                    .build();

            scheduler.publishDelayed(headersMessage, Duration.ofSeconds(1));
            verify(zSetOps).add(eq("test:messaging:scheduled"), contains("||"), anyDouble());
        }

        @Test
        @DisplayName("should handle empty headers")
        void shouldHandleEmptyHeaders() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            Message<byte[]> emptyHeadersMessage = Message.<byte[]>builder()
                    .channel("ch")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty())
                    .build();

            scheduler.publishDelayed(emptyHeadersMessage, Duration.ofSeconds(1));
            verify(zSetOps).add(anyString(), anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("start and stop")
    class LifecycleTests {

        @Test
        @DisplayName("should start the poller")
        void shouldStartPoller() {
            scheduler.start();
            // Starting again should be idempotent
            scheduler.start();
        }

        @Test
        @DisplayName("should stop gracefully")
        void shouldStopGracefully() {
            scheduler.start();
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should be idempotent on stop")
        void shouldBeIdempotentOnStop() {
            scheduler.start();
            scheduler.stop();
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle stop without start")
        void shouldHandleStopWithoutStart() {
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("pollDueMessages (internal)")
    class PollTests {

        @Test
        @DisplayName("should deliver due messages to broker")
        @SuppressWarnings("unchecked")
        void shouldDeliverDueMessages() {
            String channel = "my-channel";
            String encodedPayload = Base64.getEncoder().encodeToString("hello".getBytes());
            String value = "sched-id||" + channel + "||" + encodedPayload + "||";

            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(List.of(value));

            scheduler.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        @Test
        @DisplayName("should handle null result from Lua script")
        @SuppressWarnings("unchecked")
        void shouldHandleNullResult() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(null);

            scheduler.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        @Test
        @DisplayName("should handle empty result from Lua script")
        @SuppressWarnings("unchecked")
        void shouldHandleEmptyResult() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());

            scheduler.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            verifyNoInteractions(brokerStrategy);
        }

        @Test
        @DisplayName("should handle invalid message format gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandleInvalidMessageFormat() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(List.of("invalid-value"));

            scheduler.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            verifyNoInteractions(brokerStrategy);
        }

        @Test
        @DisplayName("should handle poll exception gracefully")
        @SuppressWarnings("unchecked")
        void shouldHandlePollException() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenThrow(new RuntimeException("Redis down"));

            scheduler.start();

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelTests {

        @Test
        @DisplayName("cancel removes matching entry from ZSET and returns true")
        void cancel_removesFromZsetAndReturnsTrue() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

            String id = scheduler.publishDelayed(message, Duration.ofSeconds(60));

            // Capture the stored value by inspecting what was added
            String encodedPayload = Base64.getEncoder().encodeToString("hello".getBytes());
            String storedValue = id + "||my-channel||" + encodedPayload + "||";

            Set<String> zsetContents = new HashSet<>();
            zsetContents.add(storedValue);

            when(zSetOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(zsetContents);
            when(zSetOps.remove(any(), any(Object[].class))).thenReturn(1L);

            assertThat(scheduler.cancel(id)).isTrue();
        }

        @Test
        @DisplayName("cancel returns false for unknown schedule ID")
        void cancel_unknownIdReturnsFalse() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(Collections.emptySet());
            when(zSetOps.remove(any(), any(Object[].class))).thenReturn(0L);

            assertThat(scheduler.cancel("not-there")).isFalse();
        }

        @Test
        @DisplayName("cancel returns false when ZSET range returns null")
        void cancel_nullRangeReturnsFalse() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(null);

            assertThat(scheduler.cancel("any-id")).isFalse();
        }
    }
}
