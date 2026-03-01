package dev.simplecore.simplix.messaging.error;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * Routes failed messages to a dead letter queue (DLQ) channel.
 *
 * <p>The DLQ channel name is derived from the original channel by appending {@code .dlq}.
 * Additional headers are added to preserve the failure context:
 * <ul>
 *   <li>{@code x-dead-letter-reason} - the reason for routing to DLQ</li>
 *   <li>{@code x-original-channel} - the channel where the message originally failed</li>
 *   <li>{@code x-retry-count} - the number of retry attempts before DLQ routing</li>
 *   <li>{@code x-dead-lettered-at} - ISO-8601 timestamp of when the message was dead-lettered</li>
 * </ul>
 */
@Slf4j
public class DeadLetterStrategy {

    private static final String DLQ_SUFFIX = ".dlq";

    private final BrokerStrategy brokerStrategy;

    /**
     * Create a new dead letter strategy.
     *
     * @param brokerStrategy the broker strategy used to publish DLQ messages
     */
    public DeadLetterStrategy(BrokerStrategy brokerStrategy) {
        this.brokerStrategy = brokerStrategy;
    }

    /**
     * Send a failed message to its dead letter queue channel.
     *
     * @param originalMessage the original message that failed processing
     * @param reason          the reason for the failure
     * @return the publish result from the DLQ channel
     */
    public PublishResult send(Message<byte[]> originalMessage, String reason) {
        String originalChannel = originalMessage.getChannel();
        String dlqChannel = originalChannel + DLQ_SUFFIX;

        // Preserve existing retry count or default to "0"
        String retryCount = originalMessage.getHeaders()
                .get(MessageHeaders.RETRY_COUNT)
                .orElse("0");

        MessageHeaders dlqHeaders = originalMessage.getHeaders()
                .with(MessageHeaders.DEAD_LETTER_REASON, reason)
                .with(MessageHeaders.ORIGINAL_CHANNEL, originalChannel)
                .with(MessageHeaders.RETRY_COUNT, retryCount)
                .with(MessageHeaders.DEAD_LETTERED_AT, Instant.now().toString());

        byte[] payload = originalMessage.getPayload() != null
                ? originalMessage.getPayload()
                : new byte[0];

        log.info("Routing message to DLQ: channel='{}' -> '{}', reason='{}', messageId='{}'",
                originalChannel, dlqChannel, reason, originalMessage.getMessageId());

        return brokerStrategy.send(dlqChannel, payload, dlqHeaders);
    }

    /**
     * Derive the DLQ channel name from the original channel.
     *
     * @param channel the original channel name
     * @return the DLQ channel name
     */
    public static String dlqChannelFor(String channel) {
        return channel + DLQ_SUFFIX;
    }
}
