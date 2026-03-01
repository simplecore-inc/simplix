package dev.simplecore.simplix.messaging.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Micrometer-based metrics for the simplix-messaging module.
 *
 * <p>Provides counters, timers, and gauges for tracking message publishing,
 * consumption, and failure rates. All meters are lazily registered on first use
 * and cached in thread-safe maps.
 *
 * <p>When no {@link MeterRegistry} is available (e.g., actuator not on classpath),
 * all recording methods become no-ops.
 */
@Slf4j
public class MessagingMetrics {

    private static final String METRIC_PUBLISHED = "simplix.messaging.published";
    private static final String METRIC_CONSUMED = "simplix.messaging.consumed";
    private static final String METRIC_FAILED = "simplix.messaging.failed";
    private static final String METRIC_PUBLISH_TIME = "simplix.messaging.publish.time";
    private static final String METRIC_CONSUME_TIME = "simplix.messaging.consume.time";
    private static final String METRIC_PENDING_COUNT = "simplix.messaging.pending.count";

    private static final String TAG_CHANNEL = "channel";
    private static final String TAG_GROUP = "group";
    private static final String TAG_ERROR_TYPE = "errorType";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    /**
     * Create a new metrics instance.
     *
     * @param registry the Micrometer meter registry; may be {@code null} for no-op mode
     */
    public MessagingMetrics(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            log.info("MessagingMetrics initialized with MeterRegistry: {}", registry.getClass().getSimpleName());
        } else {
            log.info("MessagingMetrics initialized in no-op mode (no MeterRegistry available)");
        }
    }

    /**
     * Record a successful message publish event.
     *
     * @param channel the channel the message was published to
     */
    public void recordPublish(String channel) {
        if (registry == null) {
            return;
        }
        getOrCreateCounter(METRIC_PUBLISHED, TAG_CHANNEL, channel).increment();
    }

    /**
     * Record a successful message consumption event.
     *
     * @param channel the channel the message was consumed from
     * @param group   the consumer group name
     */
    public void recordConsume(String channel, String group) {
        if (registry == null) {
            return;
        }
        String key = METRIC_CONSUMED + ":" + channel + ":" + group;
        counters.computeIfAbsent(key, k ->
                Counter.builder(METRIC_CONSUMED)
                        .tag(TAG_CHANNEL, channel)
                        .tag(TAG_GROUP, group)
                        .register(registry)
        ).increment();
    }

    /**
     * Record a message processing failure.
     *
     * @param channel   the channel where the failure occurred
     * @param errorType the type of error (e.g., exception class name)
     */
    public void recordFailure(String channel, String errorType) {
        if (registry == null) {
            return;
        }
        String key = METRIC_FAILED + ":" + channel + ":" + errorType;
        counters.computeIfAbsent(key, k ->
                Counter.builder(METRIC_FAILED)
                        .tag(TAG_CHANNEL, channel)
                        .tag(TAG_ERROR_TYPE, errorType)
                        .register(registry)
        ).increment();
    }

    /**
     * Start a timer for measuring publish duration.
     *
     * @param channel the target channel
     * @return a timer sample, or {@code null} if no registry is available
     */
    public Timer.Sample startPublishTimer(String channel) {
        if (registry == null) {
            return null;
        }
        return Timer.start(registry);
    }

    /**
     * Stop a publish timer and record the duration.
     *
     * @param sample  the timer sample returned by {@link #startPublishTimer(String)}
     * @param channel the target channel
     */
    public void stopPublishTimer(Timer.Sample sample, String channel) {
        if (registry == null || sample == null) {
            return;
        }
        Timer timer = getOrCreateTimer(METRIC_PUBLISH_TIME, TAG_CHANNEL, channel);
        sample.stop(timer);
    }

    /**
     * Start a timer for measuring consume duration.
     *
     * @param channel the source channel
     * @param group   the consumer group name
     * @return a timer sample, or {@code null} if no registry is available
     */
    public Timer.Sample startConsumeTimer(String channel, String group) {
        if (registry == null) {
            return null;
        }
        return Timer.start(registry);
    }

    /**
     * Stop a consume timer and record the duration.
     *
     * @param sample  the timer sample returned by {@link #startConsumeTimer(String, String)}
     * @param channel the source channel
     * @param group   the consumer group name
     */
    public void stopConsumeTimer(Timer.Sample sample, String channel, String group) {
        if (registry == null || sample == null) {
            return;
        }
        String key = METRIC_CONSUME_TIME + ":" + channel + ":" + group;
        Timer timer = timers.computeIfAbsent(key, k ->
                Timer.builder(METRIC_CONSUME_TIME)
                        .tag(TAG_CHANNEL, channel)
                        .tag(TAG_GROUP, group)
                        .register(registry)
        );
        sample.stop(timer);
    }

    /**
     * Register a gauge that tracks the pending message count for a channel/group.
     *
     * @param channel  the channel name
     * @param group    the consumer group name
     * @param supplier a supplier that returns the current pending count
     */
    public void registerPendingGauge(String channel, String group, Supplier<Number> supplier) {
        if (registry == null) {
            return;
        }
        registry.gauge(METRIC_PENDING_COUNT,
                io.micrometer.core.instrument.Tags.of(TAG_CHANNEL, channel, TAG_GROUP, group),
                supplier,
                s -> s.get().doubleValue());
        log.debug("Registered pending count gauge for channel='{}' group='{}'", channel, group);
    }

    // ---------------------------------------------------------------
    // Lazy meter creation helpers
    // ---------------------------------------------------------------

    private Counter getOrCreateCounter(String name, String tagKey, String tagValue) {
        String cacheKey = name + ":" + tagValue;
        return counters.computeIfAbsent(cacheKey, k ->
                Counter.builder(name)
                        .tag(tagKey, tagValue)
                        .register(registry)
        );
    }

    private Timer getOrCreateTimer(String name, String tagKey, String tagValue) {
        String cacheKey = name + ":" + tagValue;
        return timers.computeIfAbsent(cacheKey, k ->
                Timer.builder(name)
                        .tag(tagKey, tagValue)
                        .register(registry)
        );
    }
}
