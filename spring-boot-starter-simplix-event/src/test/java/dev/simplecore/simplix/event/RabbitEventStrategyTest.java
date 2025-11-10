package dev.simplecore.simplix.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;

import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.strategy.RabbitEventStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RabbitMQ Event Strategy Test")
class RabbitEventStrategyTest {

    private RabbitEventStrategy rabbitEventStrategy;
    private RabbitTemplate mockRabbitTemplate;
    private ObjectMapper objectMapper;
    private ConnectionFactory mockConnectionFactory;
    private Connection mockConnection;

    @BeforeEach
    void setUp() throws Exception {
        mockRabbitTemplate = mock(RabbitTemplate.class);
        mockConnectionFactory = mock(ConnectionFactory.class);
        mockConnection = mock(Connection.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        when(mockRabbitTemplate.getConnectionFactory()).thenReturn(mockConnectionFactory);
        when(mockConnectionFactory.createConnection()).thenReturn(mockConnection);
        when(mockConnection.isOpen()).thenReturn(true);
        when(mockRabbitTemplate.getMessageConverter()).thenReturn(new Jackson2JsonMessageConverter(objectMapper));

        rabbitEventStrategy = new RabbitEventStrategy(mockRabbitTemplate, objectMapper);

        // Use reflection to set the exchange name since it's injected by @Value
        java.lang.reflect.Field exchangeField = RabbitEventStrategy.class.getDeclaredField("exchangeName");
        exchangeField.setAccessible(true);
        exchangeField.set(rabbitEventStrategy, "test.exchange");

        java.lang.reflect.Field routingKeyPrefixField = RabbitEventStrategy.class.getDeclaredField("routingKeyPrefix");
        routingKeyPrefixField.setAccessible(true);
        routingKeyPrefixField.set(rabbitEventStrategy, "event.");
    }

    @Test
    @DisplayName("Should publish event to RabbitMQ exchange")
    void shouldPublishEventToRabbitMQ() {
        // Given
        rabbitEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("rabbit-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        // When
        rabbitEventStrategy.publish(event, options);

        // Then
        verify(mockRabbitTemplate, atLeastOnce()).convertAndSend(
            anyString(),
            anyString(),
            eq(event),
            any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should support rabbit and rabbitmq modes")
    void shouldSupportRabbitModes() {
        // Then
        assertThat(rabbitEventStrategy.supports("rabbit")).isTrue();
        assertThat(rabbitEventStrategy.supports("rabbitmq")).isTrue();
        assertThat(rabbitEventStrategy.supports("RabbitMQ")).isTrue();
        assertThat(rabbitEventStrategy.supports("kafka")).isFalse();
    }

    @Test
    @DisplayName("Should check if RabbitMQ connection is ready")
    void shouldCheckIfReady() {
        // Given
        rabbitEventStrategy.initialize();
        when(mockConnection.isOpen()).thenReturn(true);

        // When
        boolean isReady = rabbitEventStrategy.isReady();

        // Then
        assertThat(isReady).isTrue();
    }

    @Test
    @DisplayName("Should handle critical event failures")
    void shouldHandleCriticalEventFailures() throws Exception {
        // Given
        // Set up retry template to fail immediately
        java.lang.reflect.Field retryField = RabbitEventStrategy.class.getDeclaredField("maxRetries");
        retryField.setAccessible(true);
        retryField.set(rabbitEventStrategy, 1);

        rabbitEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("critical-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_DELETED")
            .occurredAt(Instant.now())
            .payload(Map.of("critical", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .critical(true)
            .persistent(false)
            .async(false)
            .build();

        // Make the send method throw an exception
        doThrow(new RuntimeException("Connection failed"))
            .when(mockRabbitTemplate).send(anyString(), anyString(), any(Message.class));

        // When/Then
        try {
            rabbitEventStrategy.publish(event, options);
            assert false : "Should have thrown exception for critical event";
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Failed to publish critical event to RabbitMQ");
        }
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        // Then
        assertThat(rabbitEventStrategy.getName()).isEqualTo("RabbitMQ");
    }
}