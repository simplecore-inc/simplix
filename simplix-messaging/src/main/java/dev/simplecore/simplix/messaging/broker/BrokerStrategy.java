package dev.simplecore.simplix.messaging.broker;

import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;

/**
 * Service Provider Interface for message broker implementations.
 *
 * <p>Each broker (Redis Streams, Kafka, RabbitMQ, Local) provides an implementation
 * of this interface. The active strategy is selected at startup based on configuration.
 *
 * <p>Implementations must be thread-safe.
 */
public interface BrokerStrategy {

    /**
     * Publish a message to the given channel.
     *
     * @param channel the target channel/stream/topic
     * @param payload the binary payload
     * @param headers the message headers
     * @return the publish result with broker-assigned record ID
     */
    PublishResult send(String channel, byte[] payload, MessageHeaders headers);

    /**
     * Subscribe to a channel. The broker implementation manages the consumer lifecycle.
     *
     * @param request the subscription parameters
     * @return a handle to manage the subscription
     */
    Subscription subscribe(SubscribeRequest request);

    /**
     * Ensure the consumer group exists for the given channel.
     * This operation is idempotent.
     *
     * @param channel   the channel name
     * @param groupName the consumer group name
     */
    void ensureConsumerGroup(String channel, String groupName);

    /**
     * Acknowledge a message as successfully processed.
     *
     * @param channel   the channel name
     * @param groupName the consumer group name
     * @param messageId the broker message ID to acknowledge
     */
    void acknowledge(String channel, String groupName, String messageId);

    /**
     * Return the capabilities of this broker.
     */
    BrokerCapabilities capabilities();

    /**
     * Initialize the broker connection and resources.
     */
    void initialize();

    /**
     * Shut down the broker, releasing all resources.
     */
    void shutdown();

    /**
     * Check whether the broker is ready to accept operations.
     */
    boolean isReady();

    /**
     * Return the broker name (e.g., "redis", "kafka", "local").
     */
    String name();
}
