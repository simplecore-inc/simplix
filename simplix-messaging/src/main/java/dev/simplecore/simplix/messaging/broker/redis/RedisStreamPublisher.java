package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes messages to Redis Streams.
 *
 * <p>Supports two payload encoding strategies:
 * <ul>
 *   <li>{@link PayloadEncoding#BASE64} - encodes binary payload as Base64 string (default)</li>
 *   <li>{@link PayloadEncoding#RAW} - stores raw binary bytes directly via Redis connection</li>
 * </ul>
 *
 * <p>Headers are always stored as string-valued fields. Stream trimming is applied
 * via MAXLEN ~ (approximate) to bound memory usage.
 */
@Slf4j
public class RedisStreamPublisher {

    private static final Logger MESSAGE_TRACE = LoggerFactory.getLogger("MESSAGE_TRACE");
    private static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final long maxLength;
    private final PayloadEncoding payloadEncoding;

    public RedisStreamPublisher(StringRedisTemplate redisTemplate, String keyPrefix,
                                 long maxLength, PayloadEncoding payloadEncoding) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.maxLength = maxLength;
        this.payloadEncoding = payloadEncoding;
    }

    public RedisStreamPublisher(StringRedisTemplate redisTemplate, String keyPrefix, long maxLength) {
        this(redisTemplate, keyPrefix, maxLength, PayloadEncoding.BASE64);
    }

    public RedisStreamPublisher(StringRedisTemplate redisTemplate, String keyPrefix) {
        this(redisTemplate, keyPrefix, 50_000L, PayloadEncoding.BASE64);
    }

    /**
     * Send a message to the specified channel.
     *
     * @param channel the logical channel name
     * @param payload the binary payload
     * @param headers the message headers
     * @return the publish result with the Redis record ID
     */
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        if (payloadEncoding == PayloadEncoding.RAW) {
            return sendRaw(channel, payload, headers);
        }
        return sendBase64(channel, payload, headers);
    }

    private PublishResult sendBase64(String channel, byte[] payload, MessageHeaders headers) {
        String streamKey = resolveKey(channel);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PAYLOAD_FIELD, Base64.getEncoder().encodeToString(payload));
        fields.putAll(headers.toMap());

        MapRecord<String, String, String> record = StreamRecords
                .string(fields)
                .withStreamKey(streamKey);

        RecordId recordId = redisTemplate.opsForStream().add(record);
        trimStream(streamKey);

        String recordIdStr = recordId != null ? recordId.getValue() : "unknown";
        log.debug("Published message to stream '{}' with record ID '{}' [encoding=BASE64]",
                streamKey, recordIdStr);

        traceMessage("OUT", channel, recordIdStr,
                headers.get(MessageHeaders.MESSAGE_ID).orElse(recordIdStr),
                payload, headers);

        return new PublishResult(recordIdStr, channel, Instant.now());
    }

    private PublishResult sendRaw(String channel, byte[] payload, MessageHeaders headers) {
        String streamKey = resolveKey(channel);
        byte[] streamKeyBytes = streamKey.getBytes(StandardCharsets.UTF_8);

        Map<byte[], byte[]> rawFields = new LinkedHashMap<>();
        rawFields.put(PAYLOAD_FIELD.getBytes(StandardCharsets.UTF_8), payload);
        for (Map.Entry<String, String> entry : headers.toMap().entrySet()) {
            rawFields.put(
                    entry.getKey().getBytes(StandardCharsets.UTF_8),
                    entry.getValue().getBytes(StandardCharsets.UTF_8)
            );
        }

        ByteRecord record = StreamRecords.rawBytes(rawFields).withStreamKey(streamKeyBytes);

        RecordId recordId = redisTemplate.execute((RedisCallback<RecordId>) connection ->
                connection.streamCommands().xAdd(record));

        trimStream(streamKey);

        String recordIdStr = recordId != null ? recordId.getValue() : "unknown";
        log.debug("Published message to stream '{}' with record ID '{}' [encoding=RAW]",
                streamKey, recordIdStr);

        traceMessage("OUT", channel, recordIdStr,
                headers.get(MessageHeaders.MESSAGE_ID).orElse(recordIdStr),
                payload, headers);

        return new PublishResult(recordIdStr, channel, Instant.now());
    }

    private void trimStream(String streamKey) {
        try {
            redisTemplate.opsForStream().trim(streamKey, maxLength, true);
        } catch (Exception e) {
            log.warn("Failed to trim stream '{}': {}", streamKey, e.getMessage());
        }
    }

    private void traceMessage(String direction, String channel, String recordId,
                               String messageId, byte[] payload, MessageHeaders headers) {
        if (!MESSAGE_TRACE.isInfoEnabled()) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"ts\":\"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            sb.append("\",\"dir\":\"").append(direction);
            sb.append("\",\"ch\":\"").append(escapeJson(channel));
            sb.append("\",\"recordId\":\"").append(escapeJson(recordId));
            sb.append("\",\"msgId\":\"").append(escapeJson(messageId));
            sb.append("\",\"size\":").append(payload.length);
            sb.append(",\"headers\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : headers.toMap().entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(escapeJson(entry.getKey())).append("\":\"")
                        .append(escapeJson(entry.getValue())).append('"');
                first = false;
            }
            sb.append("},\"payload\":\"").append(Base64.getEncoder().encodeToString(payload));
            sb.append("\"}");
            MESSAGE_TRACE.info(sb.toString());
        } catch (Exception e) {
            log.debug("Failed to trace message: {}", e.getMessage());
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String resolveKey(String channel) {
        return keyPrefix + channel;
    }
}
