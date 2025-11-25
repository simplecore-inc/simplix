package dev.simplecore.simplix.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.config.EventProperties;
import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.strategy.RedisEventStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Redis Stream Event Strategy Test")
class RedisEventStrategyTest {

    private RedisEventStrategy redisEventStrategy;
    private RedisTemplate<String, Object> mockRedisTemplate;
    private RedisConnectionFactory mockConnectionFactory;
    private RedisConnection mockConnection;
    private StreamOperations<String, Object, Object> mockStreamOps;
    private ObjectMapper objectMapper;
    private EventProperties eventProperties;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        mockRedisTemplate = mock(RedisTemplate.class);
        mockConnectionFactory = mock(RedisConnectionFactory.class);
        mockConnection = mock(RedisConnection.class);
        mockStreamOps = mock(StreamOperations.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Setup EventProperties
        eventProperties = new EventProperties();
        eventProperties.setEnrichMetadata(false);
        EventProperties.RedisConfig redisConfig = new EventProperties.RedisConfig();
        redisConfig.setStreamPrefix("test-events");
        EventProperties.RedisConfig.StreamConfig streamConfig = new EventProperties.RedisConfig.StreamConfig();
        streamConfig.setMaxLen(1000);
        streamConfig.setConsumerGroup("test-group");
        streamConfig.setAutoCreateGroup(true);
        redisConfig.setStream(streamConfig);
        eventProperties.setRedis(redisConfig);

        when(mockRedisTemplate.getConnectionFactory()).thenReturn(mockConnectionFactory);
        when(mockConnectionFactory.getConnection()).thenReturn(mockConnection);
        when(mockRedisTemplate.opsForStream()).thenReturn(mockStreamOps);
        when(mockConnection.ping()).thenReturn("PONG");
        when(mockStreamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("1234567890-0"));

        redisEventStrategy = new RedisEventStrategy();

        // Use reflection to inject dependencies
        Field redisTemplateField = RedisEventStrategy.class.getDeclaredField("redisTemplate");
        redisTemplateField.setAccessible(true);
        redisTemplateField.set(redisEventStrategy, mockRedisTemplate);

        Field objectMapperField = RedisEventStrategy.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(redisEventStrategy, objectMapper);

        Field eventPropertiesField = RedisEventStrategy.class.getDeclaredField("eventProperties");
        eventPropertiesField.setAccessible(true);
        eventPropertiesField.set(redisEventStrategy, eventProperties);
    }

    @Test
    @DisplayName("Should publish event to Redis Stream")
    void shouldPublishEventToRedisStream() {
        // Given
        redisEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("redis-event-001")
            .aggregateId("entity-001")
            .eventType("ENTITY_CREATED")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        // When
        redisEventStrategy.publish(event, options);

        // Then
        verify(mockStreamOps, times(1)).add(any(MapRecord.class));
    }

    @Test
    @DisplayName("Should publish event with correct stream key")
    void shouldPublishEventWithCorrectStreamKey() {
        // Given
        redisEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("persistent-event-001")
            .aggregateId("entity-002")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("persistent", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        // When
        redisEventStrategy.publish(event, options);

        // Then
        verify(mockStreamOps, times(1)).add(any(MapRecord.class));
    }

    @Test
    @DisplayName("Should support redis mode")
    void shouldSupportRedisMode() {
        // Then
        assertThat(redisEventStrategy.supports("redis")).isTrue();
        assertThat(redisEventStrategy.supports("REDIS")).isTrue();
        assertThat(redisEventStrategy.supports("local")).isFalse();
        assertThat(redisEventStrategy.supports("kafka")).isFalse();
    }

    @Test
    @DisplayName("Should check if Redis connection is ready")
    void shouldCheckIfRedisConnectionIsReady() {
        // Given
        redisEventStrategy.initialize();
        when(mockConnection.ping()).thenReturn("PONG");

        // When
        boolean isReady = redisEventStrategy.isReady();

        // Then
        assertThat(isReady).isTrue();
    }

    @Test
    @DisplayName("Should handle connection failure gracefully")
    void shouldHandleConnectionFailureGracefully() {
        // Given
        when(mockConnection.ping()).thenThrow(new RuntimeException("Connection failed"));
        redisEventStrategy.initialize();

        // When
        boolean isReady = redisEventStrategy.isReady();

        // Then
        assertThat(isReady).isFalse();
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        // Then
        assertThat(redisEventStrategy.getName()).isEqualTo("RedisStreamEventStrategy");
    }

    @Test
    @DisplayName("Should build correct stream key")
    void shouldBuildCorrectStreamKey() {
        // Given
        redisEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("stream-event-001")
            .aggregateId("entity-003")
            .eventType("ENTITY_DELETED")
            .occurredAt(Instant.now())
            .payload(Map.of("deleted", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        // When
        redisEventStrategy.publish(event, options);

        // Then
        verify(mockStreamOps, times(1)).add(any(MapRecord.class));
    }

    @Test
    @DisplayName("Should handle critical events with failure")
    void shouldHandleCriticalEventsWithFailure() {
        // Given
        redisEventStrategy.initialize();

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

        doThrow(new RuntimeException("Redis connection failed"))
            .when(mockStreamOps).add(any(MapRecord.class));

        // When/Then
        assertThatThrownBy(() -> redisEventStrategy.publish(event, options))
            .isInstanceOf(RedisEventStrategy.RedisStreamPublishException.class)
            .hasMessageContaining("Failed to publish critical event to Redis Stream");
    }

    @Test
    @DisplayName("Should publish event to stream successfully")
    void shouldPublishEventToStreamSuccessfully() {
        // Given
        redisEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("success-event-001")
            .aggregateId("entity-005")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("success", true))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.defaults();

        // When
        redisEventStrategy.publish(event, options);

        // Then
        verify(mockStreamOps, times(1)).add(any(MapRecord.class));
    }

    @Test
    @DisplayName("Should initialize and shutdown properly")
    void shouldInitializeAndShutdownProperly() {
        // When
        redisEventStrategy.initialize();

        // Then
        assertThat(redisEventStrategy.getName()).isNotNull();
        assertThat(redisEventStrategy.isReady()).isTrue();

        // When
        redisEventStrategy.shutdown();

        // Then
        assertThat(redisEventStrategy.getName()).isEqualTo("RedisStreamEventStrategy");
        assertThat(redisEventStrategy.isReady()).isFalse();
    }
}