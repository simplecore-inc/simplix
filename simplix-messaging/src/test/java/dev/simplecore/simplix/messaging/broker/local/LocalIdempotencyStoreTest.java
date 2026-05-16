package dev.simplecore.simplix.messaging.broker.local;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalIdempotencyStoreTest {

    @Test
    void firstAcquireReturnsTrue_secondReturnsFalse() {
        LocalIdempotencyStore store = new LocalIdempotencyStore(Duration.ofMinutes(1), 10_000);
        assertThat(store.tryAcquire("ch", "g", "m1")).isTrue();
        assertThat(store.tryAcquire("ch", "g", "m1")).isFalse();
    }

    @Test
    void differentGroupsAreIndependent() {
        LocalIdempotencyStore store = new LocalIdempotencyStore(Duration.ofMinutes(1), 10_000);
        assertThat(store.tryAcquire("ch", "g1", "m1")).isTrue();
        assertThat(store.tryAcquire("ch", "g2", "m1")).isTrue();
    }

    @Test
    void ttl_isReturnedFromConstructor() {
        LocalIdempotencyStore store = new LocalIdempotencyStore(Duration.ofSeconds(30), 100);
        assertThat(store.ttl()).isEqualTo(Duration.ofSeconds(30));
    }
}
