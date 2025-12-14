package dev.simplecore.simplix.hibernate.cache.strategy;

import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CacheEvictionStrategy.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheEvictionStrategy Tests")
class CacheEvictionStrategyTest {

    @Mock
    private HibernateCacheManager cacheManager;

    private CacheEvictionStrategy evictionStrategy;

    @BeforeEach
    void setUp() {
        evictionStrategy = new CacheEvictionStrategy(cacheManager);
    }

    @Nested
    @DisplayName("evict(Class, Object) tests")
    class EvictByClassTests {

        @Test
        @DisplayName("Should evict specific entity")
        void shouldEvictSpecificEntity() {
            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then
            verify(cacheManager).evictEntity(TestEntity.class, 123L);
        }

        @Test
        @DisplayName("Should evict entire entity cache when entityId is null")
        void shouldEvictEntireEntityCacheWhenEntityIdIsNull() {
            // When
            evictionStrategy.evict(TestEntity.class, null);

            // Then
            verify(cacheManager).evictEntityCache(TestEntity.class);
        }

        @Test
        @DisplayName("Should skip eviction when entityClass is null")
        void shouldSkipEvictionWhenEntityClassIsNull() {
            // When
            evictionStrategy.evict((Class<?>) null, 123L);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
            verify(cacheManager, never()).evictEntityCache(any());
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            doThrow(new RuntimeException("Cache error"))
                    .when(cacheManager).evictEntity(any(), any());

            // When/Then - should not throw
            evictionStrategy.evict(TestEntity.class, 123L);
        }
    }

    @Nested
    @DisplayName("evict(String, Object) tests")
    class EvictByClassNameTests {

        @Test
        @DisplayName("Should evict by class name")
        void shouldEvictByClassName() {
            // When
            evictionStrategy.evict(TestEntity.class.getName(), 123L);

            // Then
            verify(cacheManager).evictEntity(TestEntity.class, 123L);
        }

        @Test
        @DisplayName("Should evict entire cache by class name when entityId is null")
        void shouldEvictEntireCacheByClassNameWhenEntityIdIsNull() {
            // When
            evictionStrategy.evict(TestEntity.class.getName(), null);

            // Then
            verify(cacheManager).evictEntityCache(TestEntity.class);
        }

        @Test
        @DisplayName("Should skip eviction when class name is null")
        void shouldSkipEvictionWhenClassNameIsNull() {
            // When
            evictionStrategy.evict((String) null, 123L);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
            verify(cacheManager, never()).evictEntityCache(any());
        }

        @Test
        @DisplayName("Should skip eviction when class name is empty")
        void shouldSkipEvictionWhenClassNameIsEmpty() {
            // When
            evictionStrategy.evict("", 123L);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
            verify(cacheManager, never()).evictEntityCache(any());
        }

        @Test
        @DisplayName("Should handle unknown class gracefully")
        void shouldHandleUnknownClassGracefully() {
            // When
            evictionStrategy.evict("com.nonexistent.FakeClass", 123L);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
            verify(cacheManager, never()).evictEntityCache(any());
        }
    }

    // Test entity class for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }
}
