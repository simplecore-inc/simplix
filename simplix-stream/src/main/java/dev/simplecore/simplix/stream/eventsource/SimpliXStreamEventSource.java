package dev.simplecore.simplix.stream.eventsource;

import java.util.Map;

/**
 * Interface for event-based real-time data sources.
 * <p>
 * Unlike {@link dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector} which polls data periodically,
 * SimpliXStreamEventSource receives events from simplix-event and pushes data immediately to subscribers.
 * This provides lower latency for event-driven data.
 * <p>
 * Each SimpliXStreamEventSource maps an event type to a stream resource. When an event is received,
 * the source extracts parameters and data from the event payload, then the framework broadcasts
 * to all subscribers matching the subscription key.
 * <p>
 * Example implementation:
 * <pre>
 * &#64;Component
 * public class StockPriceEventSource implements SimpliXStreamEventSource {
 *
 *     &#64;Override
 *     public String getResource() {
 *         return "stock-price";  // Client subscription resource name
 *     }
 *
 *     &#64;Override
 *     public String getEventType() {
 *         return "StockPriceChanged";  // simplix-event event type
 *     }
 *
 *     &#64;Override
 *     public Map&lt;String, Object&gt; extractParams(Object payload) {
 *         StockPriceChangedEvent event = (StockPriceChangedEvent) payload;
 *         return Map.of("symbol", event.getSymbol());
 *     }
 *
 *     &#64;Override
 *     public Object extractData(Object payload) {
 *         StockPriceChangedEvent event = (StockPriceChangedEvent) payload;
 *         return Map.of(
 *             "symbol", event.getSymbol(),
 *             "price", event.getPrice(),
 *             "change", event.getChange()
 *         );
 *     }
 * }
 * </pre>
 *
 * @see dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector
 */
public interface SimpliXStreamEventSource {

    /**
     * Get the resource name this source handles.
     * <p>
     * This name is used to match subscription requests to event sources.
     * Clients subscribe to this resource name.
     *
     * @return the resource name (e.g., "stock-price", "order-updates", "notifications")
     */
    String getResource();

    /**
     * Get the event type this source listens for.
     * <p>
     * This should match the eventType from simplix-event's Event interface.
     * When an event with this type is published, this source will handle it.
     *
     * @return the event type (e.g., "StockPriceChanged", "OrderStatusUpdated")
     */
    String getEventType();

    /**
     * Extract subscription parameters from the event payload.
     * <p>
     * These parameters are used to match the event to specific subscriptions.
     * For example, if a client subscribed to stock-price with {symbol: "AAPL"},
     * this method should extract {symbol: "AAPL"} from the event payload
     * to route the event to the correct subscribers.
     *
     * @param payload the event payload (from Event.getPayload())
     * @return the subscription parameters map
     */
    Map<String, Object> extractParams(Object payload);

    /**
     * Extract data to send to clients from the event payload.
     * <p>
     * This method transforms the event payload into the data format
     * that will be sent to subscribed clients.
     *
     * @param payload the event payload (from Event.getPayload())
     * @return the data to send to clients (will be serialized to JSON)
     */
    Object extractData(Object payload);

    /**
     * Validate parameters before subscription.
     * <p>
     * Override this method to validate subscription parameters.
     * Return false to reject the subscription.
     *
     * @param params the subscription parameters
     * @return true if parameters are valid
     */
    default boolean validateParams(Map<String, Object> params) {
        return true;
    }

    /**
     * Get required permission for this resource.
     * <p>
     * Override to specify a Spring Security permission/authority required
     * to subscribe to this resource.
     *
     * @return the required permission, or null if no permission required
     */
    default String getRequiredPermission() {
        return null;
    }

    /**
     * Check if this source supports the given event.
     * <p>
     * Override for more complex event filtering logic beyond just event type matching.
     * By default, returns true if the event type matches.
     *
     * @param eventType the event type
     * @param payload   the event payload
     * @return true if this source should handle the event
     */
    default boolean supports(String eventType, Object payload) {
        return getEventType().equals(eventType);
    }
}
