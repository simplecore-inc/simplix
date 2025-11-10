package dev.simplecore.simplix.core.event.model;

import java.util.Map;

/**
 * Event message for Spring's event bus.
 * This is a shared model used across modules for event publishing.
 *
 * Uses String-based event types for maximum flexibility and extensibility.
 */
public record EventMessage(
    String aggregateId,
    String aggregateType,
    String eventType,
    Map<String, Object> payload,
    Map<String, Object> metadata
) {
}
