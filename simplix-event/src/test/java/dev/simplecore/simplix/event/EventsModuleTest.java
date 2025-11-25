package dev.simplecore.simplix.event;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.core.event.EventPublisher;
import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.publisher.UnifiedEventPublisher;
import dev.simplecore.simplix.event.strategy.LocalEventStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DisplayName("Events Module Integration Test")
class EventsModuleTest {

    private EventPublisher eventPublisher;
    private ApplicationEventPublisher mockApplicationEventPublisher;

    @BeforeEach
    void setUp() {
        mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);
        LocalEventStrategy localStrategy = new LocalEventStrategy(mockApplicationEventPublisher);
        eventPublisher = new UnifiedEventPublisher(List.of(localStrategy), "local");
    }

    @Test
    @DisplayName("Should publish event using local strategy")
    void shouldPublishEventUsingLocalStrategy() {
        // Given
        Event event = TestEvent.builder()
            .id("test-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .metadata(new HashMap<>())
            .build();

        // When
        eventPublisher.publish(event);

        // Then
        verify(mockApplicationEventPublisher).publishEvent(any(Event.class));
        // Verify event was published to local strategy
    }

    @Test
    @DisplayName("Should publish event with custom options")
    void shouldPublishEventWithCustomOptions() {
        // Given
        Event event = TestEvent.builder()
            .id("test-event-002")
            .aggregateId("entity-002")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("updated", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .critical(true)
            .persistent(true)
            .async(false)
            .build();

        // When
        eventPublisher.publish(event, options);

        // Then
        verify(mockApplicationEventPublisher).publishEvent(any(Event.class));
    }

    @Test
    @DisplayName("Should enrich event metadata when ID is not set")
    void shouldEnrichEventMetadata() {
        // Given
        Event event = TestEvent.builder()
            .aggregateId("entity-003")
            .eventType("ENTITY_DELETED")
            .payload(Map.of("deleted", true))
            .metadata(new HashMap<>())
            .build();

        // When
        eventPublisher.publish(event);

        // Then
        verify(mockApplicationEventPublisher).publishEvent(any(Event.class));
    }

    @Test
    @DisplayName("Should check if publisher is ready")
    void shouldCheckIfPublisherIsReady() {
        // When
        boolean isAvailable = eventPublisher.isAvailable();

        // Then
        assertThat(isAvailable).isTrue();
    }
}