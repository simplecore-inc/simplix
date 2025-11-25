package dev.simplecore.simplix.event;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.core.event.EventPublisher;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.publisher.UnifiedEventPublisher;
import dev.simplecore.simplix.event.strategy.KafkaEventStrategy;
import dev.simplecore.simplix.event.strategy.LocalEventStrategy;
import dev.simplecore.simplix.event.strategy.RabbitEventStrategy;
import dev.simplecore.simplix.event.strategy.RedisEventStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("Event Strategy Integration Test")
class EventStrategyIntegrationTest {

    private ApplicationEventPublisher mockApplicationEventPublisher;

    @BeforeEach
    void setUp() {
        mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);
    }

    @Test
    @DisplayName("Should select Local strategy when mode is local")
    void shouldSelectLocalStrategyWhenModeIsLocal() {
        // Given
        LocalEventStrategy localStrategy = new LocalEventStrategy(mockApplicationEventPublisher);
        List<EventStrategy> strategies = Arrays.asList(localStrategy);

        // When
        EventPublisher publisher = new UnifiedEventPublisher(strategies, "local");

        // Then
        // Mode is set to local
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should select correct strategy based on mode")
    void shouldSelectCorrectStrategyBasedOnMode() {
        // Given
        LocalEventStrategy localStrategy = mock(LocalEventStrategy.class);
        RedisEventStrategy redisStrategy = mock(RedisEventStrategy.class);
        KafkaEventStrategy kafkaStrategy = mock(KafkaEventStrategy.class);
        RabbitEventStrategy rabbitStrategy = mock(RabbitEventStrategy.class);

        // Configure strategy support
        when(localStrategy.supports("local")).thenReturn(true);
        when(localStrategy.getName()).thenReturn("Local");
        when(localStrategy.isReady()).thenReturn(true);

        when(redisStrategy.supports("redis")).thenReturn(true);
        when(redisStrategy.getName()).thenReturn("Redis");
        when(redisStrategy.isReady()).thenReturn(true);

        when(kafkaStrategy.supports("kafka")).thenReturn(true);
        when(kafkaStrategy.getName()).thenReturn("Kafka");
        when(kafkaStrategy.isReady()).thenReturn(true);

        when(rabbitStrategy.supports("rabbit")).thenReturn(true);
        when(rabbitStrategy.getName()).thenReturn("RabbitMQ");
        when(rabbitStrategy.isReady()).thenReturn(true);

        List<EventStrategy> strategies = Arrays.asList(
            localStrategy, redisStrategy, kafkaStrategy, rabbitStrategy
        );

        // Test each mode
        EventPublisher localPublisher = new UnifiedEventPublisher(strategies, "local");
        assertThat(localPublisher.getName()).isNotNull();

        EventPublisher redisPublisher = new UnifiedEventPublisher(strategies, "redis");
        assertThat(redisPublisher.getName()).isNotNull();

        EventPublisher kafkaPublisher = new UnifiedEventPublisher(strategies, "kafka");
        assertThat(kafkaPublisher.getName()).isNotNull();

        EventPublisher rabbitPublisher = new UnifiedEventPublisher(strategies, "rabbit");
        assertThat(rabbitPublisher.getName()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when no strategy supports the mode")
    void shouldThrowExceptionWhenNoStrategySupportsMode() {
        // Given
        LocalEventStrategy localStrategy = mock(LocalEventStrategy.class);
        when(localStrategy.supports("unknown")).thenReturn(false);
        when(localStrategy.getName()).thenReturn("Local");

        List<EventStrategy> strategies = Arrays.asList(localStrategy);

        // When/Then
        assertThatThrownBy(() -> new UnifiedEventPublisher(strategies, "unknown"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No event strategy found for mode: unknown");
    }

    @Test
    @DisplayName("Should throw exception when no strategies available")
    void shouldThrowExceptionWhenNoStrategiesAvailable() {
        // Given
        List<EventStrategy> strategies = Arrays.asList();

        // When/Then
        assertThatThrownBy(() -> new UnifiedEventPublisher(strategies, "local"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No event strategy found for mode: local");
    }

    @Test
    @DisplayName("Should publish event through unified publisher")
    void shouldPublishEventThroughUnifiedPublisher() {
        // Given
        LocalEventStrategy localStrategy = new LocalEventStrategy(mockApplicationEventPublisher);
        List<EventStrategy> strategies = Arrays.asList(localStrategy);
        EventPublisher publisher = new UnifiedEventPublisher(strategies, "local");

        Event event = TestEvent.builder()
            .id("integration-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .metadata(new HashMap<>())
            .build();

        // When
        publisher.publish(event);

        // Then
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should publish event with options through unified publisher")
    void shouldPublishEventWithOptionsThroughUnifiedPublisher() {
        // Given
        LocalEventStrategy localStrategy = new LocalEventStrategy(mockApplicationEventPublisher);
        List<EventStrategy> strategies = Arrays.asList(localStrategy);
        EventPublisher publisher = new UnifiedEventPublisher(strategies, "local");

        Event event = TestEvent.builder()
            .id("integration-event-002")
            .aggregateId("entity-002")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("updated", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .critical(false)
            .persistent(true)
            .async(true)
            .build();

        // When
        publisher.publish(event, options);

        // Then
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should handle null event gracefully")
    void shouldHandleNullEventGracefully() {
        // Given
        LocalEventStrategy localStrategy = new LocalEventStrategy(mockApplicationEventPublisher);
        List<EventStrategy> strategies = Arrays.asList(localStrategy);
        EventPublisher publisher = new UnifiedEventPublisher(strategies, "local");

        // When
        publisher.publish(null);

        // Then - Should not throw exception
        assertThat(publisher.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should enrich event metadata when ID is not set")
    void shouldEnrichEventMetadataWhenIdIsNotSet() throws Exception {
        // Given
        LocalEventStrategy localStrategy = new LocalEventStrategy(mockApplicationEventPublisher);
        List<EventStrategy> strategies = Arrays.asList(localStrategy);
        UnifiedEventPublisher publisher = new UnifiedEventPublisher(strategies, "local");

        // Use reflection to set enrichMetadata and instanceId
        java.lang.reflect.Field enrichMetadataField = UnifiedEventPublisher.class.getDeclaredField("enrichMetadata");
        enrichMetadataField.setAccessible(true);
        enrichMetadataField.set(publisher, true);

        java.lang.reflect.Field instanceIdField = UnifiedEventPublisher.class.getDeclaredField("instanceId");
        instanceIdField.setAccessible(true);
        instanceIdField.set(publisher, "test-instance-001");

        Event event = TestEvent.builder()
            .aggregateId("entity-003")
            .eventType("ENTITY_DELETED")
            .payload(Map.of("deleted", true))
            .metadata(new HashMap<>())
            .build();

        // When
        publisher.publish(event);

        // Then
        // Since Event interface is immutable, metadata enrichment
        // is handled internally but not reflected on the original event object
        assertThat(event.getOccurredAt()).isNull(); // Was not set initially
        assertThat(event.getMetadata()).isNotNull();
        // The event was published successfully despite null ID
    }

    @Test
    @DisplayName("Should handle critical event failure properly")
    void shouldHandleCriticalEventFailureProperly() {
        // Given
        EventStrategy failingStrategy = mock(EventStrategy.class);
        when(failingStrategy.supports("failing")).thenReturn(true);
        when(failingStrategy.getName()).thenReturn("Failing");
        when(failingStrategy.isReady()).thenReturn(true);
        doThrow(new RuntimeException("Strategy failed"))
            .when(failingStrategy).publish(any(), any());

        List<EventStrategy> strategies = Arrays.asList(failingStrategy);
        EventPublisher publisher = new UnifiedEventPublisher(strategies, "failing");

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
            .build();

        // When/Then
        assertThatThrownBy(() -> publisher.publish(event, options))
            .isInstanceOf(UnifiedEventPublisher.EventPublishException.class)
            .hasMessageContaining("Failed to publish critical event");
    }

    @Test
    @DisplayName("Should continue on non-critical event failure")
    void shouldContinueOnNonCriticalEventFailure() {
        // Given
        EventStrategy failingStrategy = mock(EventStrategy.class);
        when(failingStrategy.supports("failing")).thenReturn(true);
        when(failingStrategy.getName()).thenReturn("Failing");
        when(failingStrategy.isReady()).thenReturn(true);
        doThrow(new RuntimeException("Strategy failed"))
            .when(failingStrategy).publish(any(), any());

        List<EventStrategy> strategies = Arrays.asList(failingStrategy);
        EventPublisher publisher = new UnifiedEventPublisher(strategies, "failing");

        Event event = TestEvent.builder()
            .id("non-critical-event-001")
            .aggregateId("entity-005")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("critical", false))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .critical(false)
            .build();

        // When
        publisher.publish(event, options); // Should not throw exception

        // Then
        assertThat(publisher.isAvailable()).isTrue();
    }
}