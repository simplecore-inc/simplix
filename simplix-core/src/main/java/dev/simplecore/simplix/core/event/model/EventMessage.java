package dev.simplecore.simplix.core.event.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Event message for Spring's event bus.
 * This is a shared model used across modules for event publishing.
 * <p>
 * Uses String-based event types for maximum flexibility and extensibility.
 * Supports entity lifecycle events with change tracking via {@code changedProperties}.
 *
 * <p>
 * Usage:
 * <pre>{@code
 * // Backward-compatible factory (5-arg)
 * EventMessage event = EventMessage.of("123", "User", "USER_CREATED", payload, metadata);
 *
 * // With changed properties (6-arg)
 * EventMessage event = EventMessage.of("123", "User", "USER_UPDATED", payload, metadata, changedProps);
 *
 * // Full constructor
 * EventMessage event = new EventMessage("123", "User", "USER_UPDATED", payload, metadata, changedProps, Instant.now());
 * }</pre>
 */
public record EventMessage(
    String aggregateId,
    String aggregateType,
    String eventType,
    Map<String, Object> payload,
    Map<String, Object> metadata,
    Set<String> changedProperties,
    Instant occurredAt
) {

    // --- Factory methods (backward compatible) ---

    /**
     * Create an EventMessage without change tracking (backward compatible with original 5-field signature).
     *
     * @param aggregateId   the aggregate identifier
     * @param aggregateType the aggregate type name
     * @param eventType     the event type string
     * @param payload       event payload data
     * @param metadata      event metadata
     * @return a new EventMessage with null changedProperties and current timestamp
     */
    public static EventMessage of(String aggregateId, String aggregateType,
            String eventType, Map<String, Object> payload, Map<String, Object> metadata) {
        return new EventMessage(aggregateId, aggregateType, eventType,
            payload, metadata, null, Instant.now());
    }

    /**
     * Create an EventMessage with change tracking.
     *
     * @param aggregateId       the aggregate identifier
     * @param aggregateType     the aggregate type name
     * @param eventType         the event type string
     * @param payload           event payload data
     * @param metadata          event metadata
     * @param changedProperties set of property names that changed (for UPDATE events)
     * @return a new EventMessage with specified changedProperties and current timestamp
     */
    public static EventMessage of(String aggregateId, String aggregateType,
            String eventType, Map<String, Object> payload, Map<String, Object> metadata,
            Set<String> changedProperties) {
        return new EventMessage(aggregateId, aggregateType, eventType,
            payload, metadata, changedProperties, Instant.now());
    }

    // --- Payload helpers ---

    /**
     * Get a String value from the payload.
     */
    public String payloadString(String key) {
        if (payload == null) return null;
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a Long value from the payload.
     */
    public Long payloadLong(String key) {
        if (payload == null) return null;
        Object value = payload.get(key);
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * Get an Integer value from the payload.
     */
    public Integer payloadInt(String key) {
        if (payload == null) return null;
        Object value = payload.get(key);
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * Get a list value from the payload.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> payloadList(String key) {
        if (payload == null) return Collections.emptyList();
        Object value = payload.get(key);
        if (value instanceof List<?> list) return (List<T>) list;
        return Collections.emptyList();
    }

    // --- Metadata helpers ---

    /**
     * Get the actor ID from metadata.
     */
    public String actor() {
        return metadataString("actorId");
    }

    /**
     * Get a String value from metadata.
     */
    public String metadataString(String key) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Check if this event was caused by a soft delete operation.
     */
    public boolean isSoftDelete() {
        return metadata != null && Boolean.TRUE.equals(metadata.get("softDelete"));
    }

    // --- Type checks ---

    /**
     * Check if this event matches the given event type.
     */
    public boolean isType(String type) {
        return type != null && type.equals(eventType);
    }

    /**
     * Check if this event belongs to the given aggregate type.
     */
    public boolean isAggregate(String type) {
        return type != null && type.equals(aggregateType);
    }

    // --- Changed property checks ---

    /**
     * Check if a specific property was changed in this update event.
     *
     * @param property the property name to check
     * @return true if the property was changed, false if not or if changedProperties is null
     */
    public boolean hasChangedProperty(String property) {
        return changedProperties != null && changedProperties.contains(property);
    }

    /**
     * Check if any of the given properties were changed in this update event.
     *
     * @param properties property names to check
     * @return true if at least one property was changed
     */
    public boolean hasAnyChangedProperty(String... properties) {
        if (changedProperties == null) return false;
        return Arrays.stream(properties).anyMatch(changedProperties::contains);
    }
}
