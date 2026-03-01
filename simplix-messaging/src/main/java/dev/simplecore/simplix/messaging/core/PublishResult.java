package dev.simplecore.simplix.messaging.core;

import java.time.Instant;

/**
 * Result of a message publish operation.
 *
 * @param recordId  the broker-assigned record identifier (e.g., Redis Stream entry ID)
 * @param channel   the target channel the message was published to
 * @param timestamp the time the message was accepted by the broker
 */
public record PublishResult(
        String recordId,
        String channel,
        Instant timestamp
) {
}
