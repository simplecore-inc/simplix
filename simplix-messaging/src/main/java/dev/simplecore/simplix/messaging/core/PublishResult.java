package dev.simplecore.simplix.messaging.core;

import java.time.Instant;

/**
 * Result of a message publish operation.
 *
 * @param recordId  the broker-assigned record identifier (e.g., Redis Stream entry ID,
 *                  or {@code <stream>-<seqno>} for NATS JetStream)
 * @param channel   the target channel the message was published to
 * @param timestamp the time the message was accepted by the broker
 * @param duplicate {@code true} if the broker detected this message as a duplicate of
 *                  a previously accepted publish (NATS JetStream
 *                  {@code PublishAck.duplicate}); {@code false} otherwise. Brokers
 *                  without publish-time deduplication always return {@code false}.
 */
public record PublishResult(
        String recordId,
        String channel,
        Instant timestamp,
        boolean duplicate
) {

    /**
     * Backwards-compatible constructor for brokers that do not surface duplicate detection.
     * Equivalent to {@code new PublishResult(recordId, channel, timestamp, false)}.
     */
    public PublishResult(String recordId, String channel, Instant timestamp) {
        this(recordId, channel, timestamp, false);
    }
}
