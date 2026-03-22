package dev.simplecore.simplix.stream.eventsource;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.Set;

/**
 * Handles events from simplix-event and broadcasts to stream subscribers.
 * <p>
 * This handler bridges the simplix-event system with simplix-stream by:
 * <ol>
 *   <li>Listening for all Event instances via Spring's @EventListener</li>
 *   <li>Finding the matching SimpliXStreamEventSource for the event type</li>
 *   <li>Extracting subscription parameters and data from the event</li>
 *   <li>Broadcasting to all subscribers matching the subscription key</li>
 * </ol>
 * <p>
 * Unlike the polling-based SimpliXStreamDataCollector approach, this provides immediate
 * push notification when events occur, resulting in lower latency.
 */
@Slf4j
@RequiredArgsConstructor
public class EventStreamHandler {

    private final SimpliXStreamEventSourceRegistry eventSourceRegistry;
    private final EventSubscriberRegistry subscriberRegistry;
    private final BroadcastService broadcastService;

    /**
     * Handle incoming events from simplix-event.
     * <p>
     * This method is called by Spring's ApplicationEventPublisher when an Event
     * is published. It routes the event to the appropriate SimpliXStreamEventSource
     * and broadcasts to all matching subscribers.
     *
     * @param event the event to handle
     */
    @EventListener
    public void onEvent(Event event) {
        if (event == null) {
            return;
        }

        String eventType = event.getEventType();
        if (eventType == null) {
            log.trace("Ignoring event with null eventType: {}", event.getEventId());
            return;
        }

        // Find the event source for this event type
        SimpliXStreamEventSource source = eventSourceRegistry.findByEventType(eventType).orElse(null);
        if (source == null) {
            // No event source registered for this event type, ignore silently
            log.trace("No event source for eventType: {}", eventType);
            return;
        }

        // Check if the source supports this specific event
        Object payload = event.getPayload();
        if (!source.supports(eventType, payload)) {
            log.trace("Event source does not support this event: {}", eventType);
            return;
        }

        try {
            processEvent(source, event);
        } catch (Exception e) {
            log.error("Failed to process event: eventType={}, eventId={}",
                    eventType, event.getEventId(), e);
        }
    }

    /**
     * Process an event using the given event source.
     */
    private void processEvent(SimpliXStreamEventSource source, Event event) {
        Object payload = event.getPayload();

        // Extract parameters from the event payload
        Map<String, Object> params = source.extractParams(payload);
        if (params == null) {
            params = Map.of();
        }

        // Create subscription key
        SubscriptionKey key = SubscriptionKey.of(source.getResource(), params);

        // Get subscribers: try exact key match first, then fall back to resource-level broadcast.
        // Event params (e.g., controllerId, severity) are routing criteria,
        // while subscriber params (e.g., timezone) are metadata — they use different key spaces.
        Set<String> subscribers = subscriberRegistry.getSubscribers(key);
        if (subscribers.isEmpty()) {
            subscribers = subscriberRegistry.getSubscribersByResource(source.getResource());
        }
        if (subscribers.isEmpty()) {
            log.trace("No subscribers for resource: {}", source.getResource());
            return;
        }

        // Extract data from the event payload
        Object data = source.extractData(payload);
        if (data == null) {
            log.trace("Event source returned null data for key: {}", key.toKeyString());
            return;
        }

        // Create and broadcast the message
        StreamMessage message = StreamMessage.data(key, data);
        broadcastService.broadcast(key, message, subscribers);

//        log.debug("Event broadcast: eventType={}, resource={}, subscribers={}",
//                event.getEventType(), source.getResource(), subscribers.size());
    }

    /**
     * Add a subscriber for event-based streaming.
     * <p>
     * Called when a client subscribes to an event-based resource.
     *
     * @param key       the subscription key
     * @param sessionId the subscriber session ID
     */
    public void addSubscriber(SubscriptionKey key, String sessionId) {
        subscriberRegistry.addSubscriber(key, sessionId);
    }

    /**
     * Remove a subscriber from event-based streaming.
     * <p>
     * Called when a client unsubscribes from an event-based resource.
     *
     * @param key       the subscription key
     * @param sessionId the subscriber session ID
     */
    public void removeSubscriber(SubscriptionKey key, String sessionId) {
        subscriberRegistry.removeSubscriber(key, sessionId);
    }

    /**
     * Remove a subscriber from all event-based subscriptions.
     * <p>
     * Called when a session disconnects.
     *
     * @param sessionId the subscriber session ID
     */
    public void removeSubscriberFromAll(String sessionId) {
        subscriberRegistry.removeSubscriberFromAll(sessionId);
    }

    /**
     * Get subscriber count for a subscription key.
     *
     * @param key the subscription key
     * @return the subscriber count
     */
    public int getSubscriberCount(SubscriptionKey key) {
        return subscriberRegistry.getSubscriberCount(key);
    }

    /**
     * Check if a resource is event-based.
     *
     * @param resource the resource name
     * @return true if the resource has a SimpliXStreamEventSource
     */
    public boolean isEventBasedResource(String resource) {
        return eventSourceRegistry.hasEventSource(resource);
    }
}
