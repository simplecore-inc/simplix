package dev.simplecore.simplix.hibernate.cache.transaction;

import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.event.PendingEvictionCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for TransactionAwareCacheEvictionCollector.
 *
 * Note: Tests involving TransactionSynchronizationManager require Spring context
 * and are tested in integration tests. These unit tests focus on the non-transactional
 * fallback behavior and basic API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransactionAwareCacheEvictionCollector Tests")
class TransactionAwareCacheEvictionCollectorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransactionAwareCacheEvictionCollector collector;

    @BeforeEach
    void setUp() {
        collector = new TransactionAwareCacheEvictionCollector(eventPublisher);
    }

    @Nested
    @DisplayName("collect() tests - without transaction")
    class CollectWithoutTransactionTests {

        @Test
        @DisplayName("Should publish immediately when no transaction active")
        void shouldPublishImmediatelyWhenNoTransactionActive() {
            // Given
            PendingEviction eviction = createTestEviction();

            // When
            collector.collect(eviction);

            // Then - should publish immediately since no transaction is active
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEvictionCompletedEvent event = captor.getValue();
            assertThat(event.getEvictionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle null eviction gracefully")
        void shouldHandleNullEvictionGracefully() {
            // When
            collector.collect(null);

            // Then
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Should retry on first publish failure")
        void shouldRetryOnFirstPublishFailure() {
            // Given
            PendingEviction eviction = createTestEviction();

            doThrow(new RuntimeException("Publish failed"))
                    .doNothing()
                    .when(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));

            // When
            collector.collect(eviction);

            // Then - should have tried twice
            verify(eventPublisher, times(2)).publishEvent(any(PendingEvictionCompletedEvent.class));
        }
    }

    @Nested
    @DisplayName("getPendingCount() and hasPendingEvictions() tests")
    class PendingEvictionsQueryTests {

        @Test
        @DisplayName("Should return 0 and false when no pending evictions")
        void shouldReturnZeroAndFalseWhenNoPendingEvictions() {
            // When/Then - without transaction context, these return 0 and false
            assertThat(collector.getPendingCount()).isZero();
            assertThat(collector.hasPendingEvictions()).isFalse();
        }
    }

    @Nested
    @DisplayName("Event content tests")
    class EventContentTests {

        @Test
        @DisplayName("Published event should contain the eviction")
        void publishedEventShouldContainTheEviction() {
            // Given
            PendingEviction eviction = PendingEviction.of(
                    TestEntity.class,
                    123L,
                    "test-region",
                    PendingEviction.EvictionOperation.UPDATE
            );

            // When
            collector.collect(eviction);

            // Then
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEvictionCompletedEvent event = captor.getValue();
            assertThat(event.getPendingEvictions()).hasSize(1);
            PendingEviction capturedEviction = event.getPendingEvictions().get(0);
            assertThat(capturedEviction.getEntityClassName()).isEqualTo(TestEntity.class.getName());
            assertThat(capturedEviction.getEntityId()).isEqualTo("123");
            assertThat(capturedEviction.getRegion()).isEqualTo("test-region");
            assertThat(capturedEviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.UPDATE);
        }

        @Test
        @DisplayName("Should handle eviction with null region")
        void shouldHandleEvictionWithNullRegion() {
            // Given
            PendingEviction eviction = PendingEviction.of(
                    TestEntity.class,
                    456L,
                    null,
                    PendingEviction.EvictionOperation.INSERT
            );

            // When
            collector.collect(eviction);

            // Then
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEviction capturedEviction = captor.getValue().getPendingEvictions().get(0);
            assertThat(capturedEviction.getRegion()).isNull();
            assertThat(capturedEviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.INSERT);
        }

        @Test
        @DisplayName("Should handle DELETE operation")
        void shouldHandleDeleteOperation() {
            // Given
            PendingEviction eviction = PendingEviction.of(
                    TestEntity.class,
                    789L,
                    null,
                    PendingEviction.EvictionOperation.DELETE
            );

            // When
            collector.collect(eviction);

            // Then
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEviction capturedEviction = captor.getValue().getPendingEvictions().get(0);
            assertThat(capturedEviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.DELETE);
        }

        @Test
        @DisplayName("Should handle BULK_UPDATE operation with null entityId")
        void shouldHandleBulkUpdateOperation() {
            // Given
            PendingEviction eviction = PendingEviction.of(
                    TestEntity.class,
                    null,
                    null,
                    PendingEviction.EvictionOperation.BULK_UPDATE
            );

            // When
            collector.collect(eviction);

            // Then
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEviction capturedEviction = captor.getValue().getPendingEvictions().get(0);
            assertThat(capturedEviction.getEntityId()).isNull();
            assertThat(capturedEviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_UPDATE);
        }
    }

    @Nested
    @DisplayName("Multiple evictions tests")
    class MultipleEvictionsTests {

        @Test
        @DisplayName("Should publish each eviction separately when no transaction")
        void shouldPublishEachEvictionSeparatelyWhenNoTransaction() {
            // Given
            PendingEviction eviction1 = PendingEviction.of(
                    TestEntity.class, 1L, null, PendingEviction.EvictionOperation.UPDATE);
            PendingEviction eviction2 = PendingEviction.of(
                    AnotherEntity.class, 2L, null, PendingEviction.EvictionOperation.DELETE);

            // When
            collector.collect(eviction1);
            collector.collect(eviction2);

            // Then - each eviction should be published separately
            verify(eventPublisher, times(2)).publishEvent(any(PendingEvictionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should handle different entity types")
        void shouldHandleDifferentEntityTypes() {
            // Given
            PendingEviction eviction = PendingEviction.of(
                    AnotherEntity.class,
                    999L,
                    "another-region",
                    PendingEviction.EvictionOperation.UPDATE
            );

            // When
            collector.collect(eviction);

            // Then
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEviction capturedEviction = captor.getValue().getPendingEvictions().get(0);
            assertThat(capturedEviction.getEntityClassName()).isEqualTo(AnotherEntity.class.getName());
        }
    }

    @Nested
    @DisplayName("Fallback eviction tests")
    class FallbackEvictionTests {

        @Test
        @DisplayName("Should attempt fallback when all retries fail")
        void shouldAttemptFallbackWhenAllRetriesFail() {
            // Given
            PendingEviction eviction = createTestEviction();

            // All publish attempts fail
            doThrow(new RuntimeException("Publish failed"))
                    .when(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));

            // When - should not throw even when fallback fails
            collector.collect(eviction);

            // Then - should have tried MAX_PUBLISH_RETRY_ATTEMPTS (2) times
            verify(eventPublisher, times(2)).publishEvent(any(PendingEvictionCompletedEvent.class));
        }
    }

    private PendingEviction createTestEviction() {
        return PendingEviction.of(
                TestEntity.class,
                System.nanoTime(),
                null,
                PendingEviction.EvictionOperation.UPDATE
        );
    }

    // Test entity classes for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }

    private static class AnotherEntity {
        private Long id;
        private String value;
    }
}
