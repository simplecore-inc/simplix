package dev.simplecore.simplix.messaging.broker.local;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageListener;
import dev.simplecore.simplix.messaging.replay.ReplayService;

import java.time.Instant;

public class LocalReplayService implements ReplayService {

    private final LocalBrokerStrategy broker;

    public LocalReplayService(LocalBrokerStrategy broker) {
        this.broker = broker;
    }

    @Override
    public long replay(String channel, String fromId, String toId,
                       MessageListener<byte[]> listener) {
        return replayPaginated(channel, fromId, toId, listener, Integer.MAX_VALUE);
    }

    @Override
    public long replay(String channel, Instant from, Instant to,
                       MessageListener<byte[]> listener) {
        long count = 0;
        for (LocalBrokerStrategy.PublishedMessage pm : broker.getPublishedMessages(channel)) {
            if (pm.timestamp().isBefore(from) || pm.timestamp().isAfter(to)) continue;
            listener.onMessage(toMessage(pm), MessageAcknowledgment.NOOP);
            count++;
        }
        return count;
    }

    @Override
    public long replayPaginated(String channel, String fromId, String toId,
                                MessageListener<byte[]> listener, int pageSize) {
        boolean anyId = "0".equals(fromId);
        boolean anyEnd = "+".equals(toId);
        long count = 0;
        for (LocalBrokerStrategy.PublishedMessage pm : broker.getPublishedMessages(channel)) {
            if (!anyId && pm.recordId().compareTo(fromId) < 0) continue;
            if (!anyEnd && pm.recordId().compareTo(toId) > 0) continue;
            listener.onMessage(toMessage(pm), MessageAcknowledgment.NOOP);
            count++;
            if (count >= pageSize) break;
        }
        return count;
    }

    private Message<byte[]> toMessage(LocalBrokerStrategy.PublishedMessage pm) {
        return Message.<byte[]>builder()
                .messageId(pm.recordId())
                .channel(pm.channel())
                .payload(pm.payload())
                .headers(pm.headers())
                .timestamp(pm.timestamp())
                .build();
    }
}
