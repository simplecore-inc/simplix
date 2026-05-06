package dev.simplecore.simplix.messaging.broker;

/**
 * Declares the features supported by a broker implementation.
 *
 * @param consumerGroups     supports consumer groups
 * @param replay             supports historical replay
 * @param ordering           guarantees message ordering within a channel
 * @param deadLetter         natively supports dead letter routing
 * @param scheduledDelivery  supports delayed/scheduled delivery
 * @param nativeDedup        supports publish-time deduplication natively
 * @param nativeRequestReply has a native request/reply primitive
 */
public record BrokerCapabilities(
        boolean consumerGroups,
        boolean replay,
        boolean ordering,
        boolean deadLetter,
        boolean scheduledDelivery,
        boolean nativeDedup,
        boolean nativeRequestReply
) {
}
