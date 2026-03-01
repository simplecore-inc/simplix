package dev.simplecore.simplix.messaging.broker;

/**
 * Declares the features supported by a broker implementation.
 *
 * <p>Higher-level components check capabilities before invoking
 * broker-specific operations (e.g., replay is only attempted if
 * the broker advertises {@code replay = true}).
 *
 * @param consumerGroups whether the broker supports consumer groups
 * @param replay         whether historical messages can be replayed from an offset
 * @param ordering       whether message ordering is guaranteed within a channel
 * @param deadLetter     whether the broker natively supports dead letter routing
 */
public record BrokerCapabilities(
        boolean consumerGroups,
        boolean replay,
        boolean ordering,
        boolean deadLetter
) {
}
