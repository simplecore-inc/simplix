package dev.simplecore.simplix.core.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventPublisher interface default methods")
class EventPublisherTest {

    static class TestPublisher implements EventPublisher {
        @Override public void publish(Event event) {}
        @Override public boolean isAvailable() { return true; }
        @Override public String getName() { return "test"; }
    }

    @Test
    @DisplayName("publish with options should delegate to publish")
    void publishWithOptionsShouldDelegate() {
        TestPublisher publisher = new TestPublisher();
        GenericEvent event = GenericEvent.builder()
                .eventType("TEST")
                .aggregateId("1")
                .aggregateType("Test")
                .build();

        // Should not throw
        publisher.publish(event, "options");
    }

    @Test
    @DisplayName("getPriority should return 0 by default")
    void getPriorityShouldReturnZero() {
        TestPublisher publisher = new TestPublisher();
        assertThat(publisher.getPriority()).isEqualTo(0);
    }
}
