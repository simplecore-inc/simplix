package dev.simplecore.simplix.sync.infrastructure.redis;

import dev.simplecore.simplix.sync.core.InstanceSyncBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisInstanceSyncBroadcaster")
class RedisInstanceSyncBroadcasterTest {

    @Mock
    private RedisTemplate<String, byte[]> redisTemplate;

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    private RedisInstanceSyncBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new RedisInstanceSyncBroadcaster(redisTemplate, listenerContainer);
    }

    @Nested
    @DisplayName("UUID conversion")
    class UuidConversionTests {

        @Test
        @DisplayName("should convert UUID to 16 bytes and back")
        void shouldConvertUuidToBytes() {
            UUID original = UUID.randomUUID();
            byte[] bytes = RedisInstanceSyncBroadcaster.uuidToBytes(original);
            assertThat(bytes).hasSize(16);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            UUID reconstructed = new UUID(buffer.getLong(), buffer.getLong());
            assertThat(reconstructed).isEqualTo(original);
        }

        @Test
        @DisplayName("should produce different bytes for different UUIDs")
        void shouldProduceDifferentBytes() {
            byte[] bytes1 = RedisInstanceSyncBroadcaster.uuidToBytes(UUID.randomUUID());
            byte[] bytes2 = RedisInstanceSyncBroadcaster.uuidToBytes(UUID.randomUUID());
            assertThat(bytes1).isNotEqualTo(bytes2);
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should initialize broadcaster with random instance ID")
        void shouldInitializeWithRandomInstanceId() {
            assertThat(broadcaster).isNotNull();
        }
    }

    @Nested
    @DisplayName("broadcast")
    class BroadcastTests {

        @Test
        @DisplayName("should prefix payload with 16-byte instance ID and send via RedisTemplate")
        void shouldPrefixPayloadAndSend() {
            byte[] payload = new byte[]{1, 2, 3, 4};
            broadcaster.broadcast("sync-channel", payload);

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(redisTemplate).convertAndSend(eq("sync-channel"), captor.capture());

            byte[] prefixed = captor.getValue();
            assertThat(prefixed).hasSize(16 + 4);
            // The last 4 bytes should be the original payload
            byte[] extractedPayload = new byte[4];
            System.arraycopy(prefixed, 16, extractedPayload, 0, 4);
            assertThat(extractedPayload).containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("should handle empty payload")
        void shouldHandleEmptyPayload() {
            byte[] payload = new byte[0];
            broadcaster.broadcast("ch", payload);

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(redisTemplate).convertAndSend(eq("ch"), captor.capture());

            byte[] prefixed = captor.getValue();
            assertThat(prefixed).hasSize(16);
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should register a message listener on the specified channel topic")
        void shouldRegisterListener() {
            InstanceSyncBroadcaster.InboundPayloadListener listener = payload -> {};
            broadcaster.subscribe("my-channel", listener);

            ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
            ArgumentCaptor<ChannelTopic> topicCaptor = ArgumentCaptor.forClass(ChannelTopic.class);
            verify(listenerContainer).addMessageListener(listenerCaptor.capture(), topicCaptor.capture());

            assertThat(topicCaptor.getValue().getTopic()).isEqualTo("my-channel");
            assertThat(listenerCaptor.getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("SelfFilteringListener")
    class SelfFilteringListenerTests {

        @Test
        @DisplayName("should filter out self-messages based on instance ID")
        void shouldFilterSelfMessages() {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageListener registeredListener = captureRegisteredListener();

            // Build a message with the broadcaster's own instance ID prefix
            byte[] selfPrefixedBody = buildPrefixedBody(getInstanceIdBytes(), new byte[]{10, 20});
            Message selfMessage = new DefaultMessage("ch".getBytes(), selfPrefixedBody);

            registeredListener.onMessage(selfMessage, null);

            // Self-message should be filtered
            assertThat(received.get()).isNull();
        }

        @Test
        @DisplayName("should deliver messages from other instances")
        void shouldDeliverPeerMessages() {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageListener registeredListener = captureRegisteredListener();

            // Build a message with a different instance ID
            UUID otherId = UUID.randomUUID();
            byte[] otherIdBytes = RedisInstanceSyncBroadcaster.uuidToBytes(otherId);
            byte[] payload = new byte[]{5, 6, 7};
            byte[] peerPrefixedBody = buildPrefixedBody(otherIdBytes, payload);
            Message peerMessage = new DefaultMessage("ch".getBytes(), peerPrefixedBody);

            registeredListener.onMessage(peerMessage, null);

            assertThat(received.get()).isNotNull();
            assertThat(received.get()).containsExactly(5, 6, 7);
        }

        @Test
        @DisplayName("should discard malformed messages shorter than 16 bytes")
        void shouldDiscardMalformedMessages() {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageListener registeredListener = captureRegisteredListener();

            // Message body shorter than UUID_BYTE_LENGTH (16)
            byte[] shortBody = new byte[]{1, 2, 3};
            Message malformedMessage = new DefaultMessage("ch".getBytes(), shortBody);

            registeredListener.onMessage(malformedMessage, null);

            // Should not deliver the malformed message
            assertThat(received.get()).isNull();
        }

        @Test
        @DisplayName("should deliver payload with zero length from a peer instance")
        void shouldDeliverEmptyPayloadFromPeer() {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageListener registeredListener = captureRegisteredListener();

            UUID otherId = UUID.randomUUID();
            byte[] otherIdBytes = RedisInstanceSyncBroadcaster.uuidToBytes(otherId);
            // 16 bytes instance ID only, no payload
            byte[] body = buildPrefixedBody(otherIdBytes, new byte[0]);
            Message message = new DefaultMessage("ch".getBytes(), body);

            registeredListener.onMessage(message, null);

            assertThat(received.get()).isNotNull();
            assertThat(received.get()).isEmpty();
        }

        @Test
        @DisplayName("should handle message with exactly 16 bytes (UUID only, no payload)")
        void shouldHandleExactly16ByteMessage() {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageListener registeredListener = captureRegisteredListener();

            UUID otherId = UUID.randomUUID();
            byte[] otherIdBytes = RedisInstanceSyncBroadcaster.uuidToBytes(otherId);
            // Exactly 16 bytes
            Message message = new DefaultMessage("ch".getBytes(), otherIdBytes);

            registeredListener.onMessage(message, null);

            assertThat(received.get()).isNotNull();
            assertThat(received.get()).isEmpty();
        }

        private MessageListener captureRegisteredListener() {
            ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(listenerContainer).addMessageListener(captor.capture(), any(ChannelTopic.class));
            return captor.getValue();
        }

        /**
         * Extract instance ID bytes from the broadcaster via broadcast + capture.
         */
        private byte[] getInstanceIdBytes() {
            broadcaster.broadcast("__probe__", new byte[0]);
            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(redisTemplate).convertAndSend(eq("__probe__"), captor.capture());
            byte[] prefixed = captor.getValue();
            byte[] instanceId = new byte[16];
            System.arraycopy(prefixed, 0, instanceId, 0, 16);
            return instanceId;
        }

        private byte[] buildPrefixedBody(byte[] instanceId, byte[] payload) {
            byte[] body = new byte[instanceId.length + payload.length];
            System.arraycopy(instanceId, 0, body, 0, instanceId.length);
            System.arraycopy(payload, 0, body, instanceId.length, payload.length);
            return body;
        }
    }
}
