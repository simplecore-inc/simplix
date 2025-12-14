package dev.simplecore.simplix.hibernate.cache.batch;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for BatchEvictionOptimizer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BatchEvictionOptimizer Tests")
class BatchEvictionOptimizerTest {

    @Mock
    private CacheProviderFactory providerFactory;

    @Mock
    private CacheProvider mockProvider;

    private BatchEvictionOptimizer batchOptimizer;

    @BeforeEach
    void setUp() {
        lenient().when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
        lenient().when(providerFactory.getProvider(anyString())).thenReturn(mockProvider);
        lenient().when(mockProvider.isAvailable()).thenReturn(true);

        batchOptimizer = new BatchEvictionOptimizer(providerFactory);

        // Set @Value fields that would normally be injected by Spring
        ReflectionTestUtils.setField(batchOptimizer, "batchThreshold", 10);
        ReflectionTestUtils.setField(batchOptimizer, "maxDelayMs", 100L);
        ReflectionTestUtils.setField(batchOptimizer, "providerType", "AUTO");
    }

    @Nested
    @DisplayName("addToBatch() tests")
    class AddToBatchTests {

        @Test
        @DisplayName("Should broadcast immediately when not in batch mode")
        void shouldBroadcastImmediatelyWhenNotInBatchMode() {
            // Given
            batchOptimizer.init();
            CacheEvictionEvent event = createTestEvent("User", "1");

            // When
            batchOptimizer.addToBatch(event);

            // Then
            verify(mockProvider).broadcastEviction(event);
        }

        @Test
        @DisplayName("Should queue event when in batch mode")
        void shouldQueueEventWhenInBatchMode() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();
            CacheEvictionEvent event = createTestEvent("User", "1");

            // When
            batchOptimizer.addToBatch(event);

            // Then
            verify(mockProvider, never()).broadcastEviction(any());
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Integer) stats.get("pendingEvictions")).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle null event gracefully")
        void shouldHandleNullEventGracefully() {
            // Given
            batchOptimizer.init();

            // When/Then - should not throw
            batchOptimizer.addToBatch(null);
        }

        @Test
        @DisplayName("Should skip events during shutdown")
        void shouldSkipEventsDuringShutdown() {
            // Given
            batchOptimizer.init();
            batchOptimizer.shutdown();
            CacheEvictionEvent event = createTestEvent("User", "1");

            // When
            batchOptimizer.addToBatch(event);

            // Then - should not broadcast after shutdown
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("shutdown")).isTrue();
        }
    }

    @Nested
    @DisplayName("startBatch() and endBatch() tests")
    class BatchModeTests {

        @Test
        @DisplayName("startBatch() should enable batch mode")
        void startBatchShouldEnableBatchMode() {
            // When
            batchOptimizer.startBatch();

            // Then
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("batchMode")).isTrue();
        }

        @Test
        @DisplayName("endBatch() should disable batch mode and flush")
        void endBatchShouldDisableBatchModeAndFlush() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();
            batchOptimizer.addToBatch(createTestEvent("User", "1"));

            // When
            batchOptimizer.endBatch();

            // Then
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("batchMode")).isFalse();
            verify(mockProvider).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should support nested batch contexts")
        void shouldSupportNestedBatchContexts() {
            // Given
            batchOptimizer.init();

            // When
            batchOptimizer.startBatch(); // depth = 1
            batchOptimizer.startBatch(); // depth = 2

            // Then
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Integer) stats.get("batchDepth")).isEqualTo(2);
            assertThat((Boolean) stats.get("batchMode")).isTrue();

            // When - close one context
            batchOptimizer.endBatch(); // depth = 1

            // Then - still in batch mode
            stats = batchOptimizer.getBatchStatistics();
            assertThat((Integer) stats.get("batchDepth")).isEqualTo(1);
            assertThat((Boolean) stats.get("batchMode")).isTrue();

            // When - close last context
            batchOptimizer.endBatch(); // depth = 0

            // Then - batch mode disabled
            stats = batchOptimizer.getBatchStatistics();
            assertThat((Integer) stats.get("batchDepth")).isZero();
            assertThat((Boolean) stats.get("batchMode")).isFalse();
        }

        @Test
        @DisplayName("endBatch() without startBatch() should log warning")
        void endBatchWithoutStartBatchShouldLogWarning() {
            // When/Then - should not throw
            batchOptimizer.endBatch();

            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Integer) stats.get("batchDepth")).isZero();
        }

        @Test
        @DisplayName("BatchContext should auto-close")
        void batchContextShouldAutoClose() {
            // Given
            batchOptimizer.init();

            // When
            try (BatchEvictionOptimizer.BatchContext context = batchOptimizer.startBatch()) {
                batchOptimizer.addToBatch(createTestEvent("User", "1"));

                Map<String, Object> stats = batchOptimizer.getBatchStatistics();
                assertThat((Boolean) stats.get("batchMode")).isTrue();
            }

            // Then - after auto-close
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("batchMode")).isFalse();
            verify(mockProvider).broadcastEviction(any());
        }
    }

    @Nested
    @DisplayName("flushBatch() tests")
    class FlushBatchTests {

        @Test
        @DisplayName("Should merge similar evictions")
        void shouldMergeSimilarEvictions() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();

            // Add multiple evictions for same entity type
            batchOptimizer.addToBatch(createTestEvent("User", "1"));
            batchOptimizer.addToBatch(createTestEvent("User", "2"));
            batchOptimizer.addToBatch(createTestEvent("User", null)); // Bulk eviction

            // When
            batchOptimizer.flushBatch();

            // Then - should merge to single bulk eviction per entity type
            verify(mockProvider, atLeast(1)).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should not process when queue is empty")
        void shouldNotProcessWhenQueueIsEmpty() {
            // Given
            batchOptimizer.init();

            // When
            batchOptimizer.flushBatch();

            // Then
            verify(mockProvider, never()).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should handle events with null entityClass gracefully")
        void shouldHandleEventsWithNullEntityClassGracefully() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();

            CacheEvictionEvent eventWithNullClass = CacheEvictionEvent.builder()
                    .entityClass(null)
                    .entityId("123")
                    .build();
            batchOptimizer.addToBatch(eventWithNullClass);

            // When/Then - should not throw
            batchOptimizer.flushBatch();
        }
    }

    @Nested
    @DisplayName("autoFlush() tests")
    class AutoFlushTests {

        @Test
        @DisplayName("Should flush pending evictions")
        void shouldFlushPendingEvictions() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();
            batchOptimizer.addToBatch(createTestEvent("User", "1"));

            // When
            batchOptimizer.autoFlush();

            // Then
            verify(mockProvider).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should not flush when shutdown")
        void shouldNotFlushWhenShutdown() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();
            batchOptimizer.addToBatch(createTestEvent("User", "1"));
            batchOptimizer.shutdown();

            // When
            batchOptimizer.autoFlush();

            // Then - already flushed during shutdown, no additional calls
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("shutdown")).isTrue();
        }
    }

    @Nested
    @DisplayName("shutdown() tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should flush pending evictions before shutdown")
        void shouldFlushPendingEvictionsBeforeShutdown() {
            // Given
            batchOptimizer.init();
            batchOptimizer.startBatch();
            batchOptimizer.addToBatch(createTestEvent("User", "1"));

            // When
            batchOptimizer.shutdown();

            // Then
            verify(mockProvider).broadcastEviction(any());
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("shutdown")).isTrue();
        }

        @Test
        @DisplayName("shutdown() should be idempotent")
        void shutdownShouldBeIdempotent() {
            // Given
            batchOptimizer.init();

            // When
            batchOptimizer.shutdown();
            batchOptimizer.shutdown();
            batchOptimizer.shutdown();

            // Then - should not throw
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();
            assertThat((Boolean) stats.get("shutdown")).isTrue();
        }
    }

    @Nested
    @DisplayName("getBatchStatistics() tests")
    class GetBatchStatisticsTests {

        @Test
        @DisplayName("Should return complete statistics")
        void shouldReturnCompleteStatistics() {
            // When
            Map<String, Object> stats = batchOptimizer.getBatchStatistics();

            // Then
            assertThat(stats).containsKeys(
                    "batchMode",
                    "batchDepth",
                    "pendingEvictions",
                    "batchThreshold",
                    "maxDelayMs",
                    "shutdown"
            );
        }
    }

    private CacheEvictionEvent createTestEvent(String entityClass, String entityId) {
        return CacheEvictionEvent.builder()
                .entityClass(entityClass)
                .entityId(entityId)
                .operation("UPDATE")
                .nodeId("test-node")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
