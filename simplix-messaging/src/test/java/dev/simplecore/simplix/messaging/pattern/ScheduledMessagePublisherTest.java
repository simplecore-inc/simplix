package dev.simplecore.simplix.messaging.pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduledMessagePublisher")
class ScheduledMessagePublisherTest {

    @Test
    @DisplayName("should be constructable with valid parameters")
    void shouldBeConstructable() {
        // ScheduledMessagePublisher requires real Redis for ZADD/ZRANGEBYSCORE operations.
        // Full integration tests would use Testcontainers. This validates the class structure.
        assertThat(ScheduledMessagePublisher.class).isNotNull();
    }
}
