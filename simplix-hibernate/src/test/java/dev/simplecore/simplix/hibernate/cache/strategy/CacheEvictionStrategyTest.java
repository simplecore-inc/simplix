package dev.simplecore.simplix.hibernate.cache.strategy;

import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheProperties;
import dev.simplecore.simplix.hibernate.cache.core.CacheMode;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private CacheProviderFactory providerFactory;

    @Mock
    private CacheProvider mockProvider;

    private HibernateCacheProperties properties;
    private CacheEvictionStrategy evictionStrategy;

    @BeforeEach
    void setUp() {
        properties = new HibernateCacheProperties();
        properties.setNodeId("test-node");
        properties.setMode(CacheMode.AUTO);

        evictionStrategy = new CacheEvictionStrategy(cacheManager, providerFactory, properties);
    }

    @Nested
    @DisplayName("initialize() tests")
    class InitializeTests {

        @Test
        @DisplayName("Should initialize with available provider")
        void shouldInitializeWithAvailableProvider() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("LOCAL");

            // When
            evictionStrategy.initialize();

            // Then
            verify(mockProvider).initialize();
            verify(mockProvider).subscribeToEvictions(evictionStrategy);
        }

        @Test
        @DisplayName("Should handle null provider gracefully")
        void shouldHandleNullProviderGracefully() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(null);

            // When/Then - should not throw
            evictionStrategy.initialize();
        }
    }

    @Nested
    @DisplayName("evict() tests - LOCAL mode")
    class EvictLocalModeTests {

        @BeforeEach
        void setUpLocalMode() {
            properties.setMode(CacheMode.LOCAL);
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("LOCAL");
            evictionStrategy.initialize();
        }

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
        @DisplayName("Should not broadcast in LOCAL mode")
        void shouldNotBroadcastInLocalMode() {
            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then
            verify(mockProvider, never()).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should skip eviction when entityClass is null")
        void shouldSkipEvictionWhenEntityClassIsNull() {
            // When
            evictionStrategy.evict(null, 123L);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
            verify(cacheManager, never()).evictEntityCache(any());
        }
    }

    @Nested
    @DisplayName("evict() tests - DISTRIBUTED mode")
    class EvictDistributedModeTests {

        @BeforeEach
        void setUpDistributedMode() {
            properties.setMode(CacheMode.DISTRIBUTED);
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("REDIS");
            when(mockProvider.isAvailable()).thenReturn(true);
            evictionStrategy.initialize();
        }

        @Test
        @DisplayName("Should evict locally and broadcast")
        void shouldEvictLocallyAndBroadcast() {
            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then
            verify(cacheManager).evictEntity(TestEntity.class, 123L);
            verify(mockProvider).broadcastEviction(any(CacheEvictionEvent.class));
        }

        @Test
        @DisplayName("Should continue broadcast even if local eviction fails")
        void shouldContinueBroadcastEvenIfLocalEvictionFails() {
            // Given
            doThrow(new RuntimeException("Local eviction failed"))
                    .when(cacheManager).evictEntity(any(), any());

            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then - broadcast should still be attempted
            verify(mockProvider).broadcastEviction(any(CacheEvictionEvent.class));
        }
    }

    @Nested
    @DisplayName("evict() tests - DISABLED mode")
    class EvictDisabledModeTests {

        @BeforeEach
        void setUpDisabledMode() {
            properties.setMode(CacheMode.DISABLED);
            evictionStrategy = new CacheEvictionStrategy(cacheManager, providerFactory, properties);
        }

        @Test
        @DisplayName("Should skip all eviction in DISABLED mode")
        void shouldSkipAllEvictionInDisabledMode() {
            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
            verify(cacheManager, never()).evictEntityCache(any());
        }
    }

    @Nested
    @DisplayName("evict() tests - AUTO mode")
    class EvictAutoModeTests {

        @Test
        @DisplayName("AUTO should resolve to LOCAL when provider is LOCAL")
        void autoShouldResolveToLocalWhenProviderIsLocal() {
            // Given
            properties.setMode(CacheMode.AUTO);
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("LOCAL");
            evictionStrategy.initialize();

            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then - should only evict locally
            verify(cacheManager).evictEntity(TestEntity.class, 123L);
            verify(mockProvider, never()).broadcastEviction(any());
        }

        @Test
        @DisplayName("AUTO should resolve to DISTRIBUTED when provider is REDIS")
        void autoShouldResolveToDistributedWhenProviderIsRedis() {
            // Given
            properties.setMode(CacheMode.AUTO);
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("REDIS");
            when(mockProvider.isAvailable()).thenReturn(true);
            evictionStrategy.initialize();

            // When
            evictionStrategy.evict(TestEntity.class, 123L);

            // Then - should evict locally and broadcast
            verify(cacheManager).evictEntity(TestEntity.class, 123L);
            verify(mockProvider).broadcastEviction(any(CacheEvictionEvent.class));
        }
    }

    @Nested
    @DisplayName("onEvictionEvent() tests")
    class OnEvictionEventTests {

        @BeforeEach
        void setUpProvider() {
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("REDIS");
            evictionStrategy.initialize();
        }

        @Test
        @DisplayName("Should process remote eviction event")
        void shouldProcessRemoteEvictionEvent() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass(TestEntity.class.getName())
                    .entityId("123")
                    .nodeId("other-node") // Different from test-node
                    .build();

            // When
            evictionStrategy.onEvictionEvent(event);

            // Then
            verify(cacheManager).evictEntity(TestEntity.class, "123");
        }

        @Test
        @DisplayName("Should ignore own events")
        void shouldIgnoreOwnEvents() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass(TestEntity.class.getName())
                    .entityId("123")
                    .nodeId("test-node") // Same as our node ID
                    .build();

            // When
            evictionStrategy.onEvictionEvent(event);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
        }

        @Test
        @DisplayName("Should evict entire cache when entityId is null")
        void shouldEvictEntireCacheWhenEntityIdIsNull() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass(TestEntity.class.getName())
                    .entityId(null)
                    .nodeId("other-node")
                    .build();

            // When
            evictionStrategy.onEvictionEvent(event);

            // Then
            verify(cacheManager).evictEntityCache(TestEntity.class);
        }

        @Test
        @DisplayName("Should evict region when specified")
        void shouldEvictRegionWhenSpecified() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass(TestEntity.class.getName())
                    .entityId("123")
                    .region("test-region")
                    .nodeId("other-node")
                    .build();

            // When
            evictionStrategy.onEvictionEvent(event);

            // Then
            verify(cacheManager).evictRegion("test-region");
        }

        @Test
        @DisplayName("Should handle unknown entity class with full cache eviction")
        void shouldHandleUnknownEntityClassWithFullCacheEviction() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass("com.nonexistent.FakeClass")
                    .entityId("123")
                    .nodeId("other-node")
                    .build();

            // When
            evictionStrategy.onEvictionEvent(event);

            // Then - fallback to full cache eviction
            verify(cacheManager).evictAll();
        }

        @Test
        @DisplayName("Should skip event with null entityClass")
        void shouldSkipEventWithNullEntityClass() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass(null)
                    .entityId("123")
                    .nodeId("other-node")
                    .build();

            // When
            evictionStrategy.onEvictionEvent(event);

            // Then
            verify(cacheManager, never()).evictEntity(any(), any());
        }
    }

    @Nested
    @DisplayName("shutdown() tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should shutdown provider")
        void shouldShutdownProvider() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("LOCAL");
            evictionStrategy.initialize();

            // When
            evictionStrategy.shutdown();

            // Then
            verify(mockProvider).shutdown();
        }

        @Test
        @DisplayName("shutdown() should be idempotent")
        void shutdownShouldBeIdempotent() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("LOCAL");
            evictionStrategy.initialize();

            // When
            evictionStrategy.shutdown();
            evictionStrategy.shutdown();

            // Then - should only shutdown once
            verify(mockProvider, times(1)).shutdown();
        }
    }

    @Nested
    @DisplayName("getActiveProviderInfo() tests")
    class GetActiveProviderInfoTests {

        @Test
        @DisplayName("Should return provider info when available")
        void shouldReturnProviderInfoWhenAvailable() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.getType()).thenReturn("REDIS");
            when(mockProvider.getStats()).thenReturn(
                    new CacheProvider.CacheProviderStats(10, 5, true, "test-node"));
            evictionStrategy.initialize();

            // When
            String info = evictionStrategy.getActiveProviderInfo();

            // Then
            assertThat(info).contains("REDIS");
            assertThat(info).contains("Connected: true");
        }

        @Test
        @DisplayName("Should return 'No active provider' when no provider")
        void shouldReturnNoActiveProviderWhenNoProvider() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(null);
            evictionStrategy.initialize();

            // When
            String info = evictionStrategy.getActiveProviderInfo();

            // Then
            assertThat(info).isEqualTo("No active provider");
        }
    }

    // Test entity class for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }
}
