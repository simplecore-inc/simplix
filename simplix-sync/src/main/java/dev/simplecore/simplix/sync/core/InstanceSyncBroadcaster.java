package dev.simplecore.simplix.sync.core;

/**
 * Broadcasts raw byte payloads across application instances for state synchronization.
 *
 * <p>Provides a channel-based pub/sub abstraction for cross-instance communication.
 * Unlike structured messaging (simplix-messaging), this interface is designed for
 * fire-and-forget, best-effort delivery of raw byte payloads.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link NoOpInstanceSyncBroadcaster} — Local mode (single instance), all operations are no-ops.</li>
 *   <li>{@code RedisInstanceSyncBroadcaster} — Distributed mode via Redis Pub/Sub with UUID self-filtering.</li>
 * </ul>
 */
public interface InstanceSyncBroadcaster {

    /**
     * Broadcast a payload to all peer instances on the specified channel.
     *
     * <p>In local mode, this is a no-op. In distributed mode, the payload is published
     * to a Redis Pub/Sub channel with a self-filtering prefix so the sending instance
     * does not receive its own message.
     *
     * @param channel the channel name
     * @param payload the raw byte payload to broadcast
     */
    void broadcast(String channel, byte[] payload);

    /**
     * Subscribe to a channel to receive payloads from peer instances.
     *
     * <p>In local mode, this is a no-op. In distributed mode, the listener is registered
     * on a Redis Pub/Sub channel with self-filtering applied.
     *
     * @param channel  the channel name
     * @param listener the listener that receives inbound payloads
     */
    void subscribe(String channel, InboundPayloadListener listener);

    /**
     * Functional interface for receiving inbound payloads from peer instances.
     */
    @FunctionalInterface
    interface InboundPayloadListener {

        /**
         * Called when a payload is received from a peer instance.
         *
         * @param payload the raw byte payload (self-messages already filtered out)
         */
        void onPayload(byte[] payload);
    }
}
