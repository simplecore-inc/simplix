package dev.simplecore.simplix.hibernate.cache.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EvictionMetrics.
 */
@DisplayName("EvictionMetrics Tests")
class EvictionMetricsTest {

    private MeterRegistry meterRegistry;
    private EvictionMetrics evictionMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        evictionMetrics = new EvictionMetrics(meterRegistry);
    }

    @Nested
    @DisplayName("Constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should work without MeterRegistry")
        void shouldWorkWithoutMeterRegistry() {
            // When
            EvictionMetrics metrics = new EvictionMetrics();

            // Then - should not throw and should track metrics
            metrics.recordLocalEviction("TestEntity");
            metrics.recordSuccess();
            metrics.recordFailure();

            Map<String, Object> result = metrics.metrics();
            assertThat(result).containsKey("summary");
        }

        @Test
        @DisplayName("Should work with MeterRegistry")
        void shouldWorkWithMeterRegistry() {
            // When
            EvictionMetrics metrics = new EvictionMetrics(meterRegistry);

            // Then
            metrics.recordLocalEviction("TestEntity");

            assertThat(meterRegistry.find("cache.eviction.local").counter()).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordLocalEviction() tests")
    class RecordLocalEvictionTests {

        @Test
        @DisplayName("Should increment local eviction counter")
        void shouldIncrementLocalEvictionCounter() {
            // When
            evictionMetrics.recordLocalEviction("com.example.User");
            evictionMetrics.recordLocalEviction("com.example.User");
            evictionMetrics.recordLocalEviction("com.example.Order");

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("localEvictions")).isEqualTo(3L);
        }

        @Test
        @DisplayName("Should track entity-level metrics")
        void shouldTrackEntityLevelMetrics() {
            // When
            evictionMetrics.recordLocalEviction("com.example.User");
            evictionMetrics.recordLocalEviction("com.example.User");
            evictionMetrics.recordLocalEviction("com.example.Order");

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> byEntity = (Map<String, Object>) metrics.get("byEntity");

            assertThat(byEntity).containsKey("com.example.User");
            assertThat(byEntity).containsKey("com.example.Order");

            @SuppressWarnings("unchecked")
            Map<String, Object> userMetrics = (Map<String, Object>) byEntity.get("com.example.User");
            assertThat(userMetrics.get("count")).isEqualTo(2L);
        }

        @Test
        @DisplayName("Should skip null entity class")
        void shouldSkipNullEntityClass() {
            // When
            evictionMetrics.recordLocalEviction(null);
            evictionMetrics.recordLocalEviction("");

            // Then - counter should be incremented but no entity tracking
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("localEvictions")).isEqualTo(2L);

            @SuppressWarnings("unchecked")
            Map<String, Object> byEntity = (Map<String, Object>) metrics.get("byEntity");
            assertThat(byEntity).isEmpty();
        }
    }

    @Nested
    @DisplayName("recordDistributedEviction() tests")
    class RecordDistributedEvictionTests {

        @Test
        @DisplayName("Should increment distributed eviction counter")
        void shouldIncrementDistributedEvictionCounter() {
            // When
            evictionMetrics.recordDistributedEviction("com.example.User", 50L);
            evictionMetrics.recordDistributedEviction("com.example.User", 30L);

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("distributedEvictions")).isEqualTo(2L);
        }

        @Test
        @DisplayName("Should record latency in timer")
        void shouldRecordLatencyInTimer() {
            // When
            evictionMetrics.recordDistributedEviction("com.example.User", 100L);

            // Then
            assertThat(meterRegistry.find("cache.eviction.latency").timer()).isNotNull();
        }
    }

    @Nested
    @DisplayName("recordBroadcast() tests")
    class RecordBroadcastTests {

        @Test
        @DisplayName("Should increment broadcast counter")
        void shouldIncrementBroadcastCounter() {
            // When
            evictionMetrics.recordBroadcast();
            evictionMetrics.recordBroadcast();

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("broadcasts")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("recordSuccess() and recordFailure() tests")
    class RecordSuccessFailureTests {

        @Test
        @DisplayName("Should increment success counter")
        void shouldIncrementSuccessCounter() {
            // When
            evictionMetrics.recordSuccess();
            evictionMetrics.recordSuccess();

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("successes")).isEqualTo(2L);
        }

        @Test
        @DisplayName("Should increment failure counter")
        void shouldIncrementFailureCounter() {
            // When
            evictionMetrics.recordFailure();

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("failures")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should calculate success rate correctly")
        void shouldCalculateSuccessRateCorrectly() {
            // When
            evictionMetrics.recordSuccess();
            evictionMetrics.recordSuccess();
            evictionMetrics.recordSuccess();
            evictionMetrics.recordFailure();

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat((Double) summary.get("successRate")).isEqualTo(75.0);
            assertThat((Double) summary.get("failureRate")).isEqualTo(25.0);
        }

        @Test
        @DisplayName("Should handle zero total for success rate")
        void shouldHandleZeroTotalForSuccessRate() {
            // When - no successes or failures recorded

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat((Double) summary.get("successRate")).isEqualTo(0.0);
            assertThat((Double) summary.get("failureRate")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("metrics() tests")
    class MetricsTests {

        @Test
        @DisplayName("Should return complete metrics structure")
        void shouldReturnCompleteMetricsStructure() {
            // Given
            evictionMetrics.recordLocalEviction("TestEntity");
            evictionMetrics.recordSuccess();

            // When
            Map<String, Object> metrics = evictionMetrics.metrics();

            // Then
            assertThat(metrics).containsKeys("summary", "byEntity", "recentActivity");

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary).containsKeys(
                    "localEvictions", "distributedEvictions", "broadcasts",
                    "successes", "failures", "successRate", "failureRate"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> recentActivity = (Map<String, Object>) metrics.get("recentActivity");
            assertThat(recentActivity).containsKeys("evictionsLastMinute", "currentlyActive");
        }

        @Test
        @DisplayName("recentActivity should show recent evictions")
        void recentActivityShouldShowRecentEvictions() {
            // When
            evictionMetrics.recordLocalEviction("TestEntity");

            // Then
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> recentActivity = (Map<String, Object>) metrics.get("recentActivity");
            assertThat((Long) recentActivity.get("evictionsLastMinute")).isEqualTo(1L);
            assertThat((Boolean) recentActivity.get("currentlyActive")).isTrue();
        }
    }

    @Nested
    @DisplayName("Entity type limit tests")
    class EntityTypeLimitTests {

        @Test
        @DisplayName("Should limit entity types to prevent OOM")
        void shouldLimitEntityTypesToPreventOom() {
            // When - record more than MAX_ENTITY_TYPES (500) different entities
            for (int i = 0; i < 600; i++) {
                evictionMetrics.recordLocalEviction("Entity" + i);
            }

            // Then - local evictions counter should be 600
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
            assertThat(summary.get("localEvictions")).isEqualTo(600L);

            // But entity map should be limited
            @SuppressWarnings("unchecked")
            Map<String, Object> byEntity = (Map<String, Object>) metrics.get("byEntity");
            assertThat(byEntity.size()).isLessThanOrEqualTo(500);
        }

        @Test
        @DisplayName("Should continue tracking existing entities even when limit reached")
        void shouldContinueTrackingExistingEntitiesWhenLimitReached() {
            // Given - fill up to limit
            for (int i = 0; i < 500; i++) {
                evictionMetrics.recordLocalEviction("Entity" + i);
            }

            // When - record more evictions for existing entities
            evictionMetrics.recordLocalEviction("Entity0");
            evictionMetrics.recordLocalEviction("Entity0");

            // Then - existing entity should have increased count
            Map<String, Object> metrics = evictionMetrics.metrics();
            @SuppressWarnings("unchecked")
            Map<String, Object> byEntity = (Map<String, Object>) metrics.get("byEntity");
            @SuppressWarnings("unchecked")
            Map<String, Object> entity0 = (Map<String, Object>) byEntity.get("Entity0");
            assertThat(entity0.get("count")).isEqualTo(3L);
        }
    }
}
