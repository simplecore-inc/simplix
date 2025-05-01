package dev.simplecore.simplix.event.gateway;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Headers;

import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.constant.SimpliXEventConstants;

import java.util.Map;

/**
 * Gateway interface for sending events through the SimpliX Event system.
 * This interface provides methods to send events with or without additional headers.
 * The gateway automatically routes messages to the configured message broker.
 */
@MessagingGateway(defaultRequestChannel = SimpliXEventConstants.DEFAULT_OUTBOUND_CHANNEL)
public interface SimpliXEventGateway {
    /**
     * Sends an event through the default outbound channel.
     * @param event The event to be sent
     */
    void sendEvent(SimpliXMessageEvent event);

    /**
     * Sends an event with additional headers through the default outbound channel.
     * @param headers Additional headers to be included with the message
     * @param event The event to be sent
     */
    void sendEvent(@Headers Map<String, Object> headers, SimpliXMessageEvent event);
}