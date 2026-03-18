package dev.simplecore.simplix.cache.provider;

import dev.simplecore.simplix.cache.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("CoreCacheProviderImpl")
@ExtendWith(MockitoExtension.class)
class CoreCacheProviderImplTest {

    @Mock
    private CacheService cacheService;

    private CoreCacheProviderImpl provider;

    @BeforeEach
    void setUp() {
        when(cacheService.getStrategyName()).thenReturn("local");
        provider = new CoreCacheProviderImpl(cacheService);
    }

    @Nested
    @DisplayName("get")
    class GetTests {

        @Test
        @DisplayName("should delegate get to CacheService")
        void shouldDelegateGet() {
            when(cacheService.get("users", "key1", String.class))
                    .thenReturn(Optional.of("value1"));

            Optional<String> result = provider.get("users", "key1", String.class);

            assertThat(result).isPresent().contains("value1");
            verify(cacheService).get("users", "key1", String.class);
        }

        @Test
        @DisplayName("should return empty when key not found")
        void shouldReturnEmptyWhenNotFound() {
            when(cacheService.get("users", "missing", String.class))
                    .thenReturn(Optional.empty());

            Optional<String> result = provider.get("users", "missing", String.class);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("put")
    class PutTests {

        @Test
        @DisplayName("should delegate put to CacheService")
        void shouldDelegatePut() {
            provider.put("users", "key1", "value1");
            verify(cacheService).put("users", "key1", "value1");
        }

        @Test
        @DisplayName("should delegate put with TTL to CacheService")
        void shouldDelegatePutWithTtl() {
            Duration ttl = Duration.ofMinutes(5);
            provider.put("users", "key1", "value1", ttl);
            verify(cacheService).put("users", "key1", "value1", ttl);
        }
    }

    @Nested
    @DisplayName("getOrCompute")
    class GetOrComputeTests {

        @Test
        @DisplayName("should delegate getOrCompute to CacheService")
        void shouldDelegateGetOrCompute() {
            Callable<String> loader = () -> "computed";
            when(cacheService.getOrCompute("users", "key1", loader, String.class))
                    .thenReturn("computed");

            String result = provider.getOrCompute("users", "key1", loader, String.class);

            assertThat(result).isEqualTo("computed");
            verify(cacheService).getOrCompute("users", "key1", loader, String.class);
        }
    }

    @Nested
    @DisplayName("evict")
    class EvictTests {

        @Test
        @DisplayName("should delegate evict to CacheService")
        void shouldDelegateEvict() {
            provider.evict("users", "key1");
            verify(cacheService).evict("users", "key1");
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("should delegate clear to CacheService")
        void shouldDelegateClear() {
            provider.clear("users");
            verify(cacheService).clear("users");
        }
    }

    @Nested
    @DisplayName("exists")
    class ExistsTests {

        @Test
        @DisplayName("should delegate exists to CacheService")
        void shouldDelegateExists() {
            when(cacheService.exists("users", "key1")).thenReturn(true);

            boolean result = provider.exists("users", "key1");

            assertThat(result).isTrue();
            verify(cacheService).exists("users", "key1");
        }

        @Test
        @DisplayName("should return false when key does not exist")
        void shouldReturnFalseWhenNotExist() {
            when(cacheService.exists("users", "missing")).thenReturn(false);

            boolean result = provider.exists("users", "missing");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailableTests {

        @Test
        @DisplayName("should delegate isAvailable to CacheService")
        void shouldDelegateIsAvailable() {
            when(cacheService.isAvailable()).thenReturn(true);

            assertThat(provider.isAvailable()).isTrue();
            verify(cacheService).isAvailable();
        }
    }

    @Nested
    @DisplayName("getName")
    class GetNameTests {

        @Test
        @DisplayName("should return name with strategy prefix")
        void shouldReturnNameWithPrefix() {
            when(cacheService.getStrategyName()).thenReturn("local");

            assertThat(provider.getName()).isEqualTo("CacheModule-local");
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriorityTests {

        @Test
        @DisplayName("should return priority of 100")
        void shouldReturnHighPriority() {
            assertThat(provider.getPriority()).isEqualTo(100);
        }
    }
}
