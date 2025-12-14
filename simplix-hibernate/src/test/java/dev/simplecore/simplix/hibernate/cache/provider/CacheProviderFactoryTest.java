package dev.simplecore.simplix.hibernate.cache.provider;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for CacheProviderFactory.
 */
@DisplayName("CacheProviderFactory Tests")
class CacheProviderFactoryTest {

    @Nested
    @DisplayName("Constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw exception when LocalCacheProvider is missing")
        void shouldThrowExceptionWhenLocalCacheProviderIsMissing() {
            // Given
            List<CacheProvider> providers = List.of(new MockRedisProvider(true));

            // When/Then
            assertThatThrownBy(() -> new CacheProviderFactory(providers))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LocalCacheProvider not found");
        }

        @Test
        @DisplayName("Should initialize successfully with LocalCacheProvider")
        void shouldInitializeSuccessfullyWithLocalCacheProvider() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            List<CacheProvider> providers = List.of(localProvider);

            // When
            CacheProviderFactory factory = new CacheProviderFactory(providers);

            // Then
            assertThat(factory.getProvider("LOCAL")).isEqualTo(localProvider);
        }

        @Test
        @DisplayName("Should register all providers")
        void shouldRegisterAllProviders() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider redisProvider = new MockRedisProvider(true);
            List<CacheProvider> providers = Arrays.asList(localProvider, redisProvider);

            // When
            CacheProviderFactory factory = new CacheProviderFactory(providers);

            // Then
            assertThat(factory.isProviderAvailable("LOCAL")).isTrue();
            assertThat(factory.isProviderAvailable("REDIS")).isTrue();
        }
    }

    @Nested
    @DisplayName("getProvider() tests")
    class GetProviderTests {

        @Test
        @DisplayName("Should return provider by type")
        void shouldReturnProviderByType() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When
            CacheProvider result = factory.getProvider("LOCAL");

            // Then
            assertThat(result).isEqualTo(localProvider);
        }

        @Test
        @DisplayName("Should return best available when type is null")
        void shouldReturnBestAvailableWhenTypeIsNull() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When
            CacheProvider result = factory.getProvider(null);

            // Then
            assertThat(result).isEqualTo(localProvider);
        }

        @Test
        @DisplayName("Should return best available when type is empty")
        void shouldReturnBestAvailableWhenTypeIsEmpty() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When
            CacheProvider result = factory.getProvider("");

            // Then
            assertThat(result).isEqualTo(localProvider);
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void shouldBeCaseInsensitive() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When/Then
            assertThat(factory.getProvider("local")).isEqualTo(localProvider);
            assertThat(factory.getProvider("LOCAL")).isEqualTo(localProvider);
            assertThat(factory.getProvider("Local")).isEqualTo(localProvider);
        }

        @Test
        @DisplayName("Should fallback to best available when provider unavailable")
        void shouldFallbackToBestAvailableWhenProviderUnavailable() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider unavailableRedis = new MockRedisProvider(false);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, unavailableRedis));

            // When
            CacheProvider result = factory.getProvider("REDIS");

            // Then - should fallback to local since Redis is unavailable
            assertThat(result).isEqualTo(localProvider);
        }
    }

    @Nested
    @DisplayName("selectBestAvailable() tests")
    class SelectBestAvailableTests {

        @Test
        @DisplayName("Should prefer Redis over other providers")
        void shouldPreferRedisOverOtherProviders() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider redisProvider = new MockRedisProvider(true);
            MockHazelcastProvider hazelcastProvider = new MockHazelcastProvider(true);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, hazelcastProvider, redisProvider));

            // When
            CacheProvider result = factory.selectBestAvailable();

            // Then
            assertThat(result.getType()).isEqualTo("REDIS");
        }

        @Test
        @DisplayName("Should prefer Hazelcast when Redis unavailable")
        void shouldPreferHazelcastWhenRedisUnavailable() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider redisProvider = new MockRedisProvider(false);
            MockHazelcastProvider hazelcastProvider = new MockHazelcastProvider(true);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, hazelcastProvider, redisProvider));

            // When
            CacheProvider result = factory.selectBestAvailable();

            // Then
            assertThat(result.getType()).isEqualTo("HAZELCAST");
        }

        @Test
        @DisplayName("Should fallback to LOCAL when no distributed provider available")
        void shouldFallbackToLocalWhenNoDistributedProviderAvailable() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider redisProvider = new MockRedisProvider(false);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, redisProvider));

            // When
            CacheProvider result = factory.selectBestAvailable();

            // Then
            assertThat(result.getType()).isEqualTo("LOCAL");
        }
    }

    @Nested
    @DisplayName("getAvailableProviders() tests")
    class GetAvailableProvidersTests {

        @Test
        @DisplayName("Should return only available providers")
        void shouldReturnOnlyAvailableProviders() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider availableRedis = new MockRedisProvider(true);
            MockHazelcastProvider unavailableHazelcast = new MockHazelcastProvider(false);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, availableRedis, unavailableHazelcast));

            // When
            List<CacheProvider> result = factory.getAvailableProviders();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.stream().map(CacheProvider::getType))
                    .containsExactlyInAnyOrder("LOCAL", "REDIS");
        }
    }

    @Nested
    @DisplayName("isProviderAvailable() tests")
    class IsProviderAvailableTests {

        @Test
        @DisplayName("Should return true for available provider")
        void shouldReturnTrueForAvailableProvider() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When/Then
            assertThat(factory.isProviderAvailable("LOCAL")).isTrue();
        }

        @Test
        @DisplayName("Should return false for unavailable provider")
        void shouldReturnFalseForUnavailableProvider() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider unavailableRedis = new MockRedisProvider(false);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, unavailableRedis));

            // When/Then
            assertThat(factory.isProviderAvailable("REDIS")).isFalse();
        }

        @Test
        @DisplayName("Should return false for non-existent provider")
        void shouldReturnFalseForNonExistentProvider() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When/Then
            assertThat(factory.isProviderAvailable("NONEXISTENT")).isFalse();
        }

        @Test
        @DisplayName("Should return false for null type")
        void shouldReturnFalseForNullType() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When/Then
            assertThat(factory.isProviderAvailable(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty type")
        void shouldReturnFalseForEmptyType() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            CacheProviderFactory factory = new CacheProviderFactory(List.of(localProvider));

            // When/Then
            assertThat(factory.isProviderAvailable("")).isFalse();
        }
    }

    @Nested
    @DisplayName("getAllStats() tests")
    class GetAllStatsTests {

        @Test
        @DisplayName("Should return stats for available providers only")
        void shouldReturnStatsForAvailableProvidersOnly() {
            // Given
            LocalCacheProvider localProvider = new LocalCacheProvider();
            MockRedisProvider unavailableRedis = new MockRedisProvider(false);
            CacheProviderFactory factory = new CacheProviderFactory(
                    Arrays.asList(localProvider, unavailableRedis));

            // When
            Map<String, CacheProvider.CacheProviderStats> stats = factory.getAllStats();

            // Then
            assertThat(stats).containsKey("LOCAL");
            assertThat(stats).doesNotContainKey("REDIS");
        }
    }

    // Mock providers for testing

    private static class MockRedisProvider implements CacheProvider {
        private final boolean available;

        MockRedisProvider(boolean available) {
            this.available = available;
        }

        @Override
        public String getType() {
            return "REDIS";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void broadcastEviction(CacheEvictionEvent event) {
            // No-op
        }

        @Override
        public void subscribeToEvictions(CacheEvictionEventListener listener) {
            // No-op
        }

        @Override
        public void initialize() {
            // No-op
        }

        @Override
        public void shutdown() {
            // No-op
        }

        @Override
        public CacheProviderStats getStats() {
            return new CacheProviderStats(0, 0, available, "redis-node");
        }
    }

    private static class MockHazelcastProvider implements CacheProvider {
        private final boolean available;

        MockHazelcastProvider(boolean available) {
            this.available = available;
        }

        @Override
        public String getType() {
            return "HAZELCAST";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void broadcastEviction(CacheEvictionEvent event) {
            // No-op
        }

        @Override
        public void subscribeToEvictions(CacheEvictionEventListener listener) {
            // No-op
        }

        @Override
        public void initialize() {
            // No-op
        }

        @Override
        public void shutdown() {
            // No-op
        }

        @Override
        public CacheProviderStats getStats() {
            return new CacheProviderStats(0, 0, available, "hazelcast-node");
        }
    }
}
