package dev.simplecore.simplix.stream.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Message to be sent to stream clients.
 */
@Getter
@Builder
@ToString
public class StreamMessage {

    /**
     * Message type
     */
    private final MessageType type;

    /**
     * Subscription key this message belongs to
     */
    private final String subscriptionKey;

    /**
     * Resource name
     */
    private final String resource;

    /**
     * Message payload (data)
     */
    private final Object payload;

    /**
     * When the message was created
     */
    private final Instant timestamp;

    /**
     * Error code (for ERROR type messages)
     */
    private final String errorCode;

    /**
     * Error/info message
     */
    private final String message;

    /**
     * Message type enum
     */
    public enum MessageType {
        /**
         * Data message containing collected data
         */
        DATA,

        /**
         * Heartbeat message to keep connection alive
         */
        HEARTBEAT,

        /**
         * Error message
         */
        ERROR,

        /**
         * Subscription removed notification
         */
        SUBSCRIPTION_REMOVED,

        /**
         * Connected acknowledgement
         */
        CONNECTED,

        /**
         * Reconnected acknowledgement (session restored)
         */
        RECONNECTED
    }

    /**
     * Create a data message.
     *
     * @param key     the subscription key
     * @param payload the data payload
     * @return the message
     */
    public static StreamMessage data(SubscriptionKey key, Object payload) {
        return StreamMessage.builder()
                .type(MessageType.DATA)
                .subscriptionKey(key.toKeyString())
                .resource(key.getResource())
                .payload(payload)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a heartbeat message.
     *
     * @return the message
     */
    public static StreamMessage heartbeat() {
        return StreamMessage.builder()
                .type(MessageType.HEARTBEAT)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error message.
     *
     * @param key       the subscription key
     * @param errorCode the error code
     * @param message   the error message
     * @return the message
     */
    public static StreamMessage error(SubscriptionKey key, String errorCode, String message) {
        return StreamMessage.builder()
                .type(MessageType.ERROR)
                .subscriptionKey(key != null ? key.toKeyString() : null)
                .resource(key != null ? key.getResource() : null)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a subscription removed message.
     *
     * @param key    the subscription key
     * @param reason the removal reason
     * @return the message
     */
    public static StreamMessage subscriptionRemoved(SubscriptionKey key, String reason) {
        return StreamMessage.builder()
                .type(MessageType.SUBSCRIPTION_REMOVED)
                .subscriptionKey(key.toKeyString())
                .resource(key.getResource())
                .message(reason)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a connected message.
     *
     * @param sessionId the session ID
     * @return the message
     */
    public static StreamMessage connected(String sessionId) {
        return StreamMessage.builder()
                .type(MessageType.CONNECTED)
                .payload(new ConnectedPayload(sessionId, Instant.now()))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a reconnected message.
     *
     * @param sessionId               the session ID
     * @param restoredSubscriptionKeys the restored subscription keys
     * @return the message
     */
    public static StreamMessage reconnected(String sessionId, java.util.List<String> restoredSubscriptionKeys) {
        return StreamMessage.builder()
                .type(MessageType.RECONNECTED)
                .payload(new ReconnectedPayload(sessionId, restoredSubscriptionKeys, Instant.now()))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Payload for connected message
     */
    public record ConnectedPayload(String sessionId, Instant serverTime) {
    }

    /**
     * Payload for reconnected message
     */
    public record ReconnectedPayload(String sessionId, java.util.List<String> restoredSubscriptions, Instant serverTime) {
    }
}
