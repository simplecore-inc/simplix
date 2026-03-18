package dev.simplecore.simplix.cache.config;

import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@DisplayName("CacheMetricsCollector")
@ExtendWith(MockitoExtension.class)
class CacheMetricsCollectorTest {

    @Mock
    private CacheStrategy cacheStrategy;

    private CacheProperties properties;
    private CacheMetricsCollector collector;

    @BeforeEach
    void setUp() {
        properties = new CacheProperties();
        collector = new CacheMetricsCollector(cacheStrategy, properties);
    }

    @Nested
    @DisplayName("collectMetrics")
    class CollectMetricsTests {

        @Test
        @DisplayName("should skip collection when metrics are disabled")
        void shouldSkipWhenMetricsDisabled() {
            properties.getMetrics().setEnabled(false);

            collector.collectMetrics();

            verify(cacheStrategy, never()).getStatistics(anyString());
        }

        @Test
        @DisplayName("should collect metrics for all configured caches when enabled")
        void shouldCollectMetricsForAllCaches() {
            properties.getMetrics().setEnabled(true);
            Map<String, CacheProperties.CacheConfig> configs = new HashMap<>();
            configs.put("users", new CacheProperties.CacheConfig(600L));
            configs.put("sessions", new CacheProperties.CacheConfig(300L));
            properties.setCacheConfigs(configs);

            CacheStrategy.CacheStatistics stats = new CacheStrategy.CacheStatistics(
                    10, 5, 2, 15, 1, 0.67, 20, 1024);
            when(cacheStrategy.getStatistics("users")).thenReturn(stats);
            when(cacheStrategy.getStatistics("sessions")).thenReturn(stats);

            collector.collectMetrics();

            verify(cacheStrategy).getStatistics("users");
            verify(cacheStrategy).getStatistics("sessions");
        }

        @Test
        @DisplayName("should skip logging when there are no hits or misses")
        void shouldSkipLoggingWhenNoHitsOrMisses() {
            properties.getMetrics().setEnabled(true);
            Map<String, CacheProperties.CacheConfig> configs = new HashMap<>();
            configs.put("empty", new CacheProperties.CacheConfig(600L));
            properties.setCacheConfigs(configs);

            CacheStrategy.CacheStatistics emptyStats = CacheStrategy.CacheStatistics.empty();
            when(cacheStrategy.getStatistics("empty")).thenReturn(emptyStats);

            collector.collectMetrics();

            verify(cacheStrategy).getStatistics("empty");
        }

        @Test
        @DisplayName("should handle exception during metrics collection gracefully")
        void shouldHandleExceptionGracefully() {
            properties.getMetrics().setEnabled(true);
            Map<String, CacheProperties.CacheConfig> configs = new HashMap<>();
            configs.put("failing", new CacheProperties.CacheConfig(600L));
            properties.setCacheConfigs(configs);

            when(cacheStrategy.getStatistics("failing"))
                    .thenThrow(new RuntimeException("Redis unavailable"));

            // Should not throw
            collector.collectMetrics();

            verify(cacheStrategy).getStatistics("failing");
        }

        @Test
        @DisplayName("should collect metrics from default cache configs")
        void shouldCollectFromDefaultCacheConfigs() {
            properties.getMetrics().setEnabled(true);
            // Default constructor adds "default" cache config

            CacheStrategy.CacheStatistics stats = new CacheStrategy.CacheStatistics(
                    5, 3, 0, 8, 0, 0.625, 5, 512);
            when(cacheStrategy.getStatistics("default")).thenReturn(stats);

            collector.collectMetrics();

            verify(cacheStrategy).getStatistics("default");
        }
    }
}
