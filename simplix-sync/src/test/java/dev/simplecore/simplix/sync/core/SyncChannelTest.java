package dev.simplecore.simplix.sync.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SyncChannelTest {

    private TestBroadcaster broadcaster;
    private SyncChannel<String> channel;

    @BeforeEach
    void setUp() {
        broadcaster = new TestBroadcaster();
        PayloadCodec<String> codec = PayloadCodec.of(
                msg -> msg.getBytes(StandardCharsets.UTF_8),
                bytes -> new String(bytes, StandardCharsets.UTF_8)
        );
        channel = new SyncChannel<>("test-channel", codec, broadcaster);
    }

    @Test
    @DisplayName("should broadcast encoded message")
    void shouldBroadcastEncoded() {
        channel.broadcast("hello");
        assertThat(broadcaster.publishedPayloads).hasSize(1);
        assertThat(new String(broadcaster.publishedPayloads.get(0), StandardCharsets.UTF_8))
                .isEqualTo("hello");
        assertThat(broadcaster.publishedChannels.get(0)).isEqualTo("test-channel");
    }

    @Test
    @DisplayName("should subscribe and decode messages")
    void shouldSubscribeAndDecode() {
        AtomicReference<String> received = new AtomicReference<>();
        channel.subscribe(received::set);

        // Simulate incoming message
        broadcaster.simulateIncoming("world".getBytes(StandardCharsets.UTF_8));

        assertThat(received.get()).isEqualTo("world");
    }

    @Test
    @DisplayName("should handle decode errors gracefully")
    void shouldHandleDecodeErrors() {
        PayloadCodec<String> failingCodec = PayloadCodec.of(
                msg -> msg.getBytes(StandardCharsets.UTF_8),
                bytes -> { throw new java.io.IOException("bad data"); }
        );
        SyncChannel<String> failingChannel = new SyncChannel<>("fail-ch", failingCodec, broadcaster);

        AtomicReference<String> received = new AtomicReference<>();
        failingChannel.subscribe(received::set);

        // Should not throw, just log
        broadcaster.simulateIncoming(new byte[]{0x01});
        assertThat(received.get()).isNull();
    }

    @Test
    @DisplayName("should handle broadcast encoding error gracefully")
    void shouldHandleBroadcastEncodingError() {
        PayloadCodec<String> failingCodec = PayloadCodec.of(
                msg -> { throw new RuntimeException("encode failed"); },
                bytes -> new String(bytes, StandardCharsets.UTF_8)
        );
        SyncChannel<String> failingChannel = new SyncChannel<>("fail-ch", failingCodec, broadcaster);

        // Should not throw, just log the error
        failingChannel.broadcast("test-message");
        // Verify no payload was actually broadcast
        assertThat(broadcaster.publishedPayloads).isEmpty();
    }

    @Test
    @DisplayName("should return channel name")
    void shouldReturnChannelName() {
        assertThat(channel.getChannelName()).isEqualTo("test-channel");
    }

    /**
     * Test broadcaster that captures broadcasts and allows simulating incoming messages.
     */
    static class TestBroadcaster implements InstanceSyncBroadcaster {
        final List<byte[]> publishedPayloads = new ArrayList<>();
        final List<String> publishedChannels = new ArrayList<>();
        private InstanceSyncBroadcaster.InboundPayloadListener listener;

        @Override
        public void broadcast(String channel, byte[] payload) {
            publishedChannels.add(channel);
            publishedPayloads.add(payload);
        }

        @Override
        public void subscribe(String channel, InboundPayloadListener listener) {
            this.listener = listener;
        }

        void simulateIncoming(byte[] payload) {
            if (listener != null) {
                listener.onPayload(payload);
            }
        }
    }
}
