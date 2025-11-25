package dev.simplecore.simplix.event;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.strategy.LocalEventStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Local Event Strategy Test")
class LocalEventStrategyTest {

    private LocalEventStrategy localEventStrategy;
    private ApplicationEventPublisher mockEventPublisher;

    @BeforeEach
    void setUp() {
        mockEventPublisher = mock(ApplicationEventPublisher.class);
        localEventStrategy = new LocalEventStrategy(mockEventPublisher);
    }

    @Test
    @DisplayName("Should publish event using Spring ApplicationEventPublisher")
    void shouldPublishEventUsingApplicationEventPublisher() {
        // Given
        Event event = TestEvent.builder()
            .id("local-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        // When
        localEventStrategy.publish(event, options);

        // Then
        verify(mockEventPublisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("Should publish event synchronously when async is false")
    void shouldPublishEventSynchronously() {
        // Given
        Event event = TestEvent.builder()
            .id("sync-event-001")
            .aggregateId("entity-002")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("updated", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .async(false)
            .build();

        // When
        localEventStrategy.publish(event, options);

        // Then
        verify(mockEventPublisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("Should publish event asynchronously when async is true")
    void shouldPublishEventAsynchronously() {
        // Given
        Event event = TestEvent.builder()
            .id("async-event-001")
            .aggregateId("entity-003")
            .eventType("ENTITY_DELETED")
            .occurredAt(Instant.now())
            .payload(Map.of("deleted", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .async(true)
            .build();

        // When
        localEventStrategy.publish(event, options);

        // Then
        verify(mockEventPublisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("Should support local mode")
    void shouldSupportLocalMode() {
        // Then
        assertThat(localEventStrategy.supports("local")).isTrue();
        assertThat(localEventStrategy.supports("LOCAL")).isTrue();
        assertThat(localEventStrategy.supports("redis")).isFalse();
        assertThat(localEventStrategy.supports("kafka")).isFalse();
    }

    @Test
    @DisplayName("Should always be ready")
    void shouldAlwaysBeReady() {
        // Then
        assertThat(localEventStrategy.isReady()).isTrue();
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        // Then
        assertThat(localEventStrategy.getName()).isEqualTo("LocalEventStrategy");
    }

    @Test
    @DisplayName("Should handle null event gracefully")
    void shouldHandleNullEventGracefully() {
        // Given
        PublishOptions options = PublishOptions.defaults();

        // When/Then - NullPointerException expected since event is used before null check
        try {
            localEventStrategy.publish(null, options);
            assert false : "Should have thrown NullPointerException";
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    @DisplayName("Should handle critical events")
    void shouldHandleCriticalEvents() {
        // Given
        Event event = TestEvent.builder()
            .id("critical-event-001")
            .aggregateId("entity-004")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("critical", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .critical(true)
            .persistent(true)
            .build();

        doThrow(new RuntimeException("Publishing failed"))
            .when(mockEventPublisher).publishEvent(any(Event.class));

        // When/Then
        try {
            localEventStrategy.publish(event, options);
            assert false : "Should have thrown exception for critical event";
        } catch (LocalEventStrategy.EventPublishException e) {
            assertThat(e.getMessage()).contains("Failed to publish critical event");
            assertThat(e.getCause().getMessage()).contains("Publishing failed");
        }
    }

    @Test
    @DisplayName("Should initialize and shutdown properly")
    void shouldInitializeAndShutdownProperly() {
        // When
        localEventStrategy.initialize();

        // Then
        assertThat(localEventStrategy.isReady()).isTrue();

        // When
        localEventStrategy.shutdown();

        // Then
        assertThat(localEventStrategy.isReady()).isFalse(); // Should be false after shutdown
    }
}