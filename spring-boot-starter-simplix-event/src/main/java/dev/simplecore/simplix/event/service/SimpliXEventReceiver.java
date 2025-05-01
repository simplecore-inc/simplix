package dev.simplecore.simplix.event.service;

import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.util.GenericTypeResolver;

/**
 * Interface for event receivers that handle SimpliX message events.
 * The generic type parameter T specifies the expected payload type.
 *
 * @param <T> The type of payload this receiver expects
 */
public interface SimpliXEventReceiver<T> {
    
    /**
     * Called when an event is received on a supported channel.
     * The payload will be automatically converted to the specified type T.
     *
     * @param channelName The name of the channel the event was received on
     * @param event The event containing the payload
     * @param payload The converted payload of type T
     */
    void onEvent(String channelName, SimpliXMessageEvent event, T payload);
    
    /**
     * Returns the channel names that this receiver supports.
     *
     * @return Array of supported channel names
     */
    String[] getSupportedChannels();
    
    /**
     * Returns the expected payload type class.
     *
     * @return The Class object representing the payload type T
     */
    default Class<T> getPayloadType() {
        return GenericTypeResolver.resolveGenericType(this.getClass(), SimpliXEventReceiver.class);
    }
} 