package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NATS JetStream implementation of {@link BrokerStrategy}.
 *
 * <p>Delegates publishing to {@link NatsJetStreamPublisher}, subscribing to
 * {@link NatsJetStreamPullSubscriber}, and consumer group management to
 * {@link NatsConsumerGroupManager}.
 *
 * <p>Tracks active subscriptions in a {@link ConcurrentHashMap} and optionally
 * runs a background recovery scheduler that resubscribes any dead pollers.
 *
 * <p>Thread-safe.
 */
@Slf4j
public class NatsBrokerStrategy implements BrokerStrategy {

    private static final BrokerCapabilities CAPABILITIES =
            new BrokerCapabilities(true, true, true, true, true, true, true);

    private final Connection connection;
    private final JetStream jetStream;
    private final JetStreamManagement jsManagement;
    private final NatsConsumerGroupManager groupManager;
    private final NatsJetStreamPublisher publisher;
    private final NatsJetStreamPullSubscriber subscriber;
    private final MessagingProperties messagingProperties;

    private final ConcurrentHashMap<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubscribeRequest> subscriptionRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private ScheduledExecutorService recoveryScheduler;

    public NatsBrokerStrategy(Connection connection,
                              JetStream jetStream,
                              JetStreamManagement jsManagement,
                              NatsConsumerGroupManager groupManager,
                              NatsJetStreamPublisher publisher,
                              NatsJetStreamPullSubscriber subscriber,
                              MessagingProperties messagingProperties) {
        this.connection = connection;
        this.jetStream = jetStream;
        this.jsManagement = jsManagement;
        this.groupManager = groupManager;
        this.publisher = publisher;
        this.subscriber = subscriber;
        this.messagingProperties = messagingProperties;
    }

    @Override
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        return publisher.send(channel, payload, headers);
    }

    @Override
    public Subscription subscribe(SubscribeRequest request) {
        Subscription delegate = subscriber.subscribe(request);
        String key = subscriptionKey(request);
        subscriptionRequests.put(key, request);
        TrackedSubscription tracked = new TrackedSubscription(delegate, key);
        activeSubscriptions.put(key, tracked);
        return tracked;
    }

    @Override
    public void ensureConsumerGroup(String channel, String groupName) {
        groupManager.ensureConsumerGroup(channel, groupName);
    }

    /**
     * No-op for NATS: ACK is performed via {@code MessageAcknowledgment.ack()} returned in
     * the listener callback, which directly calls {@code Message.ack()} on the underlying
     * NATS message. Out-of-band ACK by messageId would require a global in-flight tracker;
     * NATS subscribers maintain per-subscription trackers instead.
     */
    @Override
    public void acknowledge(String channel, String groupName, String messageId) {
        log.debug("acknowledge() is a no-op for NATS broker; ACK happens via MessageAcknowledgment "
                + "[channel={}, group={}, messageId={}]", channel, groupName, messageId);
    }

    @Override
    public BrokerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void initialize() {
        ready.set(true);

        Duration period = messagingProperties.getNats().getPendingCheckInterval();
        if (period != null && !period.isZero() && !period.isNegative()) {
            recoveryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nats-resub-recovery");
                t.setDaemon(true);
                return t;
            });
            recoveryScheduler.scheduleAtFixedRate(this::recoverDeadSubscriptions,
                    period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
            log.info("NATS resubscribe scheduler started [interval={}]", period);
        }

        log.info("NATS broker strategy initialized");
    }

    @Override
    public void shutdown() {
        if (recoveryScheduler != null) {
            recoveryScheduler.shutdown();
            try {
                if (!recoveryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    recoveryScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                recoveryScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        activeSubscriptions.forEach((k, s) -> {
            try {
                if (s.isActive()) {
                    s.cancel();
                }
            } catch (Exception e) {
                log.warn("Error cancelling NATS subscription [{}]: {}", k, e.getMessage());
            }
        });
        activeSubscriptions.clear();
        subscriptionRequests.clear();

        try {
            connection.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ready.set(false);
        log.info("NATS broker strategy shut down");
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public String name() {
        return "nats";
    }

    /**
     * Returns an unmodifiable view of the active subscriptions map.
     * Package-private for testing purposes.
     */
    Map<String, Subscription> activeSubscriptions() {
        return Collections.unmodifiableMap(activeSubscriptions);
    }

    /** Walk active subscriptions, cancel + resubscribe any that report inactive. */
    private void recoverDeadSubscriptions() {
        List<String> deadKeys = activeSubscriptions.entrySet().stream()
                .filter(e -> !e.getValue().isActive())
                .map(Map.Entry::getKey)
                .toList();

        for (String key : deadKeys) {
            SubscribeRequest req = subscriptionRequests.get(key);
            if (req == null) {
                continue;
            }
            log.info("Detected dead NATS subscription [{}], resubscribing", key);
            try {
                Subscription old = activeSubscriptions.remove(key);
                if (old != null) {
                    try {
                        old.cancel();
                    } catch (Exception ignored) {
                        // Ignore errors on cancelling an already-dead subscription
                    }
                }
                // Re-register request since cancel() removes it
                subscriptionRequests.put(key, req);
                Subscription fresh = subscriber.subscribe(req);
                activeSubscriptions.put(key, new TrackedSubscription(fresh, key));
            } catch (Exception e) {
                log.warn("Failed to resubscribe NATS [{}]: {}", key, e.getMessage());
            }
        }
    }

    private static String subscriptionKey(SubscribeRequest r) {
        return r.channel() + ":" + r.groupName() + ":" + r.consumerName();
    }

    /**
     * Subscription wrapper that removes itself from activeSubscriptions on cancel.
     */
    private class TrackedSubscription implements Subscription {

        private final Subscription delegate;
        private final String key;

        TrackedSubscription(Subscription delegate, String key) {
            this.delegate = delegate;
            this.key = key;
        }

        @Override
        public String channel() {
            return delegate.channel();
        }

        @Override
        public String groupName() {
            return delegate.groupName();
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void cancel() {
            delegate.cancel();
            activeSubscriptions.remove(key);
            subscriptionRequests.remove(key);
        }
    }
}
