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
 * Metrics specific to distributed cache eviction.
 * Entity maps are limited to MAX_ENTITY_TYPES to prevent unbounded memory growth.
 */
@Slf4j
@Endpoint(id = "cache-eviction")
public class EvictionMetrics {

    /**
     * Maximum number of entity types to track to prevent OOM in long-running systems.
     */
    private static final int MAX_ENTITY_TYPES = 500;

    private final AtomicLong localEvictions = new AtomicLong();
    private final AtomicLong distributedEvictions = new AtomicLong();
    private final AtomicLong evictionBroadcasts = new AtomicLong();
    private final AtomicLong evictionSuccesses = new AtomicLong();
    private final AtomicLong evictionFailures = new AtomicLong();
    private final Map<String, AtomicLong> evictionsByEntity = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastEvictionTime = new ConcurrentHashMap<>();

    private Counter localEvictionCounter;
    private Counter distributedEvictionCounter;
    private Counter successCounter;
    private Counter failureCounter;
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

            this.successCounter = Counter.builder("cache.eviction.success")
                    .description("Number of successful cache evictions")
                    .register(registry);

            this.failureCounter = Counter.builder("cache.eviction.failure")
                    .description("Number of failed cache evictions")
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

        // Skip entity-level tracking for null or empty entity class (H9 fix)
        if (entityClass == null || entityClass.isEmpty()) {
            return;
        }

        // Only track entity-level metrics if within size limit
        // Use computeIfAbsent result to atomically check and add (H3 fix)
        AtomicLong existing = evictionsByEntity.get(entityClass);
        if (existing != null) {
            // Entity already tracked - safe to update
            existing.incrementAndGet();
            // Only update lastEvictionTime if entity is already tracked (M9 fix)
            lastEvictionTime.put(entityClass, Instant.now());
        } else if (evictionsByEntity.size() < MAX_ENTITY_TYPES) {
            // New entity and within limit - try to add atomically
            AtomicLong counter = evictionsByEntity.computeIfAbsent(entityClass, k -> new AtomicLong());
            counter.incrementAndGet();
            // Only add to lastEvictionTime if successfully added to evictionsByEntity
            if (evictionsByEntity.containsKey(entityClass)) {
                lastEvictionTime.put(entityClass, Instant.now());
            }
        }
        // If size >= MAX_ENTITY_TYPES and entity not tracked, skip silently
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

    public void recordSuccess() {
        evictionSuccesses.incrementAndGet();
        if (successCounter != null) {
            successCounter.increment();
        }
    }

    public void recordFailure() {
        evictionFailures.incrementAndGet();
        if (failureCounter != null) {
            failureCounter.increment();
        }
    }

    @ReadOperation
    public Map<String, Object> metrics() {
        return Map.of(
                "summary", Map.of(
                        "localEvictions", localEvictions.get(),
                        "distributedEvictions", distributedEvictions.get(),
                        "broadcasts", evictionBroadcasts.get(),
                        "successes", evictionSuccesses.get(),
                        "failures", evictionFailures.get(),
                        "successRate", calculateSuccessRate(),
                        "failureRate", calculateFailureRate()
                ),
                "byEntity", getEntityMetrics(),
                "recentActivity", getRecentActivity()
        );
    }

    private double calculateSuccessRate() {
        long total = evictionSuccesses.get() + evictionFailures.get();
        return total > 0 ? (double) evictionSuccesses.get() / total * 100 : 0;
    }

    private double calculateFailureRate() {
        long total = evictionSuccesses.get() + evictionFailures.get();
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