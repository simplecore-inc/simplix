package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentHashMap<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Create a Redis broker strategy.
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
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.consumerGroupManager = consumerGroupManager;
        this.publisher = publisher;
        this.subscriber = subscriber;
    }

    @Override
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        return publisher.send(channel, payload, headers);
    }

    @Override
    public Subscription subscribe(SubscribeRequest request) {
        // Recover pending messages before starting the live subscription
        subscriber.recoverPendingMessages(request);

        Subscription subscription = subscriber.subscribe(request);
        String subscriptionKey = request.channel() + ":" + request.groupName() + ":" + request.consumerName();
        activeSubscriptions.put(subscriptionKey, subscription);

        // Wrap subscription to auto-remove from activeSubscriptions on cancel
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
        log.info("Redis broker strategy initialized [keyPrefix={}]", keyPrefix);
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Redis broker strategy, cancelling {} active subscription(s)",
                activeSubscriptions.size());

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
        }
    }
}
