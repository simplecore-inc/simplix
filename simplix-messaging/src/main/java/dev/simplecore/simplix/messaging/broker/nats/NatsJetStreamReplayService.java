package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.ReplayPolicy;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NATS JetStream implementation of {@link ReplayService}.
 *
 * <p>Creates ephemeral push consumers with {@code DeliverPolicy.ByStartSequence} or
 * {@code DeliverPolicy.ByStartTime} to replay historical messages from a JetStream stream.
 * The consumer is unsubscribed (and therefore cleaned up) when replay completes.
 */
@Slf4j
public class NatsJetStreamReplayService implements ReplayService {

    private static final Duration NEXT_MESSAGE_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration EPHEMERAL_INACTIVE_THRESHOLD = Duration.ofMinutes(1);

    private final JetStream jetStream;
    private final NatsConsumerGroupManager groupManager;

    public NatsJetStreamReplayService(JetStream jetStream, NatsConsumerGroupManager groupManager) {
        this.jetStream = jetStream;
        this.groupManager = groupManager;
    }

    @Override
    public long replay(String channel, String fromId, String toId, MessageListener<byte[]> listener) {
        long fromSeq = parseFromSeq(fromId);
        long toSeq = parseToSeq(toId);

        ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .ackPolicy(AckPolicy.None)
                .deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(Math.max(fromSeq, 1L))
                .filterSubject(groupManager.resolveSubject(channel))
                .replayPolicy(ReplayPolicy.Instant)
                .inactiveThreshold(EPHEMERAL_INACTIVE_THRESHOLD)
                .build();
        return runReplay(channel, cc, toSeq, null, listener);
    }

    @Override
    public long replay(String channel, Instant from, Instant to, MessageListener<byte[]> listener) {
        ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .ackPolicy(AckPolicy.None)
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(ZonedDateTime.ofInstant(from, ZoneOffset.UTC))
                .filterSubject(groupManager.resolveSubject(channel))
                .replayPolicy(ReplayPolicy.Instant)
                .inactiveThreshold(EPHEMERAL_INACTIVE_THRESHOLD)
                .build();
        return runReplay(channel, cc, Long.MAX_VALUE, to, listener);
    }

    @Override
    public long replayPaginated(String channel, String fromId, String toId,
                                MessageListener<byte[]> listener, int pageSize) {
        // NATS push consumer naturally streams all messages; pageSize is informational.
        return replay(channel, fromId, toId, listener);
    }

    // ------------------------------------------------------------------
    // Internal replay loop
    // ------------------------------------------------------------------

    private long runReplay(String channel, ConsumerConfiguration cc, long toSeq, Instant toTime,
                           MessageListener<byte[]> listener) {
        String streamName = groupManager.resolveStreamName(channel);
        String subject = groupManager.resolveSubject(channel);
        PushSubscribeOptions opts = PushSubscribeOptions.builder()
                .stream(streamName)
                .configuration(cc)
                .build();

        JetStreamSubscription sub;
        try {
            sub = jetStream.subscribe(subject, opts);
        } catch (JetStreamApiException | IOException e) {
            throw new IllegalStateException("Failed to start replay on stream " + streamName, e);
        }

        long count = 0;
        try {
            while (true) {
                io.nats.client.Message m;
                try {
                    m = sub.nextMessage(NEXT_MESSAGE_TIMEOUT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (m == null) {
                    break;
                }

                long seq = m.metaData() != null ? m.metaData().streamSequence() : 0L;
                if (seq > toSeq) {
                    break;
                }
                if (toTime != null && m.metaData() != null
                        && m.metaData().timestamp().toInstant().isAfter(toTime)) {
                    break;
                }

                listener.onMessage(toMessage(m, channel, streamName), MessageAcknowledgment.NOOP);
                count++;
            }
        } finally {
            try {
                sub.unsubscribe();
            } catch (Exception e) {
                log.debug("Replay unsubscribe error: {}", e.getMessage());
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Message conversion
    // ------------------------------------------------------------------

    private Message<byte[]> toMessage(io.nats.client.Message natsMsg, String channel, String streamName) {
        Map<String, String> headerMap = new LinkedHashMap<>();
        Headers natsHeaders = natsMsg.getHeaders();
        if (natsHeaders != null) {
            for (String key : natsHeaders.keySet()) {
                List<String> values = natsHeaders.get(key);
                if (values != null && !values.isEmpty()) {
                    headerMap.put(key, values.get(0));
                }
            }
        }

        byte[] payload = natsMsg.getData();
        if (payload == null) {
            payload = new byte[0];
        }

        long seq = natsMsg.metaData() != null ? natsMsg.metaData().streamSequence() : 0L;
        String recordId = streamName + "-" + seq;
        String messageId = headerMap.getOrDefault(MessageHeaders.MESSAGE_ID, recordId);

        return Message.<byte[]>builder()
                .messageId(messageId)
                .channel(channel)
                .payload(payload)
                .headers(MessageHeaders.of(headerMap))
                .build();
    }

    // ------------------------------------------------------------------
    // Sequence ID parsing
    // ------------------------------------------------------------------

    /**
     * Parses the from-sequence from a record ID.
     * Accepts {@code null}, empty, or {@code "0"} as meaning "start from sequence 1".
     * Otherwise parses the numeric suffix after the last dash.
     */
    private long parseFromSeq(String fromId) {
        if (fromId == null || fromId.isEmpty() || "0".equals(fromId)) {
            return 1L;
        }
        return parseSeqFromRecordId(fromId, 1L);
    }

    /**
     * Parses the to-sequence from a record ID.
     * Accepts {@code null}, empty, or {@code "+"} as meaning "no upper bound".
     * Otherwise parses the numeric suffix after the last dash.
     */
    private long parseToSeq(String toId) {
        if (toId == null || toId.isEmpty() || "+".equals(toId)) {
            return Long.MAX_VALUE;
        }
        return parseSeqFromRecordId(toId, Long.MAX_VALUE);
    }

    /**
     * Extracts the sequence number from a record ID of the form {@code <streamName>-<seq>}
     * or plain {@code <seq>}. Uses the substring after the last {@code '-'} character.
     *
     * @param id       the record ID string
     * @param fallback value to return if parsing fails
     * @return the parsed sequence number, or fallback on error
     */
    private long parseSeqFromRecordId(String id, long fallback) {
        int dash = id.lastIndexOf('-');
        try {
            return Long.parseLong(id.substring(dash + 1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
