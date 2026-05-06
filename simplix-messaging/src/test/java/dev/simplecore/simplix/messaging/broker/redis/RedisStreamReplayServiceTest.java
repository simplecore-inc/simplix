package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@DisplayName("RedisStreamReplayService")
@ExtendWith(MockitoExtension.class)
class RedisStreamReplayServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private RedisStreamReplayService replayService;

    @BeforeEach
    void setUp() {
        replayService = new RedisStreamReplayService(redisTemplate, "test:");
    }

    @Nested
    @DisplayName("replay by stream ID range")
    class ReplayByIdRangeTests {

        @Test
        @DisplayName("replaysAllByIdRange_returns3: range returns 3 records, listener invoked 3 times, return value is 3")
        @SuppressWarnings("unchecked")
        void replaysAllByIdRange_returns3() {
            String encodedPayload = Base64.getEncoder().encodeToString("hello".getBytes());

            Map<Object, Object> fields1 = new LinkedHashMap<>();
            fields1.put("payload", encodedPayload);
            fields1.put(MessageHeaders.MESSAGE_ID, "msg-1");
            MapRecord<String, Object, Object> record1 = MapRecord.create("test:ch", fields1)
                    .withId(RecordId.of("1-0"));

            Map<Object, Object> fields2 = new LinkedHashMap<>();
            fields2.put("payload", Base64.getEncoder().encodeToString("world".getBytes()));
            fields2.put(MessageHeaders.MESSAGE_ID, "msg-2");
            MapRecord<String, Object, Object> record2 = MapRecord.create("test:ch", fields2)
                    .withId(RecordId.of("2-0"));

            Map<Object, Object> fields3 = new LinkedHashMap<>();
            fields3.put("payload", Base64.getEncoder().encodeToString("foo".getBytes()));
            fields3.put(MessageHeaders.MESSAGE_ID, "msg-3");
            MapRecord<String, Object, Object> record3 = MapRecord.create("test:ch", fields3)
                    .withId(RecordId.of("3-0"));

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(eq("test:ch"), any(Range.class), any(Limit.class)))
                    .thenReturn(List.of(record1, record2, record3));

            AtomicInteger listenerCallCount = new AtomicInteger(0);
            List<Message<byte[]>> received = new ArrayList<>();
            long replayed = replayService.replay("ch", "0", "+", (msg, ack) -> {
                listenerCallCount.incrementAndGet();
                received.add(msg);
            });

            assertThat(replayed).isEqualTo(3);
            assertThat(listenerCallCount.get()).isEqualTo(3);
            // Verify first record's payload was Base64-decoded correctly
            assertThat(new String(received.get(0).getPayload())).isEqualTo("hello");
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
        @DisplayName("replaysByTimeRange_callsRangeWithMillisIds: Instant range translates to millis-based stream IDs")
        @SuppressWarnings("unchecked")
        void replaysByTimeRange_callsRangeWithMillisIds() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(anyString(), any(Range.class), any(Limit.class)))
                    .thenReturn(Collections.emptyList());

            Instant from = Instant.ofEpochMilli(1000);
            Instant to = Instant.ofEpochMilli(2000);

            replayService.replay("ch", from, to, (msg, ack) -> {});

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Range<String>> rangeCaptor = ArgumentCaptor.forClass(Range.class);
            verify(streamOps).range(eq("test:ch"), rangeCaptor.capture(), any(Limit.class));

            Range<String> capturedRange = rangeCaptor.getValue();
            assertThat(capturedRange.getLowerBound().getValue()).hasValue("1000-0");
            assertThat(capturedRange.getUpperBound().getValue()).hasValue("2000-0");
        }
    }

    @Nested
    @DisplayName("replayPaginated")
    class ReplayPaginatedTests {

        @Test
        @DisplayName("replayPaginated_paginatesAcrossPages: 100 + 50 records across 2 pages = 150 total, at least 2 range calls")
        @SuppressWarnings("unchecked")
        void replayPaginated_paginatesAcrossPages() {
            String encodedPayload = Base64.getEncoder().encodeToString("data".getBytes());

            // Build 100 records for page 1
            List<MapRecord<String, Object, Object>> page1 = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Map<Object, Object> fields = new LinkedHashMap<>();
                fields.put("payload", encodedPayload);
                page1.add(MapRecord.create("test:ch", fields).withId(RecordId.of(i + "-0")));
            }

            // Build 50 records for page 2
            List<MapRecord<String, Object, Object>> page2 = new ArrayList<>();
            for (int i = 100; i < 150; i++) {
                Map<Object, Object> fields = new LinkedHashMap<>();
                fields.put("payload", encodedPayload);
                page2.add(MapRecord.create("test:ch", fields).withId(RecordId.of(i + "-0")));
            }

            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.range(eq("test:ch"), any(Range.class), any(Limit.class)))
                    .thenReturn(page1)                       // first page: full 100 records
                    .thenReturn(page2)                       // second page: 50 records (< pageSize, stop)
                    .thenReturn(Collections.emptyList());    // guard: should not be reached

            AtomicInteger listenerCallCount = new AtomicInteger(0);
            long replayed = replayService.replayPaginated("ch", "0", "+",
                    (msg, ack) -> listenerCallCount.incrementAndGet(), 100);

            assertThat(replayed).isEqualTo(150);
            assertThat(listenerCallCount.get()).isEqualTo(150);
            verify(streamOps, atLeast(2)).range(eq("test:ch"), any(Range.class), any(Limit.class));
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
    }
}
