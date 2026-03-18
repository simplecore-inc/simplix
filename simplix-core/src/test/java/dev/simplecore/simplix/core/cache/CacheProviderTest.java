package dev.simplecore.simplix.core.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CacheProvider interface default methods")
class CacheProviderTest {

    static class MinimalProvider implements CacheProvider {
        @Override public <T> Optional<T> get(String cacheName, Object key, Class<T> type) { return Optional.empty(); }
        @Override public <T> void put(String cacheName, Object key, T value) {}
        @Override public <T> void put(String cacheName, Object key, T value, Duration ttl) {}
        @Override public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) { return null; }
        @Override public void evict(String cacheName, Object key) {}
        @Override public void clear(String cacheName) {}
        @Override public boolean exists(String cacheName, Object key) { return false; }
        @Override public boolean isAvailable() { return false; }
        @Override public String getName() { return "minimal"; }
    }

    @Test
    @DisplayName("getPriority should return 0 by default")
    void shouldReturnDefaultPriority() {
        MinimalProvider provider = new MinimalProvider();
        assertThat(provider.getPriority()).isEqualTo(0);
    }
}
