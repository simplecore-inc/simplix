package dev.simplecore.simplix.core.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("CacheManager")
class CacheManagerTest {

    @Nested
    @DisplayName("getInstance")
    class GetInstance {

        @Test
        @DisplayName("should return singleton instance")
        void shouldReturnSingletonInstance() {
            CacheManager instance1 = CacheManager.getInstance();
            CacheManager instance2 = CacheManager.getInstance();

            assertThat(instance1).isSameAs(instance2);
        }

        @Test
        @DisplayName("should return non-null instance")
        void shouldReturnNonNullInstance() {
            assertThat(CacheManager.getInstance()).isNotNull();
        }
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("should return empty Optional from NoOp provider")
        void shouldReturnEmptyFromNoOp() {
            Optional<String> result = CacheManager.getInstance().get("cache", "key", String.class);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("put")
    class Put {

        @Test
        @DisplayName("should not throw on put")
        void shouldNotThrowOnPut() {
            assertThatNoException().isThrownBy(() ->
                CacheManager.getInstance().put("cache", "key", "value")
            );
        }

        @Test
        @DisplayName("should not throw on put with TTL")
        void shouldNotThrowOnPutWithTtl() {
            assertThatNoException().isThrownBy(() ->
                CacheManager.getInstance().put("cache", "key", "value", Duration.ofMinutes(5))
            );
        }
    }

    @Nested
    @DisplayName("getOrCompute")
    class GetOrCompute {

        @Test
        @DisplayName("should call value loader with NoOp provider")
        void shouldCallValueLoader() {
            String result = CacheManager.getInstance().getOrCompute(
                "cache", "key", () -> "computed-value", String.class
            );

            assertThat(result).isEqualTo("computed-value");
        }

        @Test
        @DisplayName("should return null when value loader throws")
        void shouldReturnNullWhenLoaderThrows() {
            String result = CacheManager.getInstance().getOrCompute(
                "cache", "key", () -> { throw new RuntimeException("loader error"); }, String.class
            );

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("evict")
    class Evict {

        @Test
        @DisplayName("should not throw on evict")
        void shouldNotThrowOnEvict() {
            assertThatNoException().isThrownBy(() ->
                CacheManager.getInstance().evict("cache", "key")
            );
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should not throw on clear")
        void shouldNotThrowOnClear() {
            assertThatNoException().isThrownBy(() ->
                CacheManager.getInstance().clear("cache")
            );
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("should return false from NoOp provider")
        void shouldReturnFalseFromNoOp() {
            assertThat(CacheManager.getInstance().exists("cache", "key")).isFalse();
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("should return true even with NoOp provider")
        void shouldReturnTrueWithNoOp() {
            assertThat(CacheManager.getInstance().isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("getProviderName")
    class GetProviderName {

        @Test
        @DisplayName("should return provider name")
        void shouldReturnProviderName() {
            String name = CacheManager.getInstance().getProviderName();

            assertThat(name).isNotNull().isNotBlank();
        }
    }
}
