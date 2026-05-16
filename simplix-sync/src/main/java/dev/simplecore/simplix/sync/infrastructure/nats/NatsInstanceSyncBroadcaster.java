package dev.simplecore.simplix.sync.infrastructure.nats;

import dev.simplecore.simplix.sync.core.InstanceSyncBroadcaster;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * NATS core pub/sub implementation of {@link InstanceSyncBroadcaster}.
 *
 * <p>Uses the same wire format as the Redis implementation: a 16-byte UUID
 * prefix identifies the source instance and subscribers filter out messages
 * originating from themselves.
 *
 * <p>Wire format: {@code [16 bytes instanceId UUID] [payload bytes]}
 *
 * <p>The broadcaster does not create its own NATS connection — it consumes
 * an {@link Connection} bean provided elsewhere (typically by
 * simplix-messaging when {@code simplix.messaging.broker=nats}). Subscriptions
 * are registered on a single shared {@link Dispatcher} created from that
 * connection; the dispatcher's lifecycle is bound to the connection's
 * lifecycle.
 *
 * <p>Channel names are passed to NATS verbatim as subjects. Callers must use
 * names that are valid NATS subjects (no spaces; {@code .} and {@code -} are
 * accepted; wildcards {@code *} and {@code >} should not appear in literal
 * channel names).
 */
public class NatsInstanceSyncBroadcaster implements InstanceSyncBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NatsInstanceSyncBroadcaster.class);
    private static final int UUID_BYTE_LENGTH = 16;

    private final byte[] instanceIdBytes;
    private final Connection connection;
    private final Dispatcher dispatcher;

    public NatsInstanceSyncBroadcaster(Connection connection) {
        this.connection = connection;

        UUID instanceId = UUID.randomUUID();
        this.instanceIdBytes = uuidToBytes(instanceId);
        this.dispatcher = connection.createDispatcher();

        log.info("NATS instance sync broadcaster initialized [instanceId={}]", instanceId);
    }

    @Override
    public void broadcast(String channel, byte[] payload) {
        byte[] prefixed = new byte[UUID_BYTE_LENGTH + payload.length];
        System.arraycopy(instanceIdBytes, 0, prefixed, 0, UUID_BYTE_LENGTH);
        System.arraycopy(payload, 0, prefixed, UUID_BYTE_LENGTH, payload.length);

        connection.publish(channel, prefixed);
    }

    @Override
    public void subscribe(String channel, InboundPayloadListener listener) {
        dispatcher.subscribe(channel, message -> {
            byte[] body = message.getData();
            if (body == null || body.length < UUID_BYTE_LENGTH) {
                log.warn("Received malformed sync message on channel='{}' (too short: {} bytes)",
                        channel, body == null ? 0 : body.length);
                return;
            }

            byte[] senderId = Arrays.copyOf(body, UUID_BYTE_LENGTH);
            if (Arrays.equals(senderId, instanceIdBytes)) {
                return;
            }

            byte[] payload = Arrays.copyOfRange(body, UUID_BYTE_LENGTH, body.length);
            listener.onPayload(payload);
        });
        log.info("Subscribed to NATS sync channel: {}", channel);
    }

    @PreDestroy
    public void shutdown() {
        if (dispatcher != null) {
            try {
                connection.closeDispatcher(dispatcher);
            } catch (Exception e) {
                log.debug("Error closing NATS sync dispatcher (already closed?): {}", e.getMessage());
            }
        }
    }

    static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(UUID_BYTE_LENGTH);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
