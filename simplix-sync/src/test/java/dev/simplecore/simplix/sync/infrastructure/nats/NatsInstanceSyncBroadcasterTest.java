package dev.simplecore.simplix.sync.infrastructure.nats;

import dev.simplecore.simplix.sync.core.InstanceSyncBroadcaster;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NatsInstanceSyncBroadcaster")
class NatsInstanceSyncBroadcasterTest {

    @Mock
    private Connection connection;

    @Mock
    private Dispatcher dispatcher;

    private NatsInstanceSyncBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        when(connection.createDispatcher()).thenReturn(dispatcher);
        broadcaster = new NatsInstanceSyncBroadcaster(connection);
    }

    @Nested
    @DisplayName("UUID conversion")
    class UuidConversionTests {

        @Test
        @DisplayName("should convert UUID to 16 bytes and back")
        void shouldConvertUuidToBytes() {
            UUID original = UUID.randomUUID();
            byte[] bytes = NatsInstanceSyncBroadcaster.uuidToBytes(original);
            assertThat(bytes).hasSize(16);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            UUID reconstructed = new UUID(buffer.getLong(), buffer.getLong());
            assertThat(reconstructed).isEqualTo(original);
        }

        @Test
        @DisplayName("should produce different bytes for different UUIDs")
        void shouldProduceDifferentBytes() {
            byte[] bytes1 = NatsInstanceSyncBroadcaster.uuidToBytes(UUID.randomUUID());
            byte[] bytes2 = NatsInstanceSyncBroadcaster.uuidToBytes(UUID.randomUUID());
            assertThat(bytes1).isNotEqualTo(bytes2);
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should create a dispatcher from the supplied connection")
        void shouldCreateDispatcher() {
            verify(connection).createDispatcher();
            assertThat(broadcaster).isNotNull();
        }
    }

    @Nested
    @DisplayName("broadcast")
    class BroadcastTests {

        @Test
        @DisplayName("should prefix payload with 16-byte instance ID and publish via Connection")
        void shouldPrefixPayloadAndPublish() {
            byte[] payload = new byte[]{1, 2, 3, 4};
            broadcaster.broadcast("sync-channel", payload);

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(connection).publish(eq("sync-channel"), captor.capture());

            byte[] prefixed = captor.getValue();
            assertThat(prefixed).hasSize(16 + 4);
            byte[] extractedPayload = new byte[4];
            System.arraycopy(prefixed, 16, extractedPayload, 0, 4);
            assertThat(extractedPayload).containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("should handle empty payload")
        void shouldHandleEmptyPayload() {
            broadcaster.broadcast("ch", new byte[0]);

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(connection).publish(eq("ch"), captor.capture());

            assertThat(captor.getValue()).hasSize(16);
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should register a message handler on the dispatcher for the given channel")
        void shouldRegisterHandlerOnDispatcher() {
            InstanceSyncBroadcaster.InboundPayloadListener listener = payload -> {};
            broadcaster.subscribe("my-channel", listener);

            verify(dispatcher).subscribe(eq("my-channel"), any(MessageHandler.class));
        }
    }

    @Nested
    @DisplayName("self-filtering message handler")
    class SelfFilteringTests {

        @Test
        @DisplayName("should filter out self-messages based on instance ID")
        void shouldFilterSelfMessages() throws Exception {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageHandler registeredHandler = captureRegisteredHandler();

            byte[] selfPrefixedBody = buildPrefixedBody(getInstanceIdBytes(), new byte[]{10, 20});
            registeredHandler.onMessage(buildMessage(selfPrefixedBody));

            assertThat(received.get()).isNull();
        }

        @Test
        @DisplayName("should deliver messages from other instances")
        void shouldDeliverPeerMessages() throws Exception {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageHandler registeredHandler = captureRegisteredHandler();

            UUID otherId = UUID.randomUUID();
            byte[] otherIdBytes = NatsInstanceSyncBroadcaster.uuidToBytes(otherId);
            byte[] payload = new byte[]{5, 6, 7};
            byte[] peerPrefixedBody = buildPrefixedBody(otherIdBytes, payload);

            registeredHandler.onMessage(buildMessage(peerPrefixedBody));

            assertThat(received.get()).isNotNull();
            assertThat(received.get()).containsExactly(5, 6, 7);
        }

        @Test
        @DisplayName("should discard malformed messages shorter than 16 bytes")
        void shouldDiscardMalformedMessages() throws Exception {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageHandler registeredHandler = captureRegisteredHandler();

            registeredHandler.onMessage(buildMessage(new byte[]{1, 2, 3}));

            assertThat(received.get()).isNull();
        }

        @Test
        @DisplayName("should discard messages with null body")
        void shouldDiscardNullBodyMessages() throws Exception {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageHandler registeredHandler = captureRegisteredHandler();

            registeredHandler.onMessage(buildMessage(null));

            assertThat(received.get()).isNull();
        }

        @Test
        @DisplayName("should deliver empty payload from a peer instance (16-byte body)")
        void shouldDeliverEmptyPayloadFromPeer() throws Exception {
            AtomicReference<byte[]> received = new AtomicReference<>();
            broadcaster.subscribe("ch", received::set);

            MessageHandler registeredHandler = captureRegisteredHandler();

            UUID otherId = UUID.randomUUID();
            byte[] otherIdBytes = NatsInstanceSyncBroadcaster.uuidToBytes(otherId);
            registeredHandler.onMessage(buildMessage(otherIdBytes));

            assertThat(received.get()).isNotNull();
            assertThat(received.get()).isEmpty();
        }

        private MessageHandler captureRegisteredHandler() {
            ArgumentCaptor<MessageHandler> captor = ArgumentCaptor.forClass(MessageHandler.class);
            verify(dispatcher).subscribe(eq("ch"), captor.capture());
            return captor.getValue();
        }

        private byte[] getInstanceIdBytes() {
            broadcaster.broadcast("__probe__", new byte[0]);
            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(connection).publish(eq("__probe__"), captor.capture());
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

        private Message buildMessage(byte[] body) {
            Message message = mock(Message.class);
            when(message.getData()).thenReturn(body);
            return message;
        }
    }
}
