package dev.simplecore.simplix.event.spi;

import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;
import java.util.function.Consumer;

/**
 * Message Broker Adapter SPI
 * <p>
 * This interface defines the contract for message broker implementations.
 * Implementations should handle the specifics of different messaging systems
 * while maintaining consistent behavior.
 */
public interface MessageBrokerAdapter {
    /**
     * Sends an event to the specified channel
     *
     * @param event The event to send
     */
    void send(SimpliXMessageEvent event);

    /**
     * Subscribes to a channel for receiving events
     *
     * @param channel The channel to subscribe to
     * @param handler The handler for received events
     */
    void subscribe(String channel, Consumer<SimpliXMessageEvent> handler);

    /**
     * Subscribes to a channel using an event receiver
     *
     * @param channel The channel to subscribe to
     * @param receiver The event receiver to handle events
     */
    void subscribe(String channel, SimpliXEventReceiver<?> receiver);

    /**
     * Unsubscribes from a channel
     *
     * @param channel The channel to unsubscribe from
     * @param receiver The event receiver to unsubscribe
     */
    void unsubscribe(String channel, SimpliXEventReceiver<?> receiver);

    /**
     * Cleanup resources when shutting down
     */
    default void cleanup() {
        // Default implementation does nothing
    }

    /**
     * Sends an event to a specific channel
     *
     * @param channelName The channel to send to
     * @param message The message to send
     */
    default void send(String channelName, SimpliXMessageEvent message) {
        message.setChannelName(channelName);
        send(message);
    }
} 