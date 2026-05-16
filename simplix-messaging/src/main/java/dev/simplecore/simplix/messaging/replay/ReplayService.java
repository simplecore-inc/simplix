package dev.simplecore.simplix.messaging.replay;

import dev.simplecore.simplix.messaging.core.MessageListener;

import java.time.Instant;

/**
 * Replays historical messages from a channel within a given range.
 *
 * <p>Brokers that support replay register an implementation. Brokers that do
 * not support replay declare {@code BrokerCapabilities.replay() == false} and
 * do not register a bean.
 */
public interface ReplayService {

    /** Replay by broker-specific record ID range, returns the number of messages replayed. */
    long replay(String channel, String fromId, String toId, MessageListener<byte[]> listener);

    /** Replay by timestamp range. */
    long replay(String channel, Instant from, Instant to, MessageListener<byte[]> listener);

    /** Replay with explicit page size for very large ranges. */
    long replayPaginated(String channel, String fromId, String toId,
                         MessageListener<byte[]> listener, int pageSize);
}
