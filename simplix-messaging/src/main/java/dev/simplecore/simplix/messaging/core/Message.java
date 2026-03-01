package dev.simplecore.simplix.messaging.core;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Generic message envelope carrying a typed payload with metadata.
 *
 * <p>Messages are immutable. Use the {@link Builder} or convenience factory methods
 * to create instances.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Raw bytes
 * Message<byte[]> raw = Message.ofBytes("my-channel", payload);
 *
 * // Wire protobuf (ADAPTER auto-detected via reflection)
 * Message<byte[]> proto = Message.ofProtobuf("sync-commands", syncCommand);
 * }</pre>
 *
 * @param <T> the payload type
 */
public final class Message<T> {

    private final String messageId;
    private final String channel;
    private final T payload;
    private final MessageHeaders headers;
    private final Instant timestamp;

    private Message(Builder<T> builder) {
        this.messageId = builder.messageId != null ? builder.messageId : UUID.randomUUID().toString();
        this.channel = Objects.requireNonNull(builder.channel, "channel must not be null");
        this.payload = builder.payload;
        this.headers = builder.headers != null ? builder.headers : MessageHeaders.empty();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getChannel() {
        return channel;
    }

    public T getPayload() {
        return payload;
    }

    public MessageHeaders getHeaders() {
        return headers;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // ---------------------------------------------------------------
    // Factory methods
    // ---------------------------------------------------------------

    /**
     * Create a message carrying raw bytes.
     */
    public static Message<byte[]> ofBytes(String channel, byte[] payload) {
        return Message.<byte[]>builder()
                .channel(channel)
                .payload(payload)
                .headers(MessageHeaders.empty().with(MessageHeaders.CONTENT_TYPE, "application/octet-stream"))
                .build();
    }

    /**
     * Create a message from a Wire protobuf object. The protobuf binary is produced
     * by reflectively locating the static {@code ADAPTER} field on the message class,
     * then calling {@code encode()}.
     *
     * @param channel      the target channel
     * @param protoMessage the Wire protobuf message instance
     * @param <M>          the Wire Message type
     * @return a message carrying the encoded bytes
     */
    @SuppressWarnings("unchecked")
    public static <M> Message<byte[]> ofProtobuf(String channel, M protoMessage) {
        Objects.requireNonNull(protoMessage, "protoMessage must not be null");
        try {
            Field adapterField = protoMessage.getClass().getField("ADAPTER");
            Object adapter = adapterField.get(null);
            var encodeMethod = adapter.getClass().getMethod("encode", Object.class);
            byte[] bytes = (byte[]) encodeMethod.invoke(adapter, protoMessage);

            return Message.<byte[]>builder()
                    .channel(channel)
                    .payload(bytes)
                    .headers(MessageHeaders.empty().with(MessageHeaders.CONTENT_TYPE, "application/protobuf"))
                    .build();
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                    "No ADAPTER field found on " + protoMessage.getClass().getName()
                    + ". Ensure this is a Wire-generated protobuf class.", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to encode protobuf message via ADAPTER", e);
        }
    }

    /**
     * Create a new builder.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    // ---------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------

    public static final class Builder<T> {
        private String messageId;
        private String channel;
        private T payload;
        private MessageHeaders headers;
        private Instant timestamp;

        private Builder() {
        }

        public Builder<T> messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder<T> channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Builder<T> headers(MessageHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Message<T> build() {
            return new Message<>(this);
        }
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", channel='" + channel + '\'' +
                ", headers=" + headers +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message<?> that)) return false;
        return messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
        return messageId.hashCode();
    }
}
