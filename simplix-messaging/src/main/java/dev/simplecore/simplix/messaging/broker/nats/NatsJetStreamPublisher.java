package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes messages to NATS JetStream subjects.
 *
 * <p>Headers are transmitted as native NATS headers. The simplix
 * {@link MessageHeaders#MESSAGE_ID} value is also written to the NATS
 * {@code Nats-Msg-Id} header to enable publish-time deduplication via the
 * stream's configured {@code duplicate_window}.
 *
 * <p>When {@code simplix.messaging.publisher.auto-message-id=true} the publisher
 * assigns a fresh UUIDv4 to {@code MESSAGE_ID} (and {@code Nats-Msg-Id}) when the
 * caller did not provide one. This prevents an application-level retry that
 * republishes the same payload from being silently dropped by NATS dedup.
 *
 * <p>If the broker reports the publish as a duplicate
 * ({@link PublishAck#isDuplicate()}), the returned {@link PublishResult#duplicate()}
 * is {@code true} and a WARN is logged so silent dedup hits surface in operations.
 */
@Slf4j
public class NatsJetStreamPublisher {

    private static final String NATS_MSG_ID_HEADER = "Nats-Msg-Id";

    private final JetStream jetStream;
    private final NatsConsumerGroupManager groupManager;
    private final MessagingProperties messagingProperties;

    public NatsJetStreamPublisher(JetStream jetStream,
                                   NatsConsumerGroupManager groupManager,
                                   MessagingProperties messagingProperties) {
        this.jetStream = jetStream;
        this.groupManager = groupManager;
        this.messagingProperties = messagingProperties;
    }

    /**
     * Backwards-compatible constructor that disables auto-message-id and duplicate
     * surfacing logging-only behavior. Prefer the three-argument constructor.
     */
    public NatsJetStreamPublisher(JetStream jetStream, NatsConsumerGroupManager groupManager) {
        this(jetStream, groupManager, new MessagingProperties());
    }

    /**
     * Publishes a message to the NATS JetStream subject resolved for the given channel.
     *
     * <p>All entries in {@code headers} are forwarded as native NATS headers. When
     * {@link MessageHeaders#MESSAGE_ID} is present (or auto-generated) it is also
     * added under the {@code Nats-Msg-Id} header to activate stream-level publish
     * deduplication.
     *
     * @param channel the logical channel name
     * @param payload the raw message bytes
     * @param headers the message metadata headers
     * @return a {@link PublishResult} whose {@code recordId} is {@code <stream>-<seqno>}
     *         and whose {@code duplicate} flag reflects {@link PublishAck#isDuplicate()}
     * @throws IllegalStateException if the JetStream publish call fails
     */
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        String streamName = groupManager.resolveStreamName(channel);
        String subject = groupManager.resolveSubject(channel);

        String messageId = headers.get(MessageHeaders.MESSAGE_ID).orElse(null);
        MessageHeaders effectiveHeaders = headers;
        if (messageId == null && messagingProperties.getPublisher().isAutoMessageId()) {
            messageId = UUID.randomUUID().toString();
            effectiveHeaders = headers.with(MessageHeaders.MESSAGE_ID, messageId);
        }

        Headers natsHeaders = new Headers();
        effectiveHeaders.toMap().forEach((k, v) -> natsHeaders.add(k, v));
        if (messageId != null) {
            natsHeaders.add(NATS_MSG_ID_HEADER, messageId);
        }

        Message natsMsg = NatsMessage.builder()
                .subject(subject)
                .headers(natsHeaders)
                .data(payload)
                .build();

        PublishOptions.Builder optsBuilder = PublishOptions.builder().stream(streamName);
        if (messageId != null) {
            optsBuilder.messageId(messageId);
        }

        try {
            PublishAck ack = jetStream.publish(natsMsg, optsBuilder.build());
            String recordId = ack.getStream() + "-" + ack.getSeqno();
            boolean duplicate = ack.isDuplicate();
            if (duplicate) {
                log.warn("NATS reported duplicate publish [stream='{}', seqno={}, channel='{}', messageId='{}']. "
                                + "The message was NOT delivered to subscribers (silent dedup). "
                                + "Verify your retry policy assigns a fresh MessageHeaders.MESSAGE_ID "
                                + "or enable simplix.messaging.publisher.auto-message-id=true.",
                        ack.getStream(), ack.getSeqno(), channel, messageId);
            } else {
                log.debug("Published to subject '{}' (stream='{}', seqno={})",
                        subject, ack.getStream(), ack.getSeqno());
            }
            return new PublishResult(recordId, channel, Instant.now(), duplicate);
        } catch (JetStreamApiException | IOException e) {
            throw new IllegalStateException(
                    "Failed to publish message to subject " + subject, e);
        }
    }
}
