package dev.simplecore.simplix.sync.core;

import java.io.IOException;

/**
 * Codec for encoding and decoding typed payloads to/from raw bytes.
 *
 * <p>Used by {@link SyncChannel} to provide type-safe broadcasting and subscription
 * over the raw byte-based {@link InstanceSyncBroadcaster}.
 *
 * @param <T> the message type
 */
public interface PayloadCodec<T> {

    /**
     * Encode a message to bytes.
     *
     * @param message the message to encode
     * @return the encoded byte array
     */
    byte[] encode(T message);

    /**
     * Decode bytes to a message.
     *
     * @param payload the byte array to decode
     * @return the decoded message
     * @throws IOException if decoding fails
     */
    T decode(byte[] payload) throws IOException;

    /**
     * Create a codec from encoder and decoder functions.
     *
     * @param encoder the encoding function
     * @param decoder the decoding function
     * @param <T>     the message type
     * @return a new PayloadCodec
     */
    static <T> PayloadCodec<T> of(Encoder<T> encoder, Decoder<T> decoder) {
        return new PayloadCodec<>() {
            @Override
            public byte[] encode(T message) {
                return encoder.encode(message);
            }

            @Override
            public T decode(byte[] payload) throws IOException {
                return decoder.decode(payload);
            }
        };
    }

    @FunctionalInterface
    interface Encoder<T> {
        byte[] encode(T message);
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(byte[] payload) throws IOException;
    }
}
