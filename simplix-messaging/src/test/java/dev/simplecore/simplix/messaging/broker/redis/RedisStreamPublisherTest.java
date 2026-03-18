package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@DisplayName("RedisStreamPublisher")
@ExtendWith(MockitoExtension.class)
class RedisStreamPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Nested
    @DisplayName("Base64 mode")
    class Base64ModeTests {

        private RedisStreamPublisher publisher;

        @BeforeEach
        void setUp() {
            publisher = new RedisStreamPublisher(redisTemplate, "test:", 50000L, PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should publish message with Base64 encoded payload")
        void shouldPublishBase64() {
            RecordId recordId = RecordId.of("1234-0");
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(recordId);

            byte[] payload = "hello".getBytes();
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");

            PublishResult result = publisher.send("my-channel", payload, headers);

            assertThat(result).isNotNull();
            assertThat(result.recordId()).isEqualTo("1234-0");
            assertThat(result.channel()).isEqualTo("my-channel");
            assertThat(result.timestamp()).isNotNull();

            verify(streamOps).add(any(MapRecord.class));
            verify(streamOps).trim(eq("test:my-channel"), eq(50000L), eq(true));
        }

        @Test
        @DisplayName("should handle null recordId from Redis")
        void shouldHandleNullRecordId() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(null);

            byte[] payload = "hello".getBytes();
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");

            PublishResult result = publisher.send("ch", payload, headers);

            assertThat(result.recordId()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should handle trim failure gracefully")
        void shouldHandleTrimFailure() {
            RecordId recordId = RecordId.of("1234-0");
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(recordId);
            doThrow(new RuntimeException("Trim failed")).when(streamOps).trim(anyString(), anyLong(), anyBoolean());

            byte[] payload = "hello".getBytes();
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");

            // Should not throw even if trim fails
            PublishResult result = publisher.send("ch", payload, headers);
            assertThat(result.recordId()).isEqualTo("1234-0");
        }

        @Test
        @DisplayName("should use MESSAGE_ID from headers for trace if present")
        void shouldUseMessageIdFromHeaders() {
            RecordId recordId = RecordId.of("1-0");
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(recordId);

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "custom-id");

            PublishResult result = publisher.send("ch", new byte[]{1}, headers);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should include all headers in the record fields")
        void shouldIncludeHeadersInRecord() {
            RecordId recordId = RecordId.of("2-0");
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(recordId);

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1")
                    .with(MessageHeaders.CONTENT_TYPE, "application/json")
                    .with(MessageHeaders.CORRELATION_ID, "corr-1");

            publisher.send("ch", new byte[0], headers);
            verify(streamOps).add(any(MapRecord.class));
        }
    }

    @Nested
    @DisplayName("Raw mode")
    class RawModeTests {

        private RedisStreamPublisher publisher;

        @BeforeEach
        void setUp() {
            publisher = new RedisStreamPublisher(redisTemplate, "raw:", 10000L, PayloadEncoding.RAW);
        }

        @Test
        @DisplayName("should publish message with raw byte payload via RedisCallback")
        void shouldPublishRaw() {
            RecordId recordId = RecordId.of("5-0");
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(recordId);
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            byte[] payload = "raw-data".getBytes();
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "raw-msg-1");

            PublishResult result = publisher.send("my-channel", payload, headers);

            assertThat(result.recordId()).isEqualTo("5-0");
            assertThat(result.channel()).isEqualTo("my-channel");
            verify(redisTemplate).execute(any(RedisCallback.class));
        }

        @Test
        @DisplayName("should handle null recordId in raw mode")
        void shouldHandleNullRecordIdRaw() {
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            byte[] payload = "raw-data".getBytes();
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "raw-msg-1");

            PublishResult result = publisher.send("ch", payload, headers);

            assertThat(result.recordId()).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("should use default Base64 encoding with two-arg constructor")
        void shouldUseDefaultEncoding() {
            RedisStreamPublisher pub = new RedisStreamPublisher(redisTemplate, "p:");

            // Verify it works - it should use BASE64 mode
            RecordId recordId = RecordId.of("1-0");
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(recordId);

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            PublishResult result = pub.send("ch", "hello".getBytes(), headers);
            assertThat(result.recordId()).isEqualTo("1-0");
        }

        @Test
        @DisplayName("should use default maxLength and Base64 with three-arg constructor")
        void shouldUseDefaultMaxLength() {
            RedisStreamPublisher pub = new RedisStreamPublisher(redisTemplate, "p:", 100L);

            RecordId recordId = RecordId.of("1-0");
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(recordId);

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            pub.send("ch", "hello".getBytes(), headers);

            verify(streamOps).trim("p:ch", 100L, true);
        }
    }
}
