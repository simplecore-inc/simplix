package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Streams implementation of {@link BrokerStrategy}.
 *
 * <p>Delegates publishing, subscribing, and consumer group management to
 * specialized components:
 * <ul>
 *   <li>{@link RedisStreamPublisher} - message publishing with Base64 encoding</li>
 *   <li>{@link RedisStreamSubscriber} - consumer group based consumption with PEL recovery</li>
 *   <li>{@link RedisConsumerGroupManager} - consumer group lifecycle management</li>
 * </ul>
 *
 * <p>Thread-safe. Tracks active subscriptions via {@link ConcurrentHashMap}
 * for clean shutdown.
 */
@Slf4j
public class RedisBrokerStrategy implements BrokerStrategy {

    private static final BrokerCapabilities CAPABILITIES =
            new BrokerCapabilities(true, true, true, false);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final RedisConsumerGroupManager consumerGroupManager;
    private final RedisStreamPublisher publisher;
    private final RedisStreamSubscriber subscriber;
    private final Duration pendingCheckInterval;
    private final Duration claimMinIdleTime;
    private final ConcurrentHashMap<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubscribeRequest> subscriptionRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private ScheduledExecutorService pelRecoveryScheduler;

    /**
     * Create a Redis broker strategy with periodic PEL recovery.
     *
     * @param redisTemplate        the Redis template for direct operations
     * @param keyPrefix            prefix for all stream keys
     * @param consumerGroupManager the consumer group manager
     * @param publisher            the stream publisher
     * @param subscriber           the stream subscriber
     * @param pendingCheckInterval interval for periodic PEL recovery checks
     * @param claimMinIdleTime     minimum idle time before claiming stuck messages
     */
    public RedisBrokerStrategy(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            RedisConsumerGroupManager consumerGroupManager,
            RedisStreamPublisher publisher,
            RedisStreamSubscriber subscriber,
            Duration pendingCheckInterval,
            Duration claimMinIdleTime) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.consumerGroupManager = consumerGroupManager;
        this.publisher = publisher;
        this.subscriber = subscriber;
        this.pendingCheckInterval = pendingCheckInterval;
        this.claimMinIdleTime = claimMinIdleTime;
    }

    /**
     * Create a Redis broker strategy without periodic PEL recovery.
     *
     * @param redisTemplate        the Redis template for direct operations
     * @param keyPrefix            prefix for all stream keys
     * @param consumerGroupManager the consumer group manager
     * @param publisher            the stream publisher
     * @param subscriber           the stream subscriber
     */
    public RedisBrokerStrategy(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            RedisConsumerGroupManager consumerGroupManager,
            RedisStreamPublisher publisher,
            RedisStreamSubscriber subscriber) {
        this(redisTemplate, keyPrefix, consumerGroupManager, publisher, subscriber, null, null);
    }

    @Override
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        return publisher.send(channel, payload, headers);
    }

    @Override
    public Subscription subscribe(SubscribeRequest request) {
        // Diagnostic: check stream state before subscription
        String streamKey = keyPrefix + request.channel();
        try {
            Long streamLen = redisTemplate.opsForStream().size(streamKey);
            log.info("Pre-subscribe diagnostic [stream={}, group={}, consumer={}, streamLen={}]",
                    streamKey, request.groupName(), request.consumerName(), streamLen);
        } catch (Exception e) {
            log.debug("Pre-subscribe diagnostic failed for stream '{}': {}", streamKey, e.getMessage());
        }

        // Recover pending messages before starting the live subscription
        subscriber.recoverPendingMessages(request);

        Subscription subscription = subscriber.subscribe(request);
        String subscriptionKey = request.channel() + ":" + request.groupName() + ":" + request.consumerName();
        subscriptionRequests.put(subscriptionKey, request);

        Subscription trackedSubscription = new TrackedSubscription(subscription, subscriptionKey);
        activeSubscriptions.put(subscriptionKey, trackedSubscription);

        log.info("Registered subscription [key={}]", subscriptionKey);
        return trackedSubscription;
    }

    @Override
    public void ensureConsumerGroup(String channel, String groupName) {
        consumerGroupManager.ensureConsumerGroup(channel, groupName);
    }

    @Override
    public void acknowledge(String channel, String groupName, String messageId) {
        String streamKey = keyPrefix + channel;
        redisTemplate.opsForStream().acknowledge(streamKey, groupName, messageId);
        log.debug("Acknowledged message '{}' on stream '{}' [group={}]",
                messageId, streamKey, groupName);
    }

    @Override
    public BrokerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void initialize() {
        ready.set(true);

        if (pendingCheckInterval != null && !pendingCheckInterval.isZero() && !pendingCheckInterval.isNegative()) {
            pelRecoveryScheduler = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "simplix-pel-recovery"));
            pelRecoveryScheduler.scheduleAtFixedRate(
                    this::recoverAllPendingMessages,
                    pendingCheckInterval.toMillis(),
                    pendingCheckInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
            log.info("PEL recovery scheduler started [interval={}]", pendingCheckInterval);
        }

        log.info("Redis broker strategy initialized [keyPrefix={}]", keyPrefix);
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Redis broker strategy, cancelling {} active subscription(s)",
                activeSubscriptions.size());

        if (pelRecoveryScheduler != null) {
            pelRecoveryScheduler.shutdown();
            try {
                if (!pelRecoveryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    pelRecoveryScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                pelRecoveryScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        activeSubscriptions.forEach((key, subscription) -> {
            try {
                if (subscription.isActive()) {
                    subscription.cancel();
                    log.debug("Cancelled subscription [key={}]", key);
                }
            } catch (Exception e) {
                log.warn("Error cancelling subscription [key={}]: {}", key, e.getMessage());
            }
        });

        activeSubscriptions.clear();
        subscriptionRequests.clear();
        ready.set(false);
        log.info("Redis broker strategy shut down");
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public String name() {
        return "redis";
    }

    private void recoverAllPendingMessages() {
        for (Map.Entry<String, SubscribeRequest> entry : subscriptionRequests.entrySet()) {
            SubscribeRequest request = entry.getValue();
            try {
                // Re-ensure consumer group exists (handles stream deletion/recreation)
                consumerGroupManager.ensureConsumerGroup(request.channel(), request.groupName());
            } catch (Exception e) {
                log.debug("Consumer group ensure failed for [key={}]: {}", entry.getKey(), e.getMessage());
            }
            try {
                subscriber.recoverPendingMessages(request);
                if (claimMinIdleTime != null && !claimMinIdleTime.isZero()) {
                    subscriber.autoClaimStuckMessages(request, claimMinIdleTime);
                }
            } catch (Exception e) {
                log.warn("Periodic PEL recovery failed for [key={}]: {}", entry.getKey(), e.getMessage());
            }
        }

        // Resubscribe dead subscriptions
        for (Map.Entry<String, Subscription> entry : activeSubscriptions.entrySet()) {
            if (entry.getValue().isActive()) continue;

            String key = entry.getKey();
            SubscribeRequest request = subscriptionRequests.get(key);
            if (request == null) continue;

            log.info("Detected dead subscription [key={}], attempting resubscribe", key);
            try {
                consumerGroupManager.ensureConsumerGroup(request.channel(), request.groupName());
                subscriber.recoverPendingMessages(request);
                Subscription newSub = subscriber.subscribe(request);
                Subscription tracked = new TrackedSubscription(newSub, key);
                activeSubscriptions.put(key, tracked);
                log.info("Successfully resubscribed [key={}]", key);
            } catch (Exception e) {
                log.warn("Resubscribe failed for [key={}]: {}", key, e.getMessage());
            }
        }
    }

    /**
     * Subscription wrapper that removes itself from activeSubscriptions on cancel.
     */
    private class TrackedSubscription implements Subscription {

        private final Subscription delegate;
        private final String subscriptionKey;

        TrackedSubscription(Subscription delegate, String subscriptionKey) {
            this.delegate = delegate;
            this.subscriptionKey = subscriptionKey;
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
            activeSubscriptions.remove(subscriptionKey);
            subscriptionRequests.remove(subscriptionKey);
        }
    }
}
