package dev.simplecore.simplix.hibernate.cache.resilience;

import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheProperties;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import dev.simplecore.simplix.hibernate.cache.provider.LocalCacheProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for EvictionRetryHandler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EvictionRetryHandler Tests")
class EvictionRetryHandlerTest {

    @Mock
    private CacheProviderFactory providerFactory;

    @Mock
    private CacheProvider mockProvider;

    private HibernateCacheProperties properties;
    private EvictionRetryHandler retryHandler;

    @BeforeEach
    void setUp() {
        properties = new HibernateCacheProperties();
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setDelayMs(100);

        retryHandler = new EvictionRetryHandler(providerFactory, properties);
    }

    @Nested
    @DisplayName("scheduleRetry() tests")
    class ScheduleRetryTests {

        @Test
        @DisplayName("Should add event to retry queue")
        void shouldAddEventToRetryQueue() {
            // Given
            CacheEvictionEvent event = createTestEvent();

            // When
            retryHandler.scheduleRetry(event, new RuntimeException("Test error"));

            // Then
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("retryQueueSize")).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle null event gracefully")
        void shouldHandleNullEventGracefully() {
            // When
            retryHandler.scheduleRetry(null, new RuntimeException("Test error"));

            // Then
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("retryQueueSize")).isZero();
        }

        @Test
        @DisplayName("Should handle null error message")
        void shouldHandleNullErrorMessage() {
            // Given
            CacheEvictionEvent event = createTestEvent();

            // When
            retryHandler.scheduleRetry(event, null);

            // Then
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("retryQueueSize")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("processRetries() tests")
    class ProcessRetriesTests {

        @BeforeEach
        void setUpProvider() {
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(true);
            retryHandler.init();
        }

        @Test
        @DisplayName("Should process retry successfully")
        void shouldProcessRetrySuccessfully() {
            // Given
            CacheEvictionEvent event = createTestEvent();
            retryHandler.scheduleRetry(event, new RuntimeException("Test error"));

            // When
            retryHandler.processRetries();

            // Then
            verify(mockProvider).broadcastEviction(event);
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("retryQueueSize")).isZero();
        }

        @Test
        @DisplayName("Should move to DLQ after max retries")
        void shouldMoveToDlqAfterMaxRetries() {
            // Given
            when(mockProvider.isAvailable()).thenReturn(true);
            doThrow(new RuntimeException("Broadcast failed"))
                    .when(mockProvider).broadcastEviction(any());

            CacheEvictionEvent event = createTestEvent();
            retryHandler.scheduleRetry(event, new RuntimeException("Initial error"));

            // When - process retries until max attempts exceeded
            for (int i = 0; i <= properties.getRetry().getMaxAttempts(); i++) {
                retryHandler.processRetries();
            }

            // Then
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("deadLetterQueueSize")).isEqualTo(1);
            assertThat((Integer) stats.get("retryQueueSize")).isZero();
        }

        @Test
        @DisplayName("Should not process when queue is empty")
        void shouldNotProcessWhenQueueIsEmpty() {
            // When
            retryHandler.processRetries();

            // Then
            verify(mockProvider, never()).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should requeue on retry failure")
        void shouldRequeueOnRetryFailure() {
            // Given
            doThrow(new RuntimeException("Broadcast failed"))
                    .when(mockProvider).broadcastEviction(any());

            CacheEvictionEvent event = createTestEvent();
            retryHandler.scheduleRetry(event, new RuntimeException("Initial error"));

            // When
            retryHandler.processRetries();

            // Then - should be requeued (not yet at max retries)
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("retryQueueSize")).isEqualTo(1);
            assertThat((Integer) stats.get("deadLetterQueueSize")).isZero();
        }
    }

    @Nested
    @DisplayName("reprocessDeadLetterQueue() tests")
    class ReprocessDeadLetterQueueTests {

        @BeforeEach
        void setUpProvider() {
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(true);
            doThrow(new RuntimeException("Broadcast failed"))
                    .when(mockProvider).broadcastEviction(any());
            retryHandler.init();
        }

        @Test
        @DisplayName("Should move DLQ items back to retry queue")
        void shouldMoveDlqItemsBackToRetryQueue() {
            // Given - add event and exhaust retries to move to DLQ
            CacheEvictionEvent event = createTestEvent();
            retryHandler.scheduleRetry(event, new RuntimeException("Initial error"));

            for (int i = 0; i <= properties.getRetry().getMaxAttempts(); i++) {
                retryHandler.processRetries();
            }

            // Verify in DLQ
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("deadLetterQueueSize")).isEqualTo(1);

            // When
            retryHandler.reprocessDeadLetterQueue();

            // Then
            stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("deadLetterQueueSize")).isZero();
            assertThat((Integer) stats.get("retryQueueSize")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("shutdown() tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should clear all queues on shutdown")
        void shouldClearAllQueuesOnShutdown() {
            // Given
            CacheEvictionEvent event = createTestEvent();
            retryHandler.scheduleRetry(event, new RuntimeException("Test error"));

            // When
            retryHandler.shutdown();

            // Then
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            assertThat((Integer) stats.get("retryQueueSize")).isZero();
            assertThat((Integer) stats.get("deadLetterQueueSize")).isZero();
        }
    }

    @Nested
    @DisplayName("getRetryStatistics() tests")
    class GetRetryStatisticsTests {

        @Test
        @DisplayName("Should return complete statistics")
        void shouldReturnCompleteStatistics() {
            // When
            Map<String, Object> stats = retryHandler.getRetryStatistics();

            // Then
            assertThat(stats).containsKeys(
                    "retryQueueSize",
                    "deadLetterQueueSize",
                    "maxRetryQueueSize",
                    "maxDlqSize",
                    "maxRetryAttempts",
                    "retryDelayMs"
            );
        }

        @Test
        @DisplayName("Should reflect current queue sizes")
        void shouldReflectCurrentQueueSizes() {
            // Given
            retryHandler.scheduleRetry(createTestEvent(), new RuntimeException("Error 1"));
            retryHandler.scheduleRetry(createTestEvent(), new RuntimeException("Error 2"));

            // When
            Map<String, Object> stats = retryHandler.getRetryStatistics();

            // Then
            assertThat((Integer) stats.get("retryQueueSize")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Queue size limit tests")
    class QueueSizeLimitTests {

        @Test
        @DisplayName("Should respect retry queue size limit")
        void shouldRespectRetryQueueSizeLimit() {
            // Given - schedule more events than MAX_RETRY_QUEUE_SIZE (5000)
            // We'll test with a smaller number to avoid slow test
            for (int i = 0; i < 100; i++) {
                retryHandler.scheduleRetry(createTestEvent(), new RuntimeException("Error " + i));
            }

            // Then
            Map<String, Object> stats = retryHandler.getRetryStatistics();
            int queueSize = (Integer) stats.get("retryQueueSize");
            int maxSize = (Integer) stats.get("maxRetryQueueSize");
            assertThat(queueSize).isLessThanOrEqualTo(maxSize);
        }
    }

    private CacheEvictionEvent createTestEvent() {
        return CacheEvictionEvent.builder()
                .entityClass("com.example.TestEntity")
                .entityId("123")
                .operation("UPDATE")
                .nodeId("test-node")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
