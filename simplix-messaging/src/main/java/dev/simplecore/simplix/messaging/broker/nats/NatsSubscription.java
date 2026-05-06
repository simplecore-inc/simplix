package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.broker.common.SubscriptionHealthTracker;
import io.nats.client.JetStreamSubscription;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Subscription} backed by a JetStream pull consumer and its background poller thread.
 *
 * <p>Cancellation sets the active flag to false, unsubscribes the underlying
 * JetStream subscription, and shuts down the poller executor.
 */
@Slf4j
public class NatsSubscription implements Subscription {

    private final String channel;
    private final String groupName;
    private final JetStreamSubscription jsSub;
    private final ExecutorService poller;
    private final SubscriptionHealthTracker healthTracker;
    private final AtomicBoolean active;

    public NatsSubscription(String channel, String groupName,
                            JetStreamSubscription jsSub,
                            ExecutorService poller,
                            SubscriptionHealthTracker healthTracker,
                            AtomicBoolean active) {
        this.channel = channel;
        this.groupName = groupName;
        this.jsSub = jsSub;
        this.poller = poller;
        this.healthTracker = healthTracker;
        this.active = active;
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public String groupName() {
        return groupName;
    }

    @Override
    public boolean isActive() {
        return active.get() && healthTracker.isHealthy();
    }

    @Override
    public void cancel() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        try {
            jsSub.unsubscribe();
        } catch (RuntimeException e) {
            log.debug("Error during NATS unsubscribe on channel '{}': {}", channel, e.getMessage());
        }
        poller.shutdownNow();
        try {
            poller.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Cancelled NATS subscription on channel '{}' [group={}]", channel, groupName);
    }
}
