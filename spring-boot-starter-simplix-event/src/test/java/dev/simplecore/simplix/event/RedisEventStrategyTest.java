package dev.simplecore.simplix.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;

import dev.simplecore.simplix.event.core.PublishOptions;
import dev.simplecore.simplix.event.strategy.RedisEventStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Redis Event Strategy Test")
class RedisEventStrategyTest {

    private RedisEventStrategy redisEventStrategy;
    private RedisTemplate<String, Object> mockRedisTemplate;
    private RedisConnectionFactory mockConnectionFactory;
    private RedisConnection mockConnection;
    private ValueOperations<String, Object> mockValueOperations;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockRedisTemplate = mock(RedisTemplate.class);
        mockConnectionFactory = mock(RedisConnectionFactory.class);
        mockConnection = mock(RedisConnection.class);
        mockValueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        when(mockRedisTemplate.getConnectionFactory()).thenReturn(mockConnectionFactory);
        when(mockConnectionFactory.getConnection()).thenReturn(mockConnection);
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);

        redisEventStrategy = new RedisEventStrategy();

        // Use reflection to inject dependencies
        Field redisTemplateField = RedisEventStrategy.class.getDeclaredField("redisTemplate");
        redisTemplateField.setAccessible(true);
        redisTemplateField.set(redisEventStrategy, mockRedisTemplate);

        Field objectMapperField = RedisEventStrategy.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(redisEventStrategy, objectMapper);

        // Set configuration values
        Field channelPrefixField = RedisEventStrategy.class.getDeclaredField("channelPrefix");
        channelPrefixField.setAccessible(true);
        channelPrefixField.set(redisEventStrategy, "test-events");

        Field defaultTtlSecondsField = RedisEventStrategy.class.getDeclaredField("defaultTtlSeconds");
        defaultTtlSecondsField.setAccessible(true);
        defaultTtlSecondsField.set(redisEventStrategy, 86400L);
    }

    @Test
    @DisplayName("Should publish event to Redis channel")
    void shouldPublishEventToRedisChannel() {
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
        verify(mockRedisTemplate, atLeastOnce()).convertAndSend(
            anyString(),
            anyString()  // Event is serialized to String
        );
    }

    @Test
    @DisplayName("Should store event persistently when enabled")
    void shouldStoreEventPersistentlyWhenEnabled() {
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

        PublishOptions options = PublishOptions.builder()
            .persistent(true)
            .build();

        // When
        redisEventStrategy.publish(event, options);

        // Then
        verify(mockValueOperations, times(1)).set(
            anyString(),
            any(Event.class),
            anyLong(),
            any(TimeUnit.class)
        );
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
        assertThat(redisEventStrategy.getName()).isEqualTo("RedisEventStrategy");
    }

    @Test
    @DisplayName("Should build correct channel name")
    void shouldBuildCorrectChannelName() throws Exception {
        // Given
        redisEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("channel-event-001")
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
        verify(mockRedisTemplate).convertAndSend(
            argThat(channel -> channel.startsWith("test-events:") && channel.contains("ENTITY_DELETED")),
            anyString()
        );
    }

    @Test
    @DisplayName("Should handle critical events with failure")
    void shouldHandleCriticalEventsWithFailure() throws Exception {
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
            .when(mockRedisTemplate).convertAndSend(anyString(), anyString());

        // When/Then
        try {
            redisEventStrategy.publish(event, options);
            assert false : "Should have thrown exception for critical event";
        } catch (RuntimeException e) {
            assertThat(e).isInstanceOf(RedisEventStrategy.RedisPublishException.class);
            assertThat(e.getMessage()).contains("Failed to publish critical event to Redis");
        }
    }

    @Test
    @DisplayName("Should store event when persistent option is enabled")
    void shouldStoreEventWhenPersistentOptionIsEnabled() throws Exception {
        // Given

        redisEventStrategy.initialize();

        Event event = TestEvent.builder()
            .id("non-persistent-event-001")
            .aggregateId("entity-005")
            .eventType("ENTITY_UPDATED")
            .occurredAt(Instant.now())
            .payload(Map.of("persistent", false))
            .metadata(new HashMap<>())
            .build();

        PublishOptions options = PublishOptions.builder()
            .persistent(true)
            .build();

        // When
        redisEventStrategy.publish(event, options);

        // Then
        verify(mockValueOperations, times(1)).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        verify(mockRedisTemplate, atLeastOnce()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("Should initialize and shutdown properly")
    void shouldInitializeAndShutdownProperly() {
        // When
        redisEventStrategy.initialize();

        // Then
        assertThat(redisEventStrategy.getName()).isNotNull();

        // When
        redisEventStrategy.shutdown();

        // Then
        assertThat(redisEventStrategy.getName()).isEqualTo("RedisEventStrategy");
    }
}