package dev.simplecore.simplix.core.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("EventManager")
class EventManagerTest {

    @Nested
    @DisplayName("getInstance")
    class GetInstance {

        @Test
        @DisplayName("should return singleton instance")
        void shouldReturnSingletonInstance() {
            EventManager instance1 = EventManager.getInstance();
            EventManager instance2 = EventManager.getInstance();

            assertThat(instance1).isSameAs(instance2);
        }

        @Test
        @DisplayName("should return non-null instance")
        void shouldReturnNonNullInstance() {
            assertThat(EventManager.getInstance()).isNotNull();
        }
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("should handle null event gracefully")
        void shouldHandleNullEvent() {
            assertThatNoException().isThrownBy(() ->
                EventManager.getInstance().publish(null)
            );
        }

        @Test
        @DisplayName("should publish event without throwing")
        void shouldPublishEventWithoutThrowing() {
            GenericEvent event = GenericEvent.builder()
                .eventType("TEST_EVENT")
                .aggregateId("agg-1")
                .build();

            assertThatNoException().isThrownBy(() ->
                EventManager.getInstance().publish(event)
            );
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("should return true even with NoOp publisher")
        void shouldReturnTrueWithNoOp() {
            assertThat(EventManager.getInstance().isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("getPublisherName")
    class GetPublisherName {

        @Test
        @DisplayName("should return publisher name")
        void shouldReturnPublisherName() {
            String name = EventManager.getInstance().getPublisherName();

            assertThat(name).isNotNull().isNotBlank();
        }
    }
}
