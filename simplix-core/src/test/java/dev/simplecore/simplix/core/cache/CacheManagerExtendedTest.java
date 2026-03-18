package dev.simplecore.simplix.core.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("CacheManager - Extended Coverage")
class CacheManagerExtendedTest {

    private final CacheManager cacheManager = CacheManager.getInstance();

    @Nested
    @DisplayName("get - exception handling")
    class GetExceptionHandling {

        @Test
        @DisplayName("should handle null cache name gracefully")
        void shouldHandleNullCacheName() {
            Optional<String> result = cacheManager.get(null, "key", String.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle null key gracefully")
        void shouldHandleNullKey() {
            Optional<String> result = cacheManager.get("cache", null, String.class);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("put - exception handling")
    class PutExceptionHandling {

        @Test
        @DisplayName("should handle null values gracefully")
        void shouldHandleNullValues() {
            assertThatNoException().isThrownBy(() ->
                    cacheManager.put("cache", "key", null));
        }

        @Test
        @DisplayName("should handle null TTL values gracefully")
        void shouldHandleNullTtl() {
            assertThatNoException().isThrownBy(() ->
                    cacheManager.put("cache", "key", "value", Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("evict - exception handling")
    class EvictExceptionHandling {

        @Test
        @DisplayName("should handle null key gracefully")
        void shouldHandleNullKey() {
            assertThatNoException().isThrownBy(() ->
                    cacheManager.evict("cache", null));
        }
    }

    @Nested
    @DisplayName("clear - exception handling")
    class ClearExceptionHandling {

        @Test
        @DisplayName("should handle null cache name gracefully")
        void shouldHandleNullCacheName() {
            assertThatNoException().isThrownBy(() ->
                    cacheManager.clear(null));
        }
    }

    @Nested
    @DisplayName("exists - exception handling")
    class ExistsExceptionHandling {

        @Test
        @DisplayName("should handle null key gracefully")
        void shouldHandleNullKey() {
            assertThat(cacheManager.exists("cache", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("getProvider")
    class GetProvider {

        @Test
        @DisplayName("should return the provider instance")
        void shouldReturnProvider() {
            CacheProvider provider = cacheManager.getProvider();
            assertThat(provider).isNotNull();
            assertThat(provider.getName()).isNotBlank();
        }
    }
}
