package dev.simplecore.simplix.messaging.broker;

/**
 * Handle representing an active subscription to a channel.
 * Used to manage the lifecycle of a consumer.
 */
public interface Subscription {

    /**
     * Return the channel this subscription is bound to.
     */
    String channel();

    /**
     * Return the consumer group name, or empty string if ungrouped.
     */
    String groupName();

    /**
     * Check whether this subscription is currently active.
     */
    boolean isActive();

    /**
     * Cancel this subscription. The consumer will stop receiving messages
     * after in-flight processing completes.
     */
    void cancel();
}
