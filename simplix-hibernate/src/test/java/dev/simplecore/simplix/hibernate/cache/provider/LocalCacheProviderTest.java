package dev.simplecore.simplix.hibernate.cache.provider;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LocalCacheProvider.
 */
@DisplayName("LocalCacheProvider Tests")
class LocalCacheProviderTest {

    private LocalCacheProvider localCacheProvider;

    @BeforeEach
    void setUp() {
        localCacheProvider = new LocalCacheProvider();
    }

    @Nested
    @DisplayName("getType() tests")
    class GetTypeTests {

        @Test
        @DisplayName("Should return LOCAL")
        void shouldReturnLocal() {
            assertThat(localCacheProvider.getType()).isEqualTo("LOCAL");
        }
    }

    @Nested
    @DisplayName("isAvailable() tests")
    class IsAvailableTests {

        @Test
        @DisplayName("Should always return true")
        void shouldAlwaysReturnTrue() {
            assertThat(localCacheProvider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should return true even after shutdown")
        void shouldReturnTrueEvenAfterShutdown() {
            // Given
            localCacheProvider.initialize();
            localCacheProvider.shutdown();

            // When/Then
            assertThat(localCacheProvider.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("broadcastEviction() tests")
    class BroadcastEvictionTests {

        @Test
        @DisplayName("Should not throw for any event")
        void shouldNotThrowForAnyEvent() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass("TestEntity")
                    .entityId("123")
                    .build();

            // When/Then - should not throw
            localCacheProvider.broadcastEviction(event);
            localCacheProvider.broadcastEviction(null);
        }
    }

    @Nested
    @DisplayName("subscribeToEvictions() tests")
    class SubscribeToEvictionsTests {

        @Test
        @DisplayName("Should not throw for any listener")
        void shouldNotThrowForAnyListener() {
            // When/Then - should not throw
            localCacheProvider.subscribeToEvictions(event -> {});
            localCacheProvider.subscribeToEvictions(null);
        }
    }

    @Nested
    @DisplayName("initialize() and shutdown() tests")
    class InitializeShutdownTests {

        @Test
        @DisplayName("initialize() should be idempotent")
        void initializeShouldBeIdempotent() {
            // When - multiple initializations
            localCacheProvider.initialize();
            localCacheProvider.initialize();
            localCacheProvider.initialize();

            // Then - should not throw and still be available
            assertThat(localCacheProvider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("shutdown() should be idempotent")
        void shutdownShouldBeIdempotent() {
            // Given
            localCacheProvider.initialize();

            // When - multiple shutdowns
            localCacheProvider.shutdown();
            localCacheProvider.shutdown();
            localCacheProvider.shutdown();

            // Then - should not throw and still be available
            assertThat(localCacheProvider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("shutdown() without initialize() should not throw")
        void shutdownWithoutInitializeShouldNotThrow() {
            // When/Then - should not throw
            localCacheProvider.shutdown();
        }

        @Test
        @DisplayName("Can re-initialize after shutdown")
        void canReInitializeAfterShutdown() {
            // Given
            localCacheProvider.initialize();
            localCacheProvider.shutdown();

            // When
            localCacheProvider.initialize();

            // Then
            assertThat(localCacheProvider.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("getStats() tests")
    class GetStatsTests {

        @Test
        @DisplayName("Should return zero counts for local provider")
        void shouldReturnZeroCountsForLocalProvider() {
            // When
            CacheProvider.CacheProviderStats stats = localCacheProvider.getStats();

            // Then
            assertThat(stats.evictionsSent()).isZero();
            assertThat(stats.evictionsReceived()).isZero();
            assertThat(stats.connected()).isTrue();
            assertThat(stats.nodeId()).isEqualTo("local");
        }
    }
}
