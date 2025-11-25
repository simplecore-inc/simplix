package dev.simplecore.simplix.cache.strategy;

import dev.simplecore.simplix.cache.config.CacheProperties;
import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import dev.simplecore.simplix.cache.strategy.RedisCacheStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Cache Strategy Tests")
class RedisCacheStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisServerCommands serverCommands;

    private RedisCacheStrategy cacheStrategy;
    private CacheProperties cacheProperties;
    private static final String CACHE_NAME = "testCache";

    @BeforeEach
    void setUp() {
        // Create CacheProperties with default settings (no prefix)
        cacheProperties = new CacheProperties();
        cacheProperties.getRedis().setUseKeyPrefix(false);

        // Mock Redis connection chain for initialize() and isAvailable()
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        cacheStrategy = new RedisCacheStrategy(redisTemplate, cacheProperties);
        cacheStrategy.initialize();
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        assertThat(cacheStrategy.getName()).isEqualTo("RedisCacheStrategy");
    }

    @Test
    @DisplayName("Should build cache key correctly")
    void shouldBuildCacheKeyCorrectly() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "key1";
        String expectedKey = CACHE_NAME + "::" + key;

        cacheStrategy.put(CACHE_NAME, key, "value");

        // put() without TTL defaults to Duration.ofHours(1)
        verify(valueOperations).set(eq(expectedKey), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should store and retrieve value")
    void shouldStoreAndRetrieveValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "key1";
        String value = "value1";
        String fullKey = CACHE_NAME + "::" + key;
        // JSON string for String.class deserialization
        String jsonValue = "\"value1\"";

        when(valueOperations.get(fullKey)).thenReturn(jsonValue);

        cacheStrategy.put(CACHE_NAME, key, value);

        Optional<String> retrieved = cacheStrategy.get(CACHE_NAME, key, String.class);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo("value1");
    }

    @Test
    @DisplayName("Should return empty for missing key")
    void shouldReturnEmptyForMissingKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String fullKey = CACHE_NAME + "::nonexistent";
        when(valueOperations.get(fullKey)).thenReturn(null);

        Optional<String> result = cacheStrategy.get(CACHE_NAME, "nonexistent", String.class);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should not store null values")
    void shouldNotStoreNullValues() {
        cacheStrategy.put(CACHE_NAME, "nullKey", null);

        verify(valueOperations, never()).set(anyString(), any());
    }

    @Test
    @DisplayName("Should set TTL when provided")
    void shouldSetTTLWhenProvided() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "key1";
        String value = "value1";
        Duration ttl = Duration.ofMinutes(5);
        String fullKey = CACHE_NAME + "::" + key;

        cacheStrategy.put(CACHE_NAME, key, value, ttl);

        // Duration.ofMinutes(5) = 300000 milliseconds
        verify(valueOperations).set(eq(fullKey), anyString(), eq(ttl));
    }

    @Test
    @DisplayName("Should evict specific key")
    void shouldEvictSpecificKey() {
        String key = "key1";
        String fullKey = CACHE_NAME + "::" + key;

        when(redisTemplate.delete(fullKey)).thenReturn(true);

        cacheStrategy.evict(CACHE_NAME, key);

        verify(redisTemplate).delete(fullKey);
    }

    @Test
    @DisplayName("Should evict multiple keys")
    void shouldEvictMultipleKeys() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");
        Set<String> fullKeys = new HashSet<>();
        for (String key : keys) {
            fullKeys.add(CACHE_NAME + "::" + key);
        }

        cacheStrategy.evictAll(CACHE_NAME, keys);

        verify(redisTemplate).delete(fullKeys);
    }

    @Test
    @DisplayName("Should clear entire cache")
    void shouldClearEntireCache() {
        String pattern = CACHE_NAME + "::*";
        Set<String> keys = new HashSet<>(Arrays.asList(
            CACHE_NAME + "::key1",
            CACHE_NAME + "::key2"
        ));

        when(redisTemplate.keys(pattern)).thenReturn(keys);

        cacheStrategy.clear(CACHE_NAME);

        verify(redisTemplate).delete(keys);
    }

    @Test
    @DisplayName("Should compute value if absent")
    void shouldComputeValueIfAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "computeKey";
        String fullKey = CACHE_NAME + "::" + key;
        String expectedValue = "computedValue";

        when(valueOperations.get(fullKey)).thenReturn(null);

        Callable<String> valueLoader = () -> expectedValue;

        String result = cacheStrategy.getOrCompute(CACHE_NAME, key, valueLoader, String.class);

        assertThat(result).isEqualTo(expectedValue);
        // getOrCompute() defaults to Duration.ofHours(1) TTL
        verify(valueOperations).set(eq(fullKey), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should not compute if value exists")
    void shouldNotComputeIfValueExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "existingKey";
        String fullKey = CACHE_NAME + "::" + key;
        // JSON string for String.class deserialization
        String existingValue = "\"existingValue\"";

        when(valueOperations.get(fullKey)).thenReturn(existingValue);

        Callable<String> valueLoader = () -> {
            throw new AssertionError("Value loader should not be called");
        };

        String result = cacheStrategy.getOrCompute(CACHE_NAME, key, valueLoader, String.class);

        assertThat(result).isEqualTo("existingValue");
    }

    @Test
    @DisplayName("Should handle computation exception")
    void shouldHandleComputationException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "errorKey";
        String fullKey = CACHE_NAME + "::" + key;

        when(valueOperations.get(fullKey)).thenReturn(null);

        Callable<String> failingLoader = () -> {
            throw new RuntimeException("Computation failed");
        };

        assertThatThrownBy(() ->
            cacheStrategy.getOrCompute(CACHE_NAME, key, failingLoader, String.class)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Cache value computation failed");

        verify(valueOperations, never()).set(anyString(), any());
    }

    @Test
    @DisplayName("Should check if key exists")
    void shouldCheckIfKeyExists() {
        String key = "existKey";
        String fullKey = CACHE_NAME + "::" + key;

        when(redisTemplate.hasKey(fullKey)).thenReturn(false, true);

        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isFalse();
        assertThat(cacheStrategy.exists(CACHE_NAME, key)).isTrue();
    }

    @Test
    @DisplayName("Should get all keys")
    void shouldGetAllKeys() {
        String pattern = CACHE_NAME + "::*";
        Set<String> fullKeys = new HashSet<>(Arrays.asList(
            CACHE_NAME + "::key1",
            CACHE_NAME + "::key2",
            CACHE_NAME + "::key3"
        ));

        when(redisTemplate.keys(pattern)).thenReturn(fullKeys);

        Collection<Object> keys = cacheStrategy.getKeys(CACHE_NAME);

        assertThat(keys).containsExactlyInAnyOrder("key1", "key2", "key3");
    }

    @Test
    @DisplayName("Should get all entries")
    void shouldGetAllEntries() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String pattern = CACHE_NAME + "::*";
        Set<String> fullKeys = new LinkedHashSet<>(Arrays.asList(
            CACHE_NAME + "::key1",
            CACHE_NAME + "::key2"
        ));
        List<String> values = Arrays.asList("\"value1\"", "\"value2\"");

        when(redisTemplate.keys(pattern)).thenReturn(fullKeys);
        when(valueOperations.multiGet(fullKeys)).thenReturn(values);

        Map<Object, String> entries = cacheStrategy.getAll(CACHE_NAME, String.class);

        assertThat(entries).hasSize(2);
        assertThat(entries).containsKeys("key1", "key2");
    }

    @Test
    @DisplayName("Should put all entries")
    void shouldPutAllEntries() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<Object, String> entries = new HashMap<>();
        entries.put("key1", "value1");
        entries.put("key2", "value2");

        cacheStrategy.putAll(CACHE_NAME, entries);

        // Verify multiSet was called with serialized entries
        verify(valueOperations).multiSet(anyMap());
        // Verify expire was called for each key (twice for 2 entries)
        verify(redisTemplate, times(2)).expire(anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("Should check availability based on Redis connection")
    void shouldCheckAvailabilityBasedOnRedisConnection() {
        // Test when Redis ping works (already configured in setUp)
        assertThat(cacheStrategy.isAvailable()).isTrue();

        // Test when Redis ping fails
        when(redisConnection.ping()).thenThrow(new RuntimeException("Connection failed"));
        assertThat(cacheStrategy.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Should return statistics")
    void shouldReturnStatistics() {
        String pattern = CACHE_NAME + "::*";
        Set<String> keys = new HashSet<>(Arrays.asList(
            CACHE_NAME + "::key1",
            CACHE_NAME + "::key2"
        ));

        Properties redisInfo = new Properties();
        redisInfo.setProperty("keyspace_hits", "100");
        redisInfo.setProperty("keyspace_misses", "50");
        redisInfo.setProperty("evicted_keys", "10");

        when(redisTemplate.keys(pattern)).thenReturn(keys);
        // Mock server commands for statistics
        when(redisConnection.serverCommands()).thenReturn(serverCommands);
        when(serverCommands.info("stats")).thenReturn(redisInfo);

        CacheStrategy.CacheStatistics stats = cacheStrategy.getStatistics(CACHE_NAME);

        assertThat(stats).isNotNull();
        assertThat(stats.size()).isEqualTo(2);
        assertThat(stats.hits()).isEqualTo(100);
        assertThat(stats.misses()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should handle shutdown gracefully")
    void shouldHandleShutdownGracefully() {
        cacheStrategy.shutdown();

        // After shutdown, operations should still work (Redis handles cleanup)
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Should apply Redis key prefix when configured")
    void shouldApplyRedisKeyPrefixWhenConfigured() {
        // Create new strategy with prefix enabled
        CacheProperties propsWithPrefix = new CacheProperties();
        propsWithPrefix.getRedis().setUseKeyPrefix(true);
        propsWithPrefix.getRedis().setKeyPrefix("myapp:");

        RedisCacheStrategy strategyWithPrefix = new RedisCacheStrategy(redisTemplate, propsWithPrefix);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "key1";
        String expectedKey = "myapp:" + CACHE_NAME + "::" + key;

        strategyWithPrefix.put(CACHE_NAME, key, "value");

        verify(valueOperations).set(eq(expectedKey), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should not apply Redis key prefix when disabled")
    void shouldNotApplyRedisKeyPrefixWhenDisabled() {
        // Already configured in setUp with useKeyPrefix=false
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String key = "key1";
        String expectedKey = CACHE_NAME + "::" + key;

        cacheStrategy.put(CACHE_NAME, key, "value");

        verify(valueOperations).set(eq(expectedKey), anyString(), any(Duration.class));
    }
}