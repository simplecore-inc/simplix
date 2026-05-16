package dev.simplecore.simplix.messaging.scheduler;

import dev.simplecore.simplix.messaging.core.Message;

import java.time.Duration;

/**
 * Delayed-delivery scheduler.
 *
 * <p>Brokers that support scheduled publishing register an implementation.
 * Brokers that do not support scheduling declare
 * {@code BrokerCapabilities.scheduledPublishing() == false} and do not register a bean.
 */
public interface MessageScheduler {

    /** Schedule a message for delivery after the given delay. Returns a unique schedule ID. */
    String publishDelayed(Message<?> message, Duration delay);

    /** Cancel a pending scheduled message. Returns true if cancelled before delivery. */
    boolean cancel(String scheduleId);

    /** Start any background poller / leader-election threads. */
    void start();

    /** Drain in-flight deliveries and stop background threads. */
    void stop();
}
