package dev.simplecore.simplix.cache.strategy;

import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import dev.simplecore.simplix.cache.strategy.LocalCacheStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Local Cache Strategy Tests")
class LocalCacheStrategyTest {

    private LocalCacheStrategy cacheStrategy;
    private static final String CACHE_NAME = "testCache";

    @BeforeEach
    void setUp() {
        cacheStrategy = new LocalCacheStrategy();
        cacheStrategy.initialize();
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        assertThat(cacheStrategy.getName()).isEqualTo("LocalCacheStrategy");
    }

    @Test
    @DisplayName("Should store and retrieve value")
    void shouldStoreAndRetrieveValue() {
        String key = "key1";
        String value = "value1";

        cacheStrategy.put(CACHE_NAME, key, value);

        Optional<String> retrieved = cacheStrategy.get(CACHE_NAME, key, String.class);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(value);
    }

    @Test
    @DisplayName("Should return empty for missing key")
    void shouldReturnEmptyForMissingKey() {
        Optional<String> result = cacheStrategy.get(CACHE_NAME, "nonexistent", String.class);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should not store null values")
    void shouldNotStoreNullValues() {
        cacheStrategy.put(CACHE_NAME, "nullKey", null);

        Optional<String> result = cacheStrategy.get(CACHE_NAME, "nullKey", String.class);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should evict specific key")
    void shouldEvictSpecificKey() {
        cacheStrategy.put(CACHE_NAME, "key1", "value1");
        cacheStrategy.put(CACHE_NAME, "key2", "value2");

        cacheStrategy.evict(CACHE_NAME, "key1");

        assertThat(cacheStrategy.get(CACHE_NAME, "key1", String.class)).isEmpty();
        assertThat(cacheStrategy.get(CACHE_NAME, "key2", String.class)).isPresent();
    }

    @Test
    @DisplayName("Should evict multiple keys")
    void shouldEvictMultipleKeys() {
        cacheStrategy.put(CACHE_NAME, "key1", "value1");
        cacheStrategy.put(CACHE_NAME, "key2", "value2");
        cacheStrategy.put(CACHE_NAME, "key3", "value3");

        cacheStrategy.evictAll(CACHE_NAME, Arrays.asList("key1", "key3"));

        assertThat(cacheStrategy.exists(CACHE_NAME, "key1")).isFalse();
        assertThat(cacheStrategy.exists(CACHE_NAME, "key2")).isTrue();
        assertThat(cacheStrategy.exists(CACHE_NAME, "key3")).isFalse();
    }

    @Test
    @DisplayName("Should clear entire cache")
    void shouldClearEntireCache() {
        cacheStrategy.put(CACHE_NAME, "key1", "value1");
        cacheStrategy.put(CACHE_NAME, "key2", "value2");

        cacheStrategy.clear(CACHE_NAME);

        assertThat(cacheStrategy.getKeys(CACHE_NAME)).isEmpty();
    }

    @Test
    @DisplayName("Should compute value if absent")
    void shouldComputeValueIfAbsent() throws Exception {
        String key = "computeKey";
        String expectedValue = "computedValue";

        Callable<String> valueLoader = () -> expectedValue;

        String result = cacheStrategy.getOrCompute(CACHE_NAME, key, valueLoader, String.class);

        assertThat(result).isEqualTo(expectedValue);
        assertThat(cacheStrategy.get(CACHE_NAME, key, String.class)).contains(expectedValue);
    }

    @Test
    @DisplayName("Should not compute if value exists")
    void shouldNotComputeIfValueExists() throws Exception {
        String key = "existingKey";
        String existingValue = "existingValue";

        cacheStrategy.put(CACHE_NAME, key, existingValue);

        Callable<String> valueLoader = () -> {
            fail("Value loader should not be called");
            return "newValue";
        };

        String result = cacheStrategy.getOrCompute(CACHE_NAME, key, valueLoader, String.class);

        assertThat(result).isEqualTo(existingValue);
    }

    @Test
    @DisplayName("Should handle computation exception")
    void shouldHandleComputationException() {
        String key = "errorKey";

        Callable<String> failingLoader = () -> {
            throw new RuntimeException("Computation failed");
        };

        String result = cacheStrategy.getOrCompute(CACHE_NAME, key, failingLoader, String.class);

        assertThat(result).isNull();
        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isFalse();
    }

    @Test
    @DisplayName("Should check if key exists")
    void shouldCheckIfKeyExists() {
        String key = "existKey";

        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isFalse();

        cacheStrategy.put(CACHE_NAME, key, "value");

        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isTrue();
    }

    @Test
    @DisplayName("Should get all keys")
    void shouldGetAllKeys() {
        cacheStrategy.put(CACHE_NAME, "key1", "value1");
        cacheStrategy.put(CACHE_NAME, "key2", "value2");
        cacheStrategy.put(CACHE_NAME, "key3", "value3");

        Collection<Object> keys = cacheStrategy.getKeys(CACHE_NAME);

        assertThat(keys).containsExactlyInAnyOrder("key1", "key2", "key3");
    }

    @Test
    @DisplayName("Should get all entries")
    void shouldGetAllEntries() {
        cacheStrategy.put(CACHE_NAME, "key1", "value1");
        cacheStrategy.put(CACHE_NAME, "key2", "value2");

        Map<Object, String> entries = cacheStrategy.getAll(CACHE_NAME, String.class);

        assertThat(entries).hasSize(2);
        assertThat(entries.get("key1")).isEqualTo("value1");
        assertThat(entries.get("key2")).isEqualTo("value2");
    }

    @Test
    @DisplayName("Should put all entries")
    void shouldPutAllEntries() {
        Map<Object, String> entries = new HashMap<>();
        entries.put("key1", "value1");
        entries.put("key2", "value2");
        entries.put("key3", "value3");

        cacheStrategy.putAll(CACHE_NAME, entries);

        assertThat(cacheStrategy.get(CACHE_NAME, "key1", String.class)).contains("value1");
        assertThat(cacheStrategy.get(CACHE_NAME, "key2", String.class)).contains("value2");
        assertThat(cacheStrategy.get(CACHE_NAME, "key3", String.class)).contains("value3");
    }

    @Test
    @DisplayName("Should respect TTL")
    void shouldRespectTTL() throws InterruptedException {
        String key = "ttlKey";
        String value = "ttlValue";
        Duration ttl = Duration.ofMillis(100);

        cacheStrategy.put(CACHE_NAME, key, value, ttl);

        // Value should exist immediately
        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isTrue();

        // Wait for TTL to expire
        Thread.sleep(150);

        // Value should be expired
        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isFalse();
    }

    @Test
    @DisplayName("Should return statistics")
    void shouldReturnStatistics() {
        // Trigger some cache operations
        cacheStrategy.put(CACHE_NAME, "key1", "value1");
        cacheStrategy.get(CACHE_NAME, "key1", String.class); // Hit
        cacheStrategy.get(CACHE_NAME, "key2", String.class); // Miss

        CacheStrategy.CacheStatistics stats = cacheStrategy.getStatistics(CACHE_NAME);

        assertThat(stats).isNotNull();
        assertThat(stats.size()).isEqualTo(1);
        // Note: Hit/miss counts depend on Caffeine stats which may need recordStats() enabled
    }

    @Test
    @DisplayName("Should clear all caches")
    void shouldClearAllCaches() {
        String cache1 = "cache1";
        String cache2 = "cache2";

        cacheStrategy.put(cache1, "key1", "value1");
        cacheStrategy.put(cache2, "key2", "value2");

        cacheStrategy.clearAll();

        assertThat(cacheStrategy.exists(cache1, "key1")).isFalse();
        assertThat(cacheStrategy.exists(cache2, "key2")).isFalse();
    }

    @Test
    @DisplayName("Should always be available")
    void shouldAlwaysBeAvailable() {
        assertThat(cacheStrategy.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should handle shutdown gracefully")
    void shouldHandleShutdownGracefully() {
        cacheStrategy.put(CACHE_NAME, "key", "value");

        cacheStrategy.shutdown();

        // After shutdown, cache should be cleared
        assertThat(cacheStrategy.getKeys(CACHE_NAME)).isEmpty();
    }

    @Test
    @DisplayName("Should support different value types")
    void shouldSupportDifferentValueTypes() {
        // Integer
        cacheStrategy.put(CACHE_NAME, "intKey", 42);
        assertThat(cacheStrategy.get(CACHE_NAME, "intKey", Integer.class)).contains(42);

        // List
        List<String> list = Arrays.asList("a", "b", "c");
        cacheStrategy.put(CACHE_NAME, "listKey", list);
        assertThat(cacheStrategy.get(CACHE_NAME, "listKey", List.class)).contains(list);

        // Custom object
        TestObject obj = new TestObject("test", 123);
        cacheStrategy.put(CACHE_NAME, "objKey", obj);
        assertThat(cacheStrategy.get(CACHE_NAME, "objKey", TestObject.class)).contains(obj);
    }

    // Test helper class
    static class TestObject {
        String name;
        int value;

        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestObject that = (TestObject) o;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }
}