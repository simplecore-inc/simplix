package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.redis.RedisStreamReplayService;
import dev.simplecore.simplix.messaging.core.MessageListener;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

/**
 * @deprecated since 1.1.1, use {@link ReplayService} (broker-agnostic SPI) instead.
 *             For Redis Streams, inject {@link RedisStreamReplayService} directly.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "1.1.1", forRemoval = true)
public class StreamReplayService implements ReplayService {

    private final RedisStreamReplayService delegate;

    public StreamReplayService(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.delegate = new RedisStreamReplayService(redisTemplate, keyPrefix);
    }

    @Override
    public long replay(String channel, String fromId, String toId, MessageListener<byte[]> listener) {
        return delegate.replay(channel, fromId, toId, listener);
    }

    @Override
    public long replay(String channel, Instant from, Instant to, MessageListener<byte[]> listener) {
        return delegate.replay(channel, from, to, listener);
    }

    @Override
    public long replayPaginated(String channel, String fromId, String toId,
                                 MessageListener<byte[]> listener, int pageSize) {
        return delegate.replayPaginated(channel, fromId, toId, listener, pageSize);
    }
}
