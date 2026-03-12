package dev.simplecore.simplix.sync.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Type-safe wrapper over {@link InstanceSyncBroadcaster} raw byte channels.
 *
 * <p>Encapsulates serialization/deserialization and error handling,
 * exposing a typed API for broadcast and subscription.
 *
 * @param <T> the message type
 */
public class SyncChannel<T> {

    private static final Logger log = LoggerFactory.getLogger(SyncChannel.class);

    private final String channelName;
    private final PayloadCodec<T> codec;
    private final InstanceSyncBroadcaster broadcaster;

    /**
     * Create a new typed sync channel.
     *
     * @param channelName  the channel name for broadcasting
     * @param codec        the codec for encoding/decoding messages
     * @param broadcaster  the underlying broadcaster
     */
    public SyncChannel(String channelName,
                        PayloadCodec<T> codec,
                        InstanceSyncBroadcaster broadcaster) {
        this.channelName = channelName;
        this.codec = codec;
        this.broadcaster = broadcaster;
    }

    /**
     * Encode and broadcast a message to peer instances.
     *
     * @param message the message to broadcast
     */
    public void broadcast(T message) {
        try {
            byte[] payload = codec.encode(message);
            broadcaster.broadcast(channelName, payload);
        } catch (Exception e) {
            log.error("Failed to broadcast message on channel={}: {}", channelName, e.getMessage());
        }
    }

    /**
     * Subscribe with a typed handler. Decode errors are logged and skipped.
     *
     * @param handler the typed message handler
     */
    public void subscribe(Consumer<T> handler) {
        broadcaster.subscribe(channelName, payload -> {
            try {
                T message = codec.decode(payload);
                handler.accept(message);
            } catch (IOException e) {
                log.error("Failed to decode message on channel={} [payloadSize={}]: {}",
                        channelName, payload.length, e.getMessage());
            }
        });
    }

    /**
     * Get the channel name.
     *
     * @return the channel name
     */
    public String getChannelName() {
        return channelName;
    }
}
