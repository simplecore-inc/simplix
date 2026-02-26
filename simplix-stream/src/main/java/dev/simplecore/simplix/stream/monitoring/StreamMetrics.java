package dev.simplecore.simplix.stream.monitoring;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for stream module.
 * <p>
 * Provides gauges and counters for monitoring stream performance.
 */
@Slf4j
public class StreamMetrics implements MeterBinder {

    private final SessionRegistry sessionRegistry;
    private final SchedulerManager schedulerManager;
    private final String prefix;

    // Counters for tracking events
    private final AtomicLong messagesDelivered = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    private final AtomicLong connectionsEstablished = new AtomicLong(0);
    private final AtomicLong connectionsClosed = new AtomicLong(0);
    private final AtomicLong subscriptionsAdded = new AtomicLong(0);
    private final AtomicLong subscriptionsRemoved = new AtomicLong(0);

    public StreamMetrics(
            SessionRegistry sessionRegistry,
            SchedulerManager schedulerManager,
            StreamProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.schedulerManager = schedulerManager;
        this.prefix = properties.getMonitoring().getMetricsPrefix();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // Session gauges
        Gauge.builder(prefix + ".sessions.active", sessionRegistry, SessionRegistry::count)
                .description("Number of active stream sessions")
                .register(registry);

        // Scheduler gauges
        Gauge.builder(prefix + ".schedulers.active", schedulerManager, SchedulerManager::getSchedulerCount)
                .description("Number of active schedulers")
                .register(registry);

        // Message counters
        Gauge.builder(prefix + ".messages.delivered", messagesDelivered, AtomicLong::get)
                .description("Total messages delivered")
                .register(registry);

        Gauge.builder(prefix + ".messages.failed", messagesFailed, AtomicLong::get)
                .description("Total messages failed to deliver")
                .register(registry);

        // Connection counters
        Gauge.builder(prefix + ".connections.established", connectionsEstablished, AtomicLong::get)
                .description("Total connections established")
                .register(registry);

        Gauge.builder(prefix + ".connections.closed", connectionsClosed, AtomicLong::get)
                .description("Total connections closed")
                .register(registry);

        // Subscription counters
        Gauge.builder(prefix + ".subscriptions.added", subscriptionsAdded, AtomicLong::get)
                .description("Total subscriptions added")
                .register(registry);

        Gauge.builder(prefix + ".subscriptions.removed", subscriptionsRemoved, AtomicLong::get)
                .description("Total subscriptions removed")
                .register(registry);

        log.info("Stream metrics registered with prefix: {}", prefix);
    }

    /**
     * Record a successful message delivery.
     */
    public void recordMessageDelivered() {
        messagesDelivered.incrementAndGet();
    }

    /**
     * Record a failed message delivery.
     */
    public void recordMessageFailed() {
        messagesFailed.incrementAndGet();
    }

    /**
     * Record a new connection.
     */
    public void recordConnectionEstablished() {
        connectionsEstablished.incrementAndGet();
    }

    /**
     * Record a connection closed.
     */
    public void recordConnectionClosed() {
        connectionsClosed.incrementAndGet();
    }

    /**
     * Record a subscription added.
     */
    public void recordSubscriptionAdded() {
        subscriptionsAdded.incrementAndGet();
    }

    /**
     * Record a subscription removed.
     */
    public void recordSubscriptionRemoved() {
        subscriptionsRemoved.incrementAndGet();
    }

    /**
     * Get the total messages delivered.
     *
     * @return the count
     */
    public long getMessagesDelivered() {
        return messagesDelivered.get();
    }

    /**
     * Get the total messages failed.
     *
     * @return the count
     */
    public long getMessagesFailed() {
        return messagesFailed.get();
    }
}
