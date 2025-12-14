package dev.simplecore.simplix.hibernate.cache.core;

import jakarta.persistence.Cache;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for HibernateCacheManager.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HibernateCacheManager Tests")
class HibernateCacheManagerTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private Cache jpaCache;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private org.hibernate.Cache hibernateCache;

    private HibernateCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        lenient().when(entityManagerFactory.getCache()).thenReturn(jpaCache);
        lenient().when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
        lenient().when(sessionFactory.getCache()).thenReturn(hibernateCache);

        cacheManager = new HibernateCacheManager(entityManagerFactory);
    }

    @Nested
    @DisplayName("evictEntityCache() tests")
    class EvictEntityCacheTests {

        @Test
        @DisplayName("Should evict entity cache for given class")
        void shouldEvictEntityCacheForGivenClass() {
            // When
            cacheManager.evictEntityCache(TestEntity.class);

            // Then
            verify(jpaCache).evict(TestEntity.class);
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            doThrow(new RuntimeException("Cache error"))
                    .when(jpaCache).evict(any(Class.class));

            // When/Then - should not throw
            cacheManager.evictEntityCache(TestEntity.class);
        }
    }

    @Nested
    @DisplayName("evictEntity() tests")
    class EvictEntityTests {

        @Test
        @DisplayName("Should evict specific entity from cache")
        void shouldEvictSpecificEntityFromCache() {
            // Given
            Long entityId = 123L;

            // When
            cacheManager.evictEntity(TestEntity.class, entityId);

            // Then
            verify(jpaCache).evict(TestEntity.class, entityId);
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            doThrow(new RuntimeException("Cache error"))
                    .when(jpaCache).evict(any(Class.class), any());

            // When/Then - should not throw
            cacheManager.evictEntity(TestEntity.class, 123L);
        }
    }

    @Nested
    @DisplayName("evictAll() tests")
    class EvictAllTests {

        @Test
        @DisplayName("Should evict all caches")
        void shouldEvictAllCaches() {
            // When
            cacheManager.evictAll();

            // Then
            verify(jpaCache).evictAll();
            verify(hibernateCache).evictAllRegions();
        }

        @Test
        @DisplayName("Should handle null hibernate cache gracefully")
        void shouldHandleNullHibernateCacheGracefully() {
            // Given
            when(sessionFactory.getCache()).thenReturn(null);

            // When/Then - should not throw
            cacheManager.evictAll();
            verify(jpaCache).evictAll();
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            doThrow(new RuntimeException("Cache error"))
                    .when(jpaCache).evictAll();

            // When/Then - should not throw
            cacheManager.evictAll();
        }
    }

    @Nested
    @DisplayName("evictRegion() tests")
    class EvictRegionTests {

        @Test
        @DisplayName("Should evict specific region")
        void shouldEvictSpecificRegion() {
            // Given
            String regionName = "com.example.TestEntity";

            // When
            cacheManager.evictRegion(regionName);

            // Then
            verify(hibernateCache).evictRegion(regionName);
        }

        @Test
        @DisplayName("Should handle null session factory gracefully")
        void shouldHandleNullSessionFactoryGracefully() {
            // Given
            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(null);

            // When/Then - should not throw
            cacheManager.evictRegion("test-region");
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            doThrow(new RuntimeException("Cache error"))
                    .when(hibernateCache).evictRegion(anyString());

            // When/Then - should not throw
            cacheManager.evictRegion("test-region");
        }
    }

    @Nested
    @DisplayName("evictQueryRegion() tests")
    class EvictQueryRegionTests {

        @Test
        @DisplayName("Should evict query region")
        void shouldEvictQueryRegion() {
            // Given
            String queryRegion = "query.findAllUsers";

            // When
            cacheManager.evictQueryRegion(queryRegion);

            // Then
            verify(hibernateCache).evictQueryRegion(queryRegion);
        }

        @Test
        @DisplayName("Should handle exception gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            doThrow(new RuntimeException("Cache error"))
                    .when(hibernateCache).evictQueryRegion(anyString());

            // When/Then - should not throw
            cacheManager.evictQueryRegion("test-query");
        }
    }

    @Nested
    @DisplayName("contains() tests")
    class ContainsTests {

        @Test
        @DisplayName("Should return true when entity is in cache")
        void shouldReturnTrueWhenEntityIsInCache() {
            // Given
            when(jpaCache.contains(TestEntity.class, 123L)).thenReturn(true);

            // When
            boolean result = cacheManager.contains(TestEntity.class, 123L);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when entity is not in cache")
        void shouldReturnFalseWhenEntityIsNotInCache() {
            // Given
            when(jpaCache.contains(TestEntity.class, 123L)).thenReturn(false);

            // When
            boolean result = cacheManager.contains(TestEntity.class, 123L);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false on exception")
        void shouldReturnFalseOnException() {
            // Given
            when(jpaCache.contains(any(Class.class), any()))
                    .thenThrow(new RuntimeException("Cache error"));

            // When
            boolean result = cacheManager.contains(TestEntity.class, 123L);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("registerRegion() and getActiveRegions() tests")
    class RegionManagementTests {

        @Test
        @DisplayName("Should register and return active regions")
        void shouldRegisterAndReturnActiveRegions() {
            // When
            cacheManager.registerRegion("region1");
            cacheManager.registerRegion("region2");

            // Then
            Set<String> regions = cacheManager.getActiveRegions();
            assertThat(regions).containsExactlyInAnyOrder("region1", "region2");
        }

        @Test
        @DisplayName("Should return empty set when no regions registered")
        void shouldReturnEmptySetWhenNoRegionsRegistered() {
            // When
            Set<String> regions = cacheManager.getActiveRegions();

            // Then
            assertThat(regions).isEmpty();
        }

        @Test
        @DisplayName("Should not add duplicate regions")
        void shouldNotAddDuplicateRegions() {
            // When
            cacheManager.registerRegion("region1");
            cacheManager.registerRegion("region1");

            // Then
            Set<String> regions = cacheManager.getActiveRegions();
            assertThat(regions).hasSize(1);
        }

        @Test
        @DisplayName("getActiveRegions should return immutable copy")
        void getActiveRegionsShouldReturnImmutableCopy() {
            // Given
            cacheManager.registerRegion("region1");

            // When
            Set<String> regions = cacheManager.getActiveRegions();

            // Then - modifying returned set should not affect internal state
            try {
                regions.add("newRegion");
            } catch (UnsupportedOperationException expected) {
                // Expected for immutable set
            }
            assertThat(cacheManager.getActiveRegions()).hasSize(1);
        }
    }

    // Test entity class for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }
}
