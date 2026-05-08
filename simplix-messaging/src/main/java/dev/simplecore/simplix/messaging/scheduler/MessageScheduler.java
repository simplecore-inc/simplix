package dev.simplecore.simplix.messaging.scheduler;

import dev.simplecore.simplix.messaging.core.Message;

import java.time.Duration;

/**
 * Delayed-delivery scheduler.
 *
 * <p>Brokers that support scheduled publishing register an implementation.
 * Brokers that do not support scheduling declare
 * {@code BrokerCapabilities.scheduledPublishing() == false} and do not register a bean.
 *
 * @deprecated since 1.1.1, scheduled message delivery is a separable concern that
 *             does not belong in the messaging SPI. Most use cases are better
 *             addressed by Spring {@code @Scheduled} + ShedLock combined with a
 *             database column for the trigger time, or by a dedicated scheduling
 *             engine such as Quartz or Temporal for durable, distributed,
 *             one-shot future delivery. The four
 *             {@code MessageScheduler} implementations (Local / Redis / NATS / the
 *             deprecated wrapper) and the autoconfigure beans that register them
 *             will be removed in a future major release. Until then, the bean
 *             registrations are gated by per-broker
 *             {@code simplix.messaging.<broker>.scheduler.enabled} flags that
 *             default to {@code false}; set the flag to {@code true} to opt in.
 */
@Deprecated(since = "1.1.1", forRemoval = true)
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
