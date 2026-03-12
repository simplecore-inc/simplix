package dev.simplecore.simplix.sync.infrastructure.redis;

import dev.simplecore.simplix.sync.core.InstanceSyncBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Redis Pub/Sub implementation of {@link InstanceSyncBroadcaster}.
 *
 * <p>Uses a 16-byte UUID prefix on each published message to identify the
 * source instance. Subscribers automatically filter out messages from the
 * same instance, preventing re-application of locally originated state changes.
 *
 * <p>Wire format: {@code [16 bytes instanceId UUID] [payload bytes]}
 */
public class RedisInstanceSyncBroadcaster implements InstanceSyncBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisInstanceSyncBroadcaster.class);
    private static final int UUID_BYTE_LENGTH = 16;

    private final byte[] instanceIdBytes;
    private final RedisTemplate<String, byte[]> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    public RedisInstanceSyncBroadcaster(RedisTemplate<String, byte[]> redisTemplate,
                                         RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;

        UUID instanceId = UUID.randomUUID();
        this.instanceIdBytes = uuidToBytes(instanceId);
        log.info("Redis instance sync broadcaster initialized [instanceId={}]", instanceId);
    }

    @Override
    public void broadcast(String channel, byte[] payload) {
        byte[] prefixed = new byte[UUID_BYTE_LENGTH + payload.length];
        System.arraycopy(instanceIdBytes, 0, prefixed, 0, UUID_BYTE_LENGTH);
        System.arraycopy(payload, 0, prefixed, UUID_BYTE_LENGTH, payload.length);

        redisTemplate.convertAndSend(channel, prefixed);
    }

    @Override
    public void subscribe(String channel, InboundPayloadListener listener) {
        listenerContainer.addMessageListener(
                new SelfFilteringListener(listener),
                new ChannelTopic(channel));
        log.info("Subscribed to sync channel: {}", channel);
    }

    private class SelfFilteringListener implements MessageListener {

        private final InboundPayloadListener delegate;

        SelfFilteringListener(InboundPayloadListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            byte[] body = message.getBody();
            if (body.length < UUID_BYTE_LENGTH) {
                log.warn("Received malformed sync message (too short: {} bytes)", body.length);
                return;
            }

            byte[] senderId = Arrays.copyOf(body, UUID_BYTE_LENGTH);
            if (Arrays.equals(senderId, instanceIdBytes)) {
                return; // Skip self-messages
            }

            byte[] payload = Arrays.copyOfRange(body, UUID_BYTE_LENGTH, body.length);
            delegate.onPayload(payload);
        }
    }

    static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(UUID_BYTE_LENGTH);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
