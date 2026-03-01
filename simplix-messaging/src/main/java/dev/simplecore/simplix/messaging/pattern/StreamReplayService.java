package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays historical messages from a Redis Stream within a specified range.
 *
 * <p>Uses XRANGE/XREVRANGE for range-based replay without consumer groups,
 * allowing reprocessing of past messages for debugging, auditing, or recomputation.
 *
 * <p>Usage example:
 * <pre>{@code
 * StreamReplayService replay = new StreamReplayService(redisTemplate, "pacs:");
 * replay.replay("sync-results", "0", "+", message -> {
 *     System.out.println("Replayed: " + message.getMessageId());
 * });
 * }</pre>
 */
@Slf4j
public class StreamReplayService {

    private static final String PAYLOAD_FIELD = "payload";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public StreamReplayService(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    /**
     * Replay messages within a stream ID range.
     *
     * @param channel    the channel to replay from
     * @param fromId     the start stream ID (inclusive), use "0" for beginning
     * @param toId       the end stream ID (inclusive), use "+" for latest
     * @param listener   the listener to receive replayed messages
     * @return the number of messages replayed
     */
    public long replay(String channel, String fromId, String toId, MessageListener<byte[]> listener) {
        return replayPaginated(channel, fromId, toId, listener, DEFAULT_PAGE_SIZE);
    }

    /**
     * Replay messages within a time range.
     *
     * @param channel  the channel to replay from
     * @param from     the start timestamp (inclusive)
     * @param to       the end timestamp (inclusive)
     * @param listener the listener to receive replayed messages
     * @return the number of messages replayed
     */
    public long replay(String channel, Instant from, Instant to, MessageListener<byte[]> listener) {
        String fromId = from.toEpochMilli() + "-0";
        String toId = to.toEpochMilli() + "-0";
        return replayPaginated(channel, fromId, toId, listener, DEFAULT_PAGE_SIZE);
    }

    /**
     * Replay with configurable page size for large ranges.
     */
    @SuppressWarnings("unchecked")
    public long replayPaginated(String channel, String fromId, String toId,
                                 MessageListener<byte[]> listener, int pageSize) {
        String streamKey = keyPrefix + channel;
        long totalReplayed = 0;
        String currentFromId = fromId;

        log.info("Starting replay on stream '{}' from '{}' to '{}' (page size: {})",
                streamKey, fromId, toId, pageSize);

        while (true) {
            org.springframework.data.domain.Range<String> range =
                    org.springframework.data.domain.Range.closed(currentFromId, toId);

            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .range(streamKey, range, org.springframework.data.redis.connection.Limit.limit().count(pageSize));

            if (records == null || records.isEmpty()) {
                break;
            }

            for (MapRecord<String, Object, Object> record : records) {
                Message<byte[]> message = convertRecord(record, channel);
                listener.onMessage(message, MessageAcknowledgment.NOOP);
                totalReplayed++;
            }

            if (records.size() < pageSize) {
                break;
            }

            // Move past the last record for the next page
            String lastId = records.get(records.size() - 1).getId().getValue();
            currentFromId = incrementStreamId(lastId);
        }

        log.info("Replay completed on stream '{}': {} messages replayed", streamKey, totalReplayed);
        return totalReplayed;
    }

    private Message<byte[]> convertRecord(MapRecord<String, Object, Object> record, String channel) {
        Map<String, String> fields = new LinkedHashMap<>();
        record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));

        String encodedPayload = fields.get(PAYLOAD_FIELD);
        byte[] payload = encodedPayload != null
                ? Base64.getDecoder().decode(encodedPayload)
                : new byte[0];

        Map<String, String> headerMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!PAYLOAD_FIELD.equals(entry.getKey())) {
                headerMap.put(entry.getKey(), entry.getValue());
            }
        }

        String messageId = headerMap.getOrDefault(MessageHeaders.MESSAGE_ID, record.getId().getValue());

        return Message.<byte[]>builder()
                .messageId(messageId)
                .channel(channel)
                .payload(payload)
                .headers(MessageHeaders.of(headerMap))
                .build();
    }

    /**
     * Increment a Redis stream ID to avoid re-reading the same record.
     * For "1234-5", returns "1234-6". For "1234", returns "1234-1".
     */
    private String incrementStreamId(String streamId) {
        int dashIndex = streamId.lastIndexOf('-');
        if (dashIndex > 0) {
            String prefix = streamId.substring(0, dashIndex);
            long sequence = Long.parseLong(streamId.substring(dashIndex + 1));
            return prefix + "-" + (sequence + 1);
        }
        return streamId + "-1";
    }
}
