package dev.simplecore.simplix.event.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

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

    private Counter publishedCounter;
    private Counter failedCounter;
    private Timer publishTimer;

    @Autowired
    public void init() {
        if (meterRegistry == null) {
            log.warn("MeterRegistry not available, metrics will be disabled");
            return;
        }

        this.publishedCounter = Counter.builder(metricsPrefix + ".published")
            .description("Number of events published")
            .register(meterRegistry);

        this.failedCounter = Counter.builder(metricsPrefix + ".failed")
            .description("Number of failed event publishes")
            .register(meterRegistry);

        this.publishTimer = Timer.builder(metricsPrefix + ".publish.time")
            .description("Time taken to publish events")
            .register(meterRegistry);

        log.info("Event metrics initialized with prefix: {}", metricsPrefix);
    }

    public void recordPublished(String eventType) {
        if (publishedCounter != null) {
            publishedCounter.increment();
        }
    }

    public void recordFailed(String eventType) {
        if (failedCounter != null) {
            failedCounter.increment();
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