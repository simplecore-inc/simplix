package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RedisStreamReplayService implements ReplayService {

    private static final String PAYLOAD_FIELD = "payload";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisStreamReplayService(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public long replay(String channel, String fromId, String toId,
                       MessageListener<byte[]> listener) {
        return replayPaginated(channel, fromId, toId, listener, DEFAULT_PAGE_SIZE);
    }

    @Override
    public long replay(String channel, Instant from, Instant to,
                       MessageListener<byte[]> listener) {
        return replayPaginated(channel,
                from.toEpochMilli() + "-0",
                to.toEpochMilli() + "-0",
                listener, DEFAULT_PAGE_SIZE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public long replayPaginated(String channel, String fromId, String toId,
                                MessageListener<byte[]> listener, int pageSize) {
        String streamKey = keyPrefix + channel;
        long total = 0;
        String currentFromId = fromId;
        while (true) {
            Range<String> range = Range.closed(currentFromId, toId);
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .range(streamKey, range, Limit.limit().count(pageSize));
            if (records == null || records.isEmpty()) break;
            for (MapRecord<String, Object, Object> r : records) {
                listener.onMessage(toMessage(r, channel), MessageAcknowledgment.NOOP);
                total++;
            }
            if (records.size() < pageSize) break;
            currentFromId = incrementStreamId(records.get(records.size() - 1).getId().getValue());
        }
        return total;
    }

    private Message<byte[]> toMessage(MapRecord<String, Object, Object> r, String channel) {
        Map<String, String> fields = new LinkedHashMap<>();
        r.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));
        String enc = fields.get(PAYLOAD_FIELD);
        byte[] payload = enc != null ? Base64.getDecoder().decode(enc) : new byte[0];
        Map<String, String> hdr = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fields.entrySet())
            if (!PAYLOAD_FIELD.equals(e.getKey())) hdr.put(e.getKey(), e.getValue());
        String mid = hdr.getOrDefault(MessageHeaders.MESSAGE_ID, r.getId().getValue());
        return Message.<byte[]>builder().messageId(mid).channel(channel)
                .payload(payload).headers(MessageHeaders.of(hdr)).build();
    }

    private String incrementStreamId(String id) {
        int dash = id.lastIndexOf('-');
        if (dash > 0) {
            return id.substring(0, dash) + "-" + (Long.parseLong(id.substring(dash + 1)) + 1);
        }
        return id + "-1";
    }
}
