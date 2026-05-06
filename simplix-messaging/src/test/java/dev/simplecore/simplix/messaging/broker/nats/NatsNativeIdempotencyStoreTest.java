package dev.simplecore.simplix.messaging.broker.nats;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NatsNativeIdempotencyStoreTest {

    @Test
    void tryAcquire_alwaysReturnsTrue_passThrough() {
        NatsNativeIdempotencyStore s = new NatsNativeIdempotencyStore(Duration.ofMinutes(2));
        assertThat(s.tryAcquire("ch", "g", "m1")).isTrue();
        assertThat(s.tryAcquire("ch", "g", "m1")).isTrue();
    }

    @Test
    void ttl_returnsConfiguredDuplicateWindow() {
        assertThat(new NatsNativeIdempotencyStore(Duration.ofMinutes(5)).ttl())
                .isEqualTo(Duration.ofMinutes(5));
    }
}
