package dev.simplecore.simplix.sync.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisInstanceSyncBroadcasterTest {

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
