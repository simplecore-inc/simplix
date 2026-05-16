package dev.simplecore.simplix.cache.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.cache.config.CacheProperties;
import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NatsCacheStrategy")
class NatsCacheStrategyTest {

    @Mock
    private Connection connection;

    @Mock
    private KeyValueManagement kvManagement;

    @Mock
    private KeyValue keyValue;

    @Mock
    private KeyValueStatus kvStatus;

    private NatsCacheStrategy strategy;

    @BeforeEach
    void setUp() throws Exception {
        when(connection.keyValueManagement()).thenReturn(kvManagement);
        when(kvManagement.create(any())).thenReturn(kvStatus);
        when(connection.keyValue(anyString())).thenReturn(keyValue);

        CacheProperties properties = new CacheProperties();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        strategy = new NatsCacheStrategy(connection, properties, objectMapper);
    }

    @Nested
    @DisplayName("get/put roundtrip")
    class GetPutRoundtrip {

        @Test
        @DisplayName("should return empty when key is missing")
        void getReturnsEmptyOnMiss() throws Exception {
            when(keyValue.get(anyString())).thenReturn(null);

            Optional<String> result = strategy.get("default", "k1", String.class);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should put then get value")
        void putThenGet() throws Exception {
            KeyValueEntry entry = mock(KeyValueEntry.class);
            byte[] serialized = "\"hello\"".getBytes();
            when(entry.getValue()).thenReturn(serialized);
            when(keyValue.get(anyString())).thenReturn(entry);

            strategy.put("default", "k1", "hello");
            Optional<String> result = strategy.get("default", "k1", String.class);

            verify(keyValue).put(anyString(), any(byte[].class));
            assertThat(result).contains("hello");
        }

        @Test
        @DisplayName("should skip null put silently")
        void putNullSkipped() throws Exception {
            strategy.put("default", "k1", null);
            verify(keyValue, times(0)).put(anyString(), any(byte[].class));
        }
    }

    @Nested
    @DisplayName("eviction")
    class Eviction {

        @Test
        @DisplayName("evict removes single key")
        void evictRemovesKey() throws Exception {
            strategy.evict("default", "k1");
            verify(keyValue).delete(anyString());
        }

        @Test
        @DisplayName("evictAll removes multiple keys")
        void evictAllRemovesMultipleKeys() throws Exception {
            strategy.evictAll("default", List.of("k1", "k2", "k3"));
            verify(keyValue, times(3)).delete(anyString());
        }

        @Test
        @DisplayName("clear iterates listed keys and deletes each")
        void clearDeletesAll() throws Exception {
            when(keyValue.keys()).thenReturn(List.of("k1", "k2"));
            strategy.clear("default");
            verify(keyValue, times(2)).delete(anyString());
        }
    }

    @Nested
    @DisplayName("bucket lifecycle")
    class BucketLifecycle {

        @Test
        @DisplayName("creates KV bucket idempotently on first access")
        void createsBucketOnce() throws Exception {
            strategy.put("default", "k1", "v1");
            strategy.put("default", "k2", "v2");
            verify(kvManagement, times(1)).create(any());
        }

        @Test
        @DisplayName("binds existing bucket via Connection.keyValue() on first put")
        void bindsBucketOnFirstAccess() throws Exception {
            strategy.put("default", "k1", "v1");
            verify(connection).keyValue(anyString());
        }
    }

    @Nested
    @DisplayName("metadata")
    class Metadata {

        @Test
        @DisplayName("getName returns NatsCacheStrategy")
        void getNameReturnsExpected() {
            assertThat(strategy.getName()).isEqualTo("NatsCacheStrategy");
        }

        @Test
        @DisplayName("isAvailable reflects connection status")
        void isAvailableReflectsConnection() {
            when(connection.getStatus()).thenReturn(Connection.Status.CONNECTED);
            assertThat(strategy.isAvailable()).isTrue();

            when(connection.getStatus()).thenReturn(Connection.Status.DISCONNECTED);
            assertThat(strategy.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("getStatistics tracks hits and misses")
        void statisticsTracksHitsAndMisses() throws Exception {
            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("\"v\"".getBytes());

            when(keyValue.get("hit")).thenReturn(entry);
            when(keyValue.get("miss")).thenReturn(null);

            strategy.get("default", "hit", String.class);
            strategy.get("default", "miss", String.class);
            strategy.get("default", "miss", String.class);

            CacheStrategy.CacheStatistics stats = strategy.getStatistics("default");
            assertThat(stats.hits()).isEqualTo(1L);
            assertThat(stats.misses()).isEqualTo(2L);
        }
    }
}
