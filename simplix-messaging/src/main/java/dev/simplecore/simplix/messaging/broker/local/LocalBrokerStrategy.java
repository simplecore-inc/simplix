package dev.simplecore.simplix.messaging.broker.local;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory broker implementation for testing without external dependencies.
 *
 * <p>Provides synchronous, same-thread message delivery with consumer group
 * simulation via round-robin distribution. Messages are stored in memory
 * for test assertions via {@link #getPublishedMessages(String)}.
 *
 * <p>Capabilities: consumer groups (simulated), no replay, ordering guaranteed, no dead letter.
 */
@Slf4j
public class LocalBrokerStrategy implements BrokerStrategy {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SubscribeRequest>> subscribers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PublishedMessage>> publishedMessages =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> groupRoundRobin =
            new ConcurrentHashMap<>();
    private volatile boolean ready = false;

    @Override
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        String recordId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();

        // Store for test assertions
        PublishedMessage published = new PublishedMessage(recordId, channel, payload, headers, timestamp);
        publishedMessages.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(published);

        // Build the message envelope
        String messageId = headers.get(MessageHeaders.MESSAGE_ID).orElse(recordId);
        Message<byte[]> message = Message.<byte[]>builder()
                .messageId(messageId)
                .channel(channel)
                .payload(payload)
                .headers(headers)
                .timestamp(timestamp)
                .build();

        // Deliver to subscribers
        CopyOnWriteArrayList<SubscribeRequest> channelSubscribers = subscribers.get(channel);
        if (channelSubscribers != null && !channelSubscribers.isEmpty()) {
            deliverToSubscribers(channel, message, channelSubscribers);
        }

        log.debug("Published message to channel='{}' recordId='{}'", channel, recordId);
        return new PublishResult(recordId, channel, timestamp);
    }

    @Override
    public Subscription subscribe(SubscribeRequest request) {
        subscribers.computeIfAbsent(request.channel(), k -> new CopyOnWriteArrayList<>()).add(request);
        log.debug("Subscribed to channel='{}' group='{}' consumer='{}'",
                request.channel(), request.groupName(), request.consumerName());
        return new LocalSubscription(request.channel(), request.groupName(), request, this);
    }

    @Override
    public void ensureConsumerGroup(String channel, String groupName) {
        // No-op: consumer groups are tracked in memory via subscriber list
        log.debug("Consumer group ensured (no-op): channel='{}' group='{}'", channel, groupName);
    }

    @Override
    public void acknowledge(String channel, String groupName, String messageId) {
        // No-op: no persistence needed for local broker
        log.debug("Acknowledged (no-op): channel='{}' group='{}' messageId='{}'",
                channel, groupName, messageId);
    }

    @Override
    public BrokerCapabilities capabilities() {
        return new BrokerCapabilities(true, false, true, false);
    }

    @Override
    public void initialize() {
        ready = true;
        log.info("LocalBrokerStrategy initialized");
    }

    @Override
    public void shutdown() {
        ready = false;
        subscribers.clear();
        publishedMessages.clear();
        groupRoundRobin.clear();
        log.info("LocalBrokerStrategy shut down");
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String name() {
        return "local";
    }

    // ---------------------------------------------------------------
    // Test assertion helpers
    // ---------------------------------------------------------------

    /**
     * Return all messages published to the given channel.
     * Useful for test assertions.
     *
     * @param channel the channel name
     * @return unmodifiable list of published messages, or empty list if none
     */
    public List<PublishedMessage> getPublishedMessages(String channel) {
        CopyOnWriteArrayList<PublishedMessage> messages = publishedMessages.get(channel);
        return messages != null ? Collections.unmodifiableList(messages) : Collections.emptyList();
    }

    /**
     * Clear all published messages (for test reset).
     */
    public void clearPublishedMessages() {
        publishedMessages.clear();
    }

    // ---------------------------------------------------------------
    // Delivery logic
    // ---------------------------------------------------------------

    private void deliverToSubscribers(String channel, Message<byte[]> message,
                                      List<SubscribeRequest> channelSubscribers) {
        // Partition subscribers by group
        Map<String, List<SubscribeRequest>> groupedSubscribers = new ConcurrentHashMap<>();
        List<SubscribeRequest> ungrouped = new ArrayList<>();

        for (SubscribeRequest subscriber : channelSubscribers) {
            if (subscriber.groupName() == null || subscriber.groupName().isEmpty()) {
                ungrouped.add(subscriber);
            } else {
                groupedSubscribers.computeIfAbsent(subscriber.groupName(), k -> new ArrayList<>())
                        .add(subscriber);
            }
        }

        // Deliver to all ungrouped subscribers (broadcast)
        for (SubscribeRequest subscriber : ungrouped) {
            deliverToListener(subscriber.listener(), message);
        }

        // Deliver to one subscriber per group (round-robin)
        for (Map.Entry<String, List<SubscribeRequest>> entry : groupedSubscribers.entrySet()) {
            String groupKey = channel + ":" + entry.getKey();
            List<SubscribeRequest> groupMembers = entry.getValue();

            AtomicInteger counter = groupRoundRobin.computeIfAbsent(groupKey, k -> new AtomicInteger(0));
            int index = Math.abs(counter.getAndIncrement() % groupMembers.size());
            deliverToListener(groupMembers.get(index).listener(), message);
        }
    }

    private void deliverToListener(dev.simplecore.simplix.messaging.core.MessageListener<byte[]> listener,
                                   Message<byte[]> message) {
        try {
            listener.onMessage(message, MessageAcknowledgment.NOOP);
        } catch (Exception e) {
            log.error("Listener threw exception for message on channel='{}'",
                    message.getChannel(), e);
        }
    }

    /**
     * Remove a subscriber from the channel.
     */
    void removeSubscriber(String channel, SubscribeRequest request) {
        CopyOnWriteArrayList<SubscribeRequest> channelSubscribers = subscribers.get(channel);
        if (channelSubscribers != null) {
            channelSubscribers.remove(request);
        }
    }

    // ---------------------------------------------------------------
    // Inner classes
    // ---------------------------------------------------------------

    /**
     * Record representing a published message stored for test assertions.
     */
    public record PublishedMessage(
            String recordId,
            String channel,
            byte[] payload,
            MessageHeaders headers,
            Instant timestamp
    ) {
    }

    /**
     * Local subscription implementation that tracks active state and
     * supports cancellation by removing from the subscriber list.
     */
    private static class LocalSubscription implements Subscription {

        private final String channel;
        private final String groupName;
        private final SubscribeRequest request;
        private final LocalBrokerStrategy broker;
        private final AtomicBoolean active = new AtomicBoolean(true);

        LocalSubscription(String channel, String groupName,
                          SubscribeRequest request, LocalBrokerStrategy broker) {
            this.channel = channel;
            this.groupName = groupName;
            this.request = request;
            this.broker = broker;
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
            return active.get();
        }

        @Override
        public void cancel() {
            if (active.compareAndSet(true, false)) {
                broker.removeSubscriber(channel, request);
                log.debug("Subscription cancelled: channel='{}' group='{}'", channel, groupName);
            }
        }
    }
}
