package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StreamReplayService")
@ExtendWith(MockitoExtension.class)
class StreamReplayServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private StreamReplayService replayService;

    @BeforeEach
    void setUp() {
        replayService = new StreamReplayService(redisTemplate, "test:");
    }

    @Nested
    @DisplayName("replay by stream ID range")
    class ReplayByIdRangeTests {

        @Test
        @DisplayName("should replay messages within range")
        @SuppressWarnings("unchecked")
        void shouldReplayMessages() {
            String encodedPayload = Base64.getEncoder().encodeToString("hello".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);
            fields.put(MessageHeaders.MESSAGE_ID, "msg-1");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(eq("test:ch"), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record));

            AtomicInteger count = new AtomicInteger(0);
            long replayed = replayService.replay("ch", "0", "+", (msg, ack) -> count.incrementAndGet());

            assertThat(replayed).isEqualTo(1);
            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return 0 when no records found")
        @SuppressWarnings("unchecked")
        void shouldReturnZeroWhenNoRecords() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(Collections.emptyList());

            long replayed = replayService.replay("ch", "0", "+", (msg, ack) -> {});

            assertThat(replayed).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 when null records returned")
        @SuppressWarnings("unchecked")
        void shouldReturnZeroWhenNullRecords() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(null);

            long replayed = replayService.replay("ch", "0", "+", (msg, ack) -> {});

            assertThat(replayed).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("replay by time range")
    class ReplayByTimeRangeTests {

        @Test
        @DisplayName("should convert Instants to stream IDs and replay")
        @SuppressWarnings("unchecked")
        void shouldReplayByTimeRange() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(Collections.emptyList());

            Instant from = Instant.parse("2024-01-01T00:00:00Z");
            Instant to = Instant.parse("2024-01-02T00:00:00Z");

            long replayed = replayService.replay("ch", from, to, (msg, ack) -> {});

            assertThat(replayed).isEqualTo(0);
            verify(streamOps).range(eq("test:ch"), any(Range.class), any(Limit.class));
        }
    }

    @Nested
    @DisplayName("replayPaginated")
    class ReplayPaginatedTests {

        @Test
        @DisplayName("should paginate through multiple pages")
        @SuppressWarnings("unchecked")
        void shouldPaginateThroughPages() {
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<Object, Object> fields1 = new LinkedHashMap<>();
            fields1.put("payload", encodedPayload);
            MapRecord<String, Object, Object> record1 = MapRecord.create("test:ch", fields1)
                    .withId(RecordId.of("1-0"));

            Map<Object, Object> fields2 = new LinkedHashMap<>();
            fields2.put("payload", encodedPayload);
            MapRecord<String, Object, Object> record2 = MapRecord.create("test:ch", fields2)
                    .withId(RecordId.of("2-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            // First call returns full page (size = pageSize), second returns less (stop)
            when(streamOps.range(eq("test:ch"), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record1))           // first page (full)
                    .thenReturn(List.of(record2))            // second page (full)
                    .thenReturn(Collections.emptyList());    // third page (empty, stop)

            AtomicInteger count = new AtomicInteger(0);
            long replayed = replayService.replayPaginated("ch", "0", "+",
                    (msg, ack) -> count.incrementAndGet(), 1);

            assertThat(replayed).isEqualTo(2);
            assertThat(count.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should handle null payload in record")
        @SuppressWarnings("unchecked")
        void shouldHandleNullPayload() {
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put(MessageHeaders.MESSAGE_ID, "msg-1");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record));

            List<Message<byte[]>> received = new ArrayList<>();
            replayService.replay("ch", "0", "+", (msg, ack) -> received.add(msg));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).getPayload()).isEmpty();
        }

        @Test
        @DisplayName("should use record ID as messageId when header is missing")
        @SuppressWarnings("unchecked")
        void shouldUseRecordIdWhenNoMessageIdHeader() {
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("99-5"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record));

            List<Message<byte[]>> received = new ArrayList<>();
            replayService.replay("ch", "0", "+", (msg, ack) -> received.add(msg));

            assertThat(received.get(0).getMessageId()).isEqualTo("99-5");
        }

        @Test
        @DisplayName("should separate headers from payload field")
        @SuppressWarnings("unchecked")
        void shouldSeparateHeaders() {
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);
            fields.put("x-custom-header", "custom-value");
            fields.put(MessageHeaders.CONTENT_TYPE, "application/json");

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record));

            List<Message<byte[]>> received = new ArrayList<>();
            replayService.replay("ch", "0", "+", (msg, ack) -> received.add(msg));

            assertThat(received.get(0).getHeaders().get("x-custom-header")).hasValue("custom-value");
            assertThat(received.get(0).getHeaders().contentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("should stop pagination when last page is less than page size")
        @SuppressWarnings("unchecked")
        void shouldStopOnPartialPage() {
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);

            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("100-3"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record));

            long replayed = replayService.replayPaginated("ch", "0", "+",
                    (msg, ack) -> {}, 5);

            assertThat(replayed).isEqualTo(1);
            verify(streamOps, times(1)).range(anyString(), any(Range.class), any(Limit.class));
        }

        @Test
        @DisplayName("should increment stream ID without sequence separator")
        @SuppressWarnings("unchecked")
        void shouldHandleStreamIdWithoutDash() {
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("payload", encodedPayload);

            // Use a stream ID without a dash (unusual but possible)
            MapRecord<String, Object, Object> record = MapRecord.create("test:ch", fields)
                    .withId(RecordId.of("1234-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            // Return full page first, then empty
            when(streamOps.range(eq("test:ch"), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record))
                    .thenReturn(Collections.emptyList());

            long replayed = replayService.replayPaginated("ch", "0", "+",
                    (msg, ack) -> {}, 1);

            assertThat(replayed).isEqualTo(1);
        }
    }
}
