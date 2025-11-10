package dev.simplecore.simplix.cache.service;

import dev.simplecore.simplix.cache.service.CacheService;
import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cache Service Tests")
class CacheServiceTest {

    @Mock
    private CacheStrategy cacheStrategy;

    private CacheService cacheService;
    private static final String CACHE_NAME = "testCache";

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(cacheStrategy);
    }

    @Test
    @DisplayName("Should delegate get to strategy")
    void shouldDelegateGetToStrategy() {
        String key = "key1";
        String value = "value1";

        when(cacheStrategy.get(CACHE_NAME, key, String.class))
            .thenReturn(Optional.of(value));

        Optional<String> result = cacheService.get(CACHE_NAME, key, String.class);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(value);
        verify(cacheStrategy).get(CACHE_NAME, key, String.class);
    }

    @Test
    @DisplayName("Should delegate put to strategy")
    void shouldDelegatePutToStrategy() {
        String key = "key1";
        String value = "value1";

        cacheService.put(CACHE_NAME, key, value);

        verify(cacheStrategy).put(CACHE_NAME, key, value);
    }

    @Test
    @DisplayName("Should delegate put with TTL to strategy")
    void shouldDelegatePutWithTTLToStrategy() {
        String key = "key1";
        String value = "value1";
        Duration ttl = Duration.ofHours(1);

        cacheService.put(CACHE_NAME, key, value, ttl);

        verify(cacheStrategy).put(CACHE_NAME, key, value, ttl);
    }

    @Test
    @DisplayName("Should delegate getOrCompute to strategy")
    void shouldDelegateGetOrComputeToStrategy() throws Exception {
        String key = "key1";
        String value = "computedValue";
        Callable<String> valueLoader = () -> value;

        when(cacheStrategy.<String>getOrCompute(eq(CACHE_NAME), eq(key), any(), eq(String.class)))
            .thenReturn(value);

        String result = cacheService.getOrCompute(CACHE_NAME, key, valueLoader, String.class);

        assertThat(result).isEqualTo(value);
        verify(cacheStrategy).getOrCompute(eq(CACHE_NAME), eq(key), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should delegate getOrCompute with TTL to strategy")
    void shouldDelegateGetOrComputeWithTTLToStrategy() throws Exception {
        String key = "key1";
        String value = "computedValue";
        Duration ttl = Duration.ofMinutes(30);
        Callable<String> valueLoader = () -> value;

        when(cacheStrategy.<String>getOrCompute(eq(CACHE_NAME), eq(key), any(), eq(String.class), eq(ttl)))
            .thenReturn(value);

        String result = cacheService.getOrCompute(CACHE_NAME, key, valueLoader, String.class, ttl);

        assertThat(result).isEqualTo(value);
        verify(cacheStrategy).getOrCompute(eq(CACHE_NAME), eq(key), any(), eq(String.class), eq(ttl));
    }

    @Test
    @DisplayName("Should delegate evict to strategy")
    void shouldDelegateEvictToStrategy() {
        String key = "key1";

        cacheService.evict(CACHE_NAME, key);

        verify(cacheStrategy).evict(CACHE_NAME, key);
    }

    @Test
    @DisplayName("Should delegate evictAll to strategy")
    void shouldDelegateEvictAllToStrategy() {
        Collection<String> keys = Arrays.asList("key1", "key2", "key3");

        cacheService.evictAll(CACHE_NAME, keys);

        verify(cacheStrategy).evictAll(CACHE_NAME, keys);
    }

    @Test
    @DisplayName("Should delegate clear to strategy")
    void shouldDelegateClearToStrategy() {
        cacheService.clear(CACHE_NAME);

        verify(cacheStrategy).clear(CACHE_NAME);
    }

    @Test
    @DisplayName("Should delegate clearAll to strategy")
    void shouldDelegateClearAllToStrategy() {
        cacheService.clearAll();

        verify(cacheStrategy).clearAll();
    }

    @Test
    @DisplayName("Should delegate exists to strategy")
    void shouldDelegateExistsToStrategy() {
        String key = "key1";

        when(cacheStrategy.exists(CACHE_NAME, key)).thenReturn(true);

        boolean result = cacheService.exists(CACHE_NAME, key);

        assertThat(result).isTrue();
        verify(cacheStrategy).exists(CACHE_NAME, key);
    }

    @Test
    @DisplayName("Should delegate getKeys to strategy")
    void shouldDelegateGetKeysToStrategy() {
        Collection<Object> keys = Arrays.asList("key1", "key2", "key3");

        when(cacheStrategy.getKeys(CACHE_NAME)).thenReturn(keys);

        Collection<Object> result = cacheService.getKeys(CACHE_NAME);

        assertThat(result).isEqualTo(keys);
        verify(cacheStrategy).getKeys(CACHE_NAME);
    }

    @Test
    @DisplayName("Should delegate getAll to strategy")
    void shouldDelegateGetAllToStrategy() {
        Map<Object, String> entries = new HashMap<>();
        entries.put("key1", "value1");
        entries.put("key2", "value2");

        when(cacheStrategy.getAll(CACHE_NAME, String.class)).thenReturn(entries);

        Map<Object, String> result = cacheService.getAll(CACHE_NAME, String.class);

        assertThat(result).isEqualTo(entries);
        verify(cacheStrategy).getAll(CACHE_NAME, String.class);
    }

    @Test
    @DisplayName("Should delegate putAll to strategy")
    void shouldDelegatePutAllToStrategy() {
        Map<Object, String> entries = new HashMap<>();
        entries.put("key1", "value1");
        entries.put("key2", "value2");

        cacheService.putAll(CACHE_NAME, entries);

        verify(cacheStrategy).putAll(CACHE_NAME, entries);
    }

    @Test
    @DisplayName("Should delegate getStatistics to strategy")
    void shouldDelegateGetStatisticsToStrategy() {
        CacheStrategy.CacheStatistics stats = new CacheStrategy.CacheStatistics(
            20L,    // hits
            10L,    // misses
            5L,     // evictions
            30L,    // puts
            3L,     // removals
            0.667,  // hit rate
            100L,   // size
            1024L   // memory usage
        );

        when(cacheStrategy.getStatistics(CACHE_NAME)).thenReturn(stats);

        CacheStrategy.CacheStatistics result = cacheService.getStatistics(CACHE_NAME);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(100L);
        assertThat(result.hits()).isEqualTo(20L);
        assertThat(result.misses()).isEqualTo(10L);
        verify(cacheStrategy).getStatistics(CACHE_NAME);
    }

    @Test
    @DisplayName("Should return strategy name")
    void shouldReturnStrategyName() {
        when(cacheStrategy.getName()).thenReturn("TestStrategy");

        // Clear invocations from constructor call
        clearInvocations(cacheStrategy);

        String name = cacheService.getStrategyName();

        assertThat(name).isEqualTo("TestStrategy");
        verify(cacheStrategy).getName();
    }

    @Test
    @DisplayName("Should check availability")
    void shouldCheckAvailability() {
        when(cacheStrategy.isAvailable()).thenReturn(true);

        boolean available = cacheService.isAvailable();

        assertThat(available).isTrue();
        verify(cacheStrategy).isAvailable();
    }
}