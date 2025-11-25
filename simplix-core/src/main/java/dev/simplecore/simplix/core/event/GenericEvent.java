package dev.simplecore.simplix.core.event;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic implementation of Event for general-purpose event publishing.
 * This class can be used for any event type without requiring domain-specific entities.
 * <p>
 * Example usage:
 * <pre>
 * GenericEvent event = GenericEvent.builder()
 *     .eventType("USER_LOGIN")
 *     .aggregateId(userId)
 *     .aggregateType("User")
 *     .payload(Map.of("username", "john", "loginTime", Instant.now()))
 *     .build();
 *
 * eventManager.publish(event);
 * </pre>
 */
public class GenericEvent implements Event {

    private final String eventId;
    private final String eventType;
    private final String aggregateId;
    private final Instant occurredAt;
    private final String userId;
    private final String tenantId;
    private final Map<String, Object> metadata;
    private final Object payload;

    private GenericEvent(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.eventType = builder.eventType;
        this.aggregateId = builder.aggregateId;
        this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
        this.userId = builder.userId;
        this.tenantId = builder.tenantId;
        this.metadata = builder.metadata != null ? builder.metadata : new HashMap<>();
        this.payload = builder.payload;
    }

    @Override
    public String getEventId() {
        return eventId;
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

    /**
     * Create a new builder for GenericEvent
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for GenericEvent
     */
    public static class Builder {
        private String eventId;
        private String eventType;
        private String aggregateId;
        private Instant occurredAt;
        private String userId;
        private String tenantId;
        private Map<String, Object> metadata;
        private Object payload;

        private Builder() {
        }

        /**
         * Set the event ID (auto-generated if not set)
         */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * Set the event type (required)
         */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * Set the aggregate ID (required)
         */
        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        /**
         * Set the aggregate type (optional, stored in metadata)
         */
        public Builder aggregateType(String aggregateType) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put("aggregateType", aggregateType);
            return this;
        }

        /**
         * Set when the event occurred (auto-generated if not set)
         */
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        /**
         * Set the user who triggered the event (optional)
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Set the tenant context (optional)
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Set event metadata (optional)
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Add a single metadata entry
         */
        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Set the event payload (optional)
         */
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Build the GenericEvent
         */
        public GenericEvent build() {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType is required");
            }
            if (aggregateId == null || aggregateId.isBlank()) {
                throw new IllegalArgumentException("aggregateId is required");
            }
            return new GenericEvent(this);
        }
    }

    @Override
    public String toString() {
        return "GenericEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", occurredAt=" + occurredAt +
                ", userId='" + userId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
}
