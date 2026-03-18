package dev.simplecore.simplix.core.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("EventManager - Extended Coverage")
class EventManagerExtendedTest {

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("should handle null event gracefully")
        void shouldHandleNullEvent() {
            assertThatNoException().isThrownBy(() ->
                    EventManager.getInstance().publish(null));
        }

        @Test
        @DisplayName("should publish event without throwing")
        void shouldPublishEvent() {
            GenericEvent event = GenericEvent.builder()
                    .eventType("TEST_EVENT")
                    .aggregateId("1")
                    .aggregateType("Test")
                    .build();

            assertThatNoException().isThrownBy(() ->
                    EventManager.getInstance().publish(event));
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("should return true with NoOp publisher")
        void shouldReturnTrue() {
            assertThat(EventManager.getInstance().isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("getPublisherName")
    class GetPublisherName {

        @Test
        @DisplayName("should return publisher name")
        void shouldReturnName() {
            String name = EventManager.getInstance().getPublisherName();
            assertThat(name).isNotNull().isNotBlank();
        }
    }
}
