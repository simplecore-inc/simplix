package dev.simplecore.simplix.hibernate.cache.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics specific to distributed cache eviction
 */
@Slf4j
@Endpoint(id = "cache-eviction")
public class EvictionMetrics {

    private final AtomicLong localEvictions = new AtomicLong();
    private final AtomicLong distributedEvictions = new AtomicLong();
    private final AtomicLong evictionBroadcasts = new AtomicLong();
    private final AtomicLong evictionFailures = new AtomicLong();
    private final Map<String, AtomicLong> evictionsByEntity = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastEvictionTime = new ConcurrentHashMap<>();

    private Counter localEvictionCounter;
    private Counter distributedEvictionCounter;
    private Timer evictionLatency;

    public EvictionMetrics(MeterRegistry registry) {
        initializeMetrics(registry);
    }

    // Constructor for when MeterRegistry is not available
    public EvictionMetrics() {
        log.info("ℹ MeterRegistry not available, metrics will be tracked without Micrometer");
    }

    private void initializeMetrics(MeterRegistry registry) {
        if (registry != null) {
            this.localEvictionCounter = Counter.builder("cache.eviction.local")
                    .description("Number of local cache evictions")
                    .register(registry);

            this.distributedEvictionCounter = Counter.builder("cache.eviction.distributed")
                    .description("Number of distributed cache evictions")
                    .register(registry);

            this.evictionLatency = Timer.builder("cache.eviction.latency")
                    .description("Cache eviction latency")
                    .register(registry);
        }
    }

    public void recordLocalEviction(String entityClass) {
        localEvictions.incrementAndGet();
        if (localEvictionCounter != null) {
            localEvictionCounter.increment();
        }
        evictionsByEntity.computeIfAbsent(entityClass, k -> new AtomicLong()).incrementAndGet();
        lastEvictionTime.put(entityClass, Instant.now());
    }

    public void recordDistributedEviction(String entityClass, long latencyMs) {
        distributedEvictions.incrementAndGet();
        if (distributedEvictionCounter != null) {
            distributedEvictionCounter.increment();
        }
        if (evictionLatency != null) {
            evictionLatency.record(Duration.ofMillis(latencyMs));
        }
    }

    public void recordBroadcast() {
        evictionBroadcasts.incrementAndGet();
    }

    public void recordFailure() {
        evictionFailures.incrementAndGet();
    }

    @ReadOperation
    public Map<String, Object> metrics() {
        return Map.of(
                "summary", Map.of(
                        "localEvictions", localEvictions.get(),
                        "distributedEvictions", distributedEvictions.get(),
                        "broadcasts", evictionBroadcasts.get(),
                        "failures", evictionFailures.get(),
                        "failureRate", calculateFailureRate()
                ),
                "byEntity", getEntityMetrics(),
                "recentActivity", getRecentActivity()
        );
    }

    private double calculateFailureRate() {
        long total = evictionBroadcasts.get();
        return total > 0 ? (double) evictionFailures.get() / total * 100 : 0;
    }

    private Map<String, Object> getEntityMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        evictionsByEntity.forEach((entity, count) -> {
            Instant last = lastEvictionTime.get(entity);
            metrics.put(entity, Map.of(
                    "count", count.get(),
                    "lastEviction", last != null ? last.toString() : "never"
            ));
        });
        return metrics;
    }

    private Map<String, Object> getRecentActivity() {
        Instant oneMinuteAgo = Instant.now().minusSeconds(60);
        long recentEvictions = lastEvictionTime.values().stream()
                .filter(time -> time.isAfter(oneMinuteAgo))
                .count();

        return Map.of(
                "evictionsLastMinute", recentEvictions,
                "currentlyActive", recentEvictions > 0
        );
    }
}