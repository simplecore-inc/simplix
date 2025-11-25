package dev.simplecore.simplix.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;

import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.strategy.KafkaEventStrategy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("Kafka Event Strategy Test")
class KafkaEventStrategyTest {

    private KafkaEventStrategy kafkaEventStrategy;
    private KafkaTemplate<String, Object> mockKafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockKafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        kafkaEventStrategy = new KafkaEventStrategy();

        // Use reflection to inject dependencies
        Field kafkaTemplateField = KafkaEventStrategy.class.getDeclaredField("kafkaTemplate");
        kafkaTemplateField.setAccessible(true);
        kafkaTemplateField.set(kafkaEventStrategy, mockKafkaTemplate);

        // KafkaEventStrategy doesn't have objectMapper field

        // Set configuration values
        Field topicPrefixField = KafkaEventStrategy.class.getDeclaredField("topicPrefix");
        topicPrefixField.setAccessible(true);
        topicPrefixField.set(kafkaEventStrategy, "simplix-events");

        Field defaultTopicField = KafkaEventStrategy.class.getDeclaredField("defaultTopic");
        defaultTopicField.setAccessible(true);
        defaultTopicField.set(kafkaEventStrategy, "domain-events");
    }

    @Test
    @DisplayName("Should publish event to Kafka topic")
    void shouldPublishEventToKafkaTopic() {
        // Given
        kafkaEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("kafka-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Complete the future with a mock result
        SendResult<String, Object> sendResult = mock(SendResult.class);
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>("test-topic", event);
        RecordMetadata recordMetadata = new RecordMetadata(
            new TopicPartition("test-topic", 0), 0L, 0L, 0L, null, 0, 0
        );
        when(sendResult.getProducerRecord()).thenReturn(producerRecord);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        future.complete(sendResult);

        // When
        kafkaEventStrategy.publish(event, options);

        // Then
        verify(mockKafkaTemplate, times(1)).send(
            any(ProducerRecord.class)
        );
    }

    @Test
    @DisplayName("Should build correct topic name")
    void shouldBuildCorrectTopicName() {
        // Given
        kafkaEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("topic-event-001")
            .aggregateId("entity-002")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("updated", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        future.complete(mock(SendResult.class));

        // When
        kafkaEventStrategy.publish(event, options);

        // Then - topic is built from topicPrefix + eventType.toLowerCase()
        verify(mockKafkaTemplate).send(
            argThat((ProducerRecord<String, Object> record) ->
                record.topic().equals("simplix-events-entity_updated"))
        );
    }

    @Test
    @DisplayName("Should use routing key when specified")
    void shouldUseRoutingKeyWhenSpecified() throws Exception {
        // Given
        kafkaEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("routing-key-event-001")
            .aggregateId("entity-003")
            .eventType("ENTITY_DELETED")
            .occurredAt(Instant.now())
            .payload(Map.of("useRoutingKey", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .routingKey("custom-topic")
            .build();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        future.complete(mock(SendResult.class));

        // When
        kafkaEventStrategy.publish(event, options);

        // Then - topic uses routingKey when specified
        verify(mockKafkaTemplate).send(
            argThat((ProducerRecord<String, Object> record) ->
                record.topic().equals("simplix-events-custom-topic"))
        );
    }

    @Test
    @DisplayName("Should support kafka mode")
    void shouldSupportKafkaMode() {
        // Then
        assertThat(kafkaEventStrategy.supports("kafka")).isTrue();
        assertThat(kafkaEventStrategy.supports("KAFKA")).isTrue();
        assertThat(kafkaEventStrategy.supports("local")).isFalse();
        assertThat(kafkaEventStrategy.supports("redis")).isFalse();
    }

    @Test
    @DisplayName("Should check if Kafka is ready")
    void shouldCheckIfKafkaIsReady() {
        // Given
        kafkaEventStrategy.initialize();

        // When
        boolean isReady = kafkaEventStrategy.isReady();

        // Then
        assertThat(isReady).isTrue();
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        // Then
        assertThat(kafkaEventStrategy.getName()).isEqualTo("KafkaEventStrategy");
    }

    @Test
    @DisplayName("Should handle publish failure for critical events")
    void shouldHandlePublishFailureForCriticalEvents() throws Exception {
        // Given
        kafkaEventStrategy.initialize();

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
            .async(false)  // Use sync for predictable testing
            .maxRetries(0) // No retries to get immediate exception
            .build();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        // Make send().get() throw exception for sync mode
        future.completeExceptionally(new RuntimeException("Kafka send failed"));

        // When/Then
        try {
            kafkaEventStrategy.publish(event, options);
            assert false : "Should have thrown exception for critical event";
        } catch (KafkaEventStrategy.KafkaPublishException e) {
            // Expected for critical events
            assertThat(e.getMessage()).contains("Failed to publish critical event to Kafka");
        }
    }

    @Test
    @DisplayName("Should add event headers")
    void shouldAddEventHeaders() {
        // Given
        kafkaEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("header-event-001")
            .aggregateId("entity-005")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("header", "test"))
            .metadata(Map.of("custom", "metadata"))
            .build();

        PublishOptions options = PublishOptions.defaults();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        future.complete(mock(SendResult.class));

        // When
        kafkaEventStrategy.publish(event, options);

        // Then
        verify(mockKafkaTemplate).send(
            argThat((ProducerRecord<String, Object> record) -> {
                Event evt = (Event) record.value();
                return evt.getMetadata().containsKey("custom");
            })
        );
    }

    @Test
    @DisplayName("Should handle async publish for non-critical events")
    void shouldHandleAsyncPublishForNonCriticalEvents() throws Exception {
        // Given
        kafkaEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("retry-event-001")
            .aggregateId("entity-006")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("retry", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .critical(false)
            .async(true)
            .build();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
        future.completeExceptionally(new RuntimeException("Temporary failure"));

        // When
        kafkaEventStrategy.publish(event, options);

        // Then - Should not throw exception for non-critical async events
        Thread.sleep(100); // Wait for async processing
    }

    @Test
    @DisplayName("Should initialize and shutdown properly")
    void shouldInitializeAndShutdownProperly() {
        // When
        kafkaEventStrategy.initialize();

        // Then
        assertThat(kafkaEventStrategy.isReady()).isTrue();

        // When
        kafkaEventStrategy.shutdown();

        // Then
        assertThat(kafkaEventStrategy.getName()).isEqualTo("KafkaEventStrategy");
    }
}