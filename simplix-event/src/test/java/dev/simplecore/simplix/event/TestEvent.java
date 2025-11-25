package dev.simplecore.simplix.event;

import dev.simplecore.simplix.core.event.Event;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Test implementation of Event for unit tests
 */
@Data
@Builder
public class TestEvent implements Event {
    private String id;
    private String eventType;
    private String aggregateId;
    private Instant occurredAt;
    private String userId;
    private String tenantId;
    private Map<String, Object> metadata;
    private Object payload;

    @Override
    public String getEventId() {
        return id;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public Object getPayload() {
        return payload;
    }
}