package dev.simplecore.simplix.hibernate.cache.handler;

import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.event.PendingEvictionCompletedEvent;
import dev.simplecore.simplix.hibernate.cache.monitoring.EvictionMetrics;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PostCommitCacheEvictionHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostCommitCacheEvictionHandler Tests")
class PostCommitCacheEvictionHandlerTest {

    @Mock
    private CacheEvictionStrategy evictionStrategy;

    @Mock
    private EvictionMetrics evictionMetrics;

    private PostCommitCacheEvictionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PostCommitCacheEvictionHandler(evictionStrategy, evictionMetrics);
    }

    @Nested
    @DisplayName("handlePostCommitEviction() tests")
    class HandlePostCommitEvictionTests {

        @Test
        @DisplayName("Should process all pending evictions")
        void shouldProcessAllPendingEvictions() {
            // Given
            List<PendingEviction> evictions = Arrays.asList(
                    createPendingEviction(TestEntity.class, "1", PendingEviction.EvictionOperation.UPDATE),
                    createPendingEviction(TestEntity.class, "2", PendingEviction.EvictionOperation.UPDATE),
                    createPendingEviction(TestEntity.class, "3", PendingEviction.EvictionOperation.DELETE)
            );
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, evictions);

            // When
            handler.handlePostCommitEviction(event);

            // Then
            verify(evictionStrategy, times(3)).evict(any(), any());
            verify(evictionMetrics, times(3)).recordSuccess();
        }

        @Test
        @DisplayName("Should skip empty eviction list")
        void shouldSkipEmptyEvictionList() {
            // Given
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, Collections.emptyList());

            // When
            handler.handlePostCommitEviction(event);

            // Then
            verify(evictionStrategy, never()).evict(any(), any());
        }

        @Test
        @DisplayName("Should handle bulk operations")
        void shouldHandleBulkOperations() {
            // Given
            PendingEviction bulkEviction = createPendingEviction(
                    TestEntity.class, null, PendingEviction.EvictionOperation.BULK_UPDATE);
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, List.of(bulkEviction));

            // When
            handler.handlePostCommitEviction(event);

            // Then - should evict with null entityId for bulk
            verify(evictionStrategy).evict(TestEntity.class, null);
        }

        @Test
        @DisplayName("Should handle BULK_DELETE operations")
        void shouldHandleBulkDeleteOperations() {
            // Given
            PendingEviction bulkEviction = createPendingEviction(
                    TestEntity.class, null, PendingEviction.EvictionOperation.BULK_DELETE);
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, List.of(bulkEviction));

            // When
            handler.handlePostCommitEviction(event);

            // Then - should evict with null entityId for bulk
            verify(evictionStrategy).evict(TestEntity.class, null);
        }

        @Test
        @DisplayName("Should skip null pending eviction entries")
        void shouldSkipNullPendingEvictionEntries() {
            // Given
            List<PendingEviction> evictions = Arrays.asList(
                    createPendingEviction(TestEntity.class, "1", PendingEviction.EvictionOperation.UPDATE),
                    null,
                    createPendingEviction(TestEntity.class, "2", PendingEviction.EvictionOperation.UPDATE)
            );
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, evictions);

            // When
            handler.handlePostCommitEviction(event);

            // Then - should only process 2 valid evictions
            verify(evictionStrategy, times(2)).evict(any(), any());
            verify(evictionMetrics, times(2)).recordSuccess();
        }

        @Test
        @DisplayName("Should continue processing after eviction failure")
        void shouldContinueProcessingAfterEvictionFailure() {
            // Given
            List<PendingEviction> evictions = Arrays.asList(
                    createPendingEviction(TestEntity.class, "1", PendingEviction.EvictionOperation.UPDATE),
                    createPendingEviction(TestEntity.class, "2", PendingEviction.EvictionOperation.UPDATE),
                    createPendingEviction(TestEntity.class, "3", PendingEviction.EvictionOperation.UPDATE)
            );
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, evictions);

            // First call succeeds, second fails, third succeeds
            doNothing()
                    .doThrow(new RuntimeException("Eviction failed"))
                    .doNothing()
                    .when(evictionStrategy).evict(any(), any());

            // When
            handler.handlePostCommitEviction(event);

            // Then - all three should be attempted
            verify(evictionStrategy, times(3)).evict(any(), any());
            verify(evictionMetrics, times(2)).recordSuccess();
            verify(evictionMetrics, times(1)).recordFailure();
        }

        @Test
        @DisplayName("Should skip eviction when entity class cannot be resolved")
        void shouldSkipEvictionWhenEntityClassCannotBeResolved() {
            // Given
            PendingEviction evictionWithBadClass = PendingEviction.builder()
                    .entityClassName("com.nonexistent.FakeClass")
                    .entityId("123")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, List.of(evictionWithBadClass));

            // When
            handler.handlePostCommitEviction(event);

            // Then - should not attempt eviction since class can't be resolved
            verify(evictionStrategy, never()).evict(any(), any());
        }

        @Test
        @DisplayName("Should work without EvictionMetrics")
        void shouldWorkWithoutEvictionMetrics() {
            // Given
            PostCommitCacheEvictionHandler handlerWithoutMetrics =
                    new PostCommitCacheEvictionHandler(evictionStrategy, null);

            PendingEviction eviction = createPendingEviction(
                    TestEntity.class, "1", PendingEviction.EvictionOperation.UPDATE);
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, List.of(eviction));

            // When/Then - should not throw
            handlerWithoutMetrics.handlePostCommitEviction(event);
            verify(evictionStrategy).evict(TestEntity.class, "1");
        }
    }

    private PendingEviction createPendingEviction(
            Class<?> entityClass, String entityId, PendingEviction.EvictionOperation operation) {
        return PendingEviction.of(entityClass, entityId, null, operation);
    }

    // Test entity class for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }
}
