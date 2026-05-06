package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.DiscardPolicy;
import io.nats.client.api.ReplayPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;

/**
 * Manages JetStream streams and durable consumers for the NATS broker.
 *
 * <p>Handles idempotent stream creation (update-on-exists), durable/ephemeral
 * consumer setup, and helper utilities used by the NATS broker strategy.
 */
@Slf4j
public class NatsConsumerGroupManager {

    /** JetStream API error code returned when a stream with the same name already exists. */
    private static final int STREAM_ALREADY_EXISTS_CODE = 10058;

    private final JetStreamManagement jsm;
    private final MessagingProperties messagingProperties;

    public NatsConsumerGroupManager(JetStreamManagement jsm, MessagingProperties messagingProperties) {
        this.jsm = jsm;
        this.messagingProperties = messagingProperties;
    }

    /**
     * Returns the JetStream stream name for the given channel.
     * Format: {@code <streamPrefix><channel>}
     *
     * @param channel the logical channel name
     * @return the resolved stream name
     */
    public String resolveStreamName(String channel) {
        return messagingProperties.getNats().getStreamPrefix() + channel;
    }

    /**
     * Returns the NATS subject for the given channel.
     * Format: {@code <subjectPrefix><channel>}
     *
     * @param channel the logical channel name
     * @return the resolved subject string
     */
    public String resolveSubject(String channel) {
        return messagingProperties.getNats().getSubjectPrefix() + channel;
    }

    /**
     * Returns whether the stream for the given channel already exists in JetStream.
     *
     * @param channel the logical channel name
     * @return {@code true} if the stream exists, {@code false} otherwise
     */
    public boolean streamExists(String channel) {
        try {
            jsm.getStreamInfo(resolveStreamName(channel));
            return true;
        } catch (JetStreamApiException | IOException e) {
            return false;
        }
    }

    /**
     * Creates the JetStream stream for the given channel, or updates it if it already exists.
     *
     * <p>Stream configuration is derived from {@link MessagingProperties.NatsProperties}.
     * If {@code addStream} throws a {@link JetStreamApiException} with API error code 10058
     * (stream already exists), {@code updateStream} is called instead.
     *
     * @param channel the logical channel name
     * @throws IllegalStateException if stream creation or update fails
     */
    public void ensureStream(String channel) {
        MessagingProperties.NatsProperties nats = messagingProperties.getNats();
        String streamName = resolveStreamName(channel);
        String subject = resolveSubject(channel);

        // When auto-create is disabled the application acts as a publish/subscribe-only
        // client; the stream must already be provisioned externally (e.g., NATS CLI or IaC).
        if (!nats.isAutoCreateStreams()) {
            if (streamExists(channel)) {
                log.debug("Stream '{}' exists; auto-create disabled, skipping ensure", streamName);
                return;
            }
            throw new IllegalStateException(
                    "Stream " + streamName + " is not provisioned and "
                            + "simplix.messaging.nats.auto-create-streams=false. "
                            + "Provision the stream externally before starting the application.");
        }

        StreamConfiguration config = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .storageType(parseStorageType(nats.getStorage()))
                .retentionPolicy(parseRetentionPolicy(nats.getRetention()))
                .maxMessages(nats.resolveMaxMsgs(channel, messagingProperties))
                .maxAge(nats.getMaxAge())
                .maxBytes(nats.getMaxBytes())
                .replicas(nats.getReplicas())
                .duplicateWindow(nats.resolveDuplicateWindow(channel, messagingProperties))
                .discardPolicy(parseDiscardPolicy(nats.getDiscardPolicy()))
                .build();

        try {
            jsm.addStream(config);
            log.info("Created JetStream stream '{}' on subject '{}'", streamName, subject);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == STREAM_ALREADY_EXISTS_CODE) {
                if (!nats.isAutoUpdateStreams()) {
                    log.debug("Stream '{}' exists; auto-update disabled, leaving config untouched", streamName);
                    return;
                }
                try {
                    jsm.updateStream(config);
                    log.debug("Updated existing JetStream stream '{}'", streamName);
                } catch (JetStreamApiException | IOException ex) {
                    throw new IllegalStateException("Failed to update stream " + streamName, ex);
                }
            } else {
                throw new IllegalStateException("Failed to create stream " + streamName, e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create stream " + streamName, e);
        }
    }

    /**
     * Ensures a durable (or ephemeral) consumer exists on the stream for the given channel.
     *
     * <p>If {@code groupName} is non-empty, a durable consumer with that name is created.
     * An empty {@code groupName} creates an ephemeral consumer (no durable name set).
     *
     * @param channel   the logical channel name
     * @param groupName the consumer group name; empty string for ephemeral
     * @throws IllegalStateException if consumer creation fails
     */
    public void ensureConsumerGroup(String channel, String groupName) {
        MessagingProperties.NatsProperties nats = messagingProperties.getNats();
        String streamName = resolveStreamName(channel);
        String subject = resolveSubject(channel);

        ConsumerConfiguration.Builder cc = ConsumerConfiguration.builder()
                .ackPolicy(parseAckPolicy(nats.getAckPolicy()))
                .ackWait(nats.resolveAckWait(messagingProperties.getError()))
                .maxDeliver(nats.resolveMaxDeliver(messagingProperties.getError()))
                .deliverPolicy(parseDeliverPolicy(nats.resolveDeliverPolicy(channel, messagingProperties)))
                .filterSubject(subject)
                .replayPolicy(ReplayPolicy.Instant);

        if (groupName != null && !groupName.isEmpty()) {
            cc.durable(groupName);
        }

        try {
            jsm.addOrUpdateConsumer(streamName, cc.build());
            log.info("Ensured consumer '{}' on stream '{}' (subject={})",
                    (groupName == null || groupName.isEmpty()) ? "<ephemeral>" : groupName,
                    streamName, subject);
        } catch (JetStreamApiException | IOException e) {
            throw new IllegalStateException("Failed to add/update consumer for stream " + streamName, e);
        }
    }

    /**
     * Retrieves the current {@link ConsumerInfo} for the named consumer on the given channel's stream.
     *
     * @param channel   the logical channel name
     * @param groupName the durable consumer name
     * @return the consumer info, or {@code null} if not found or on error
     */
    public ConsumerInfo getConsumerInfo(String channel, String groupName) {
        try {
            return jsm.getConsumerInfo(resolveStreamName(channel), groupName);
        } catch (JetStreamApiException | IOException e) {
            return null;
        }
    }

    /**
     * Returns the number of pending (undelivered) messages for the given consumer.
     *
     * @param channel   the logical channel name
     * @param groupName the durable consumer name
     * @return the pending message count, or {@code 0} if the consumer info is unavailable
     */
    public long getPendingCount(String channel, String groupName) {
        ConsumerInfo info = getConsumerInfo(channel, groupName);
        return info != null ? info.getNumPending() : 0L;
    }

    /**
     * Generates a unique consumer instance name combining the instance ID and a short UUID suffix.
     *
     * @param instanceId the application instance identifier
     * @return a unique consumer name of the form {@code <instanceId>-<8-char-uuid-suffix>}
     */
    public String generateConsumerName(String instanceId) {
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return instanceId + "-" + shortUuid;
    }

    // ---------------------------------------------------------------
    // Private parsing helpers
    // ---------------------------------------------------------------

    private static StorageType parseStorageType(String s) {
        return "memory".equalsIgnoreCase(s) ? StorageType.Memory : StorageType.File;
    }

    private static RetentionPolicy parseRetentionPolicy(String s) {
        if ("interest".equalsIgnoreCase(s)) return RetentionPolicy.Interest;
        if ("workqueue".equalsIgnoreCase(s)) return RetentionPolicy.WorkQueue;
        return RetentionPolicy.Limits;
    }

    private static DiscardPolicy parseDiscardPolicy(String s) {
        return "new".equalsIgnoreCase(s) ? DiscardPolicy.New : DiscardPolicy.Old;
    }

    private static AckPolicy parseAckPolicy(String s) {
        if ("all".equalsIgnoreCase(s)) return AckPolicy.All;
        if ("none".equalsIgnoreCase(s)) return AckPolicy.None;
        return AckPolicy.Explicit;
    }

    private static DeliverPolicy parseDeliverPolicy(String s) {
        if (s == null) return DeliverPolicy.All;
        switch (s.toLowerCase()) {
            case "new":
                return DeliverPolicy.New;
            case "last":
                return DeliverPolicy.Last;
            case "last_per_subject":
            case "lastpersubject":
                return DeliverPolicy.LastPerSubject;
            case "all":
            default:
                return DeliverPolicy.All;
        }
    }
}
