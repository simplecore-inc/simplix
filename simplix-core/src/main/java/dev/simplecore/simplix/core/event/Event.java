package dev.simplecore.simplix.core.event;

import java.time.Instant;
import java.util.Map;

/**
 * Core Event Interface
 * Base interface for all events in the system
 */
public interface Event {

    /**
     * Get the unique identifier of the event
     */
    String getEventId();

    /**
     * Get the type/name of the event
     */
    String getEventType();

    /**
     * Get the aggregate ID this event relates to
     */
    String getAggregateId();

    /**
     * Get the timestamp when the event occurred
     */
    Instant getOccurredAt();

    /**
     * Get the user who triggered the event (optional)
     */
    String getUserId();

    /**
     * Get the tenant context for the event (optional)
     */
    String getTenantId();

    /**
     * Get additional metadata for the event
     */
    Map<String, Object> getMetadata();

    /**
     * Get the event payload/data
     */
    Object getPayload();
}