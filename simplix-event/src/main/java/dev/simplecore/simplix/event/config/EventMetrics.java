package dev.simplecore.simplix.event.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Metrics collector for event publishing
 * Tracks event publish counts, timings, and failures
 */
@Slf4j
public class EventMetrics {

    @Value("${simplix.events.monitoring.metrics-prefix:simplix.events}")
    private String metricsPrefix;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private Timer publishTimer;

    // Cache counters by eventType to avoid repeated registration
    private final ConcurrentMap<String, Counter> publishedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> failedCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        if (meterRegistry == null) {
            log.warn("MeterRegistry not available, metrics will be disabled");
            return;
        }

        this.publishTimer = Timer.builder(metricsPrefix + ".publish.time")
            .description("Time taken to publish events")
            .register(meterRegistry);

        log.info("Event metrics initialized with prefix: {}", metricsPrefix);
    }

    public void recordPublished(String eventType) {
        if (meterRegistry != null) {
            String type = eventType != null ? eventType : "unknown";
            publishedCounters.computeIfAbsent(type, t ->
                Counter.builder(metricsPrefix + ".published")
                    .description("Number of events published")
                    .tag("eventType", t)
                    .register(meterRegistry)
            ).increment();
        }
    }

    public void recordFailed(String eventType) {
        if (meterRegistry != null) {
            String type = eventType != null ? eventType : "unknown";
            failedCounters.computeIfAbsent(type, t ->
                Counter.builder(metricsPrefix + ".failed")
                    .description("Number of failed event publishes")
                    .tag("eventType", t)
                    .register(meterRegistry)
            ).increment();
        }
    }

    public Timer.Sample startTimer() {
        if (meterRegistry != null) {
            return Timer.start(meterRegistry);
        }
        return null;
    }

    public void stopTimer(Timer.Sample sample) {
        if (sample != null && publishTimer != null) {
            sample.stop(publishTimer);
        }
    }
}