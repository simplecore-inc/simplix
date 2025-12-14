package dev.simplecore.simplix.hibernate.cache.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Immutable event published when cache eviction occurs.
 *
 * <p>This class is intentionally immutable to prevent concurrent modification issues
 * in distributed cache scenarios. Use {@link #withNodeId(String)} to create a copy
 * with the sender node ID set.</p>
 *
 * <p>The {@code eventId} field provides idempotency support for retry scenarios (C12 fix).
 * Receivers should track processed eventIds to prevent duplicate processing when
 * network issues cause retry broadcasts.</p>
 *
 * <p><strong>Jackson Deserialization:</strong> Uses @JsonCreator to ensure eventId is
 * preserved during deserialization. The default constructor was removed to prevent
 * accidental eventId regeneration (10th review fix).</p>
 */
@Getter
@Builder(toBuilder = true)
public class CacheEvictionEvent implements Serializable {

    private static final long serialVersionUID = 4L;  // Incremented for JsonCreator change

    /**
     * Unique identifier for this event, used for idempotency during retries.
     * Generated automatically when building the event if not explicitly set.
     */
    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();

    private final String entityClass;
    private final String entityId;
    private final String region;
    private final String operation;
    private final Long timestamp;
    private final String nodeId;

    /**
     * All-args constructor for Jackson deserialization and programmatic creation.
     * Uses @JsonCreator to ensure all fields including eventId are properly deserialized.
     *
     * <p>If eventId is null during deserialization, a new UUID is generated to maintain
     * backward compatibility with events that don't have eventId.</p>
     */
    @JsonCreator
    public CacheEvictionEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("entityClass") String entityClass,
            @JsonProperty("entityId") String entityId,
            @JsonProperty("region") String region,
            @JsonProperty("operation") String operation,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("nodeId") String nodeId) {
        // Preserve original eventId if present, otherwise generate new UUID
        this.eventId = (eventId != null && !eventId.isEmpty()) ? eventId : UUID.randomUUID().toString();
        this.entityClass = entityClass;
        this.entityId = entityId;
        this.region = region;
        this.operation = operation;
        this.timestamp = timestamp;
        this.nodeId = nodeId;
    }

    /**
     * Creates a copy of this event with the specified node ID.
     * This method preserves immutability by returning a new instance.
     *
     * @param nodeId the node ID to set on the copy
     * @return a new CacheEvictionEvent instance with the specified node ID
     */
    public CacheEvictionEvent withNodeId(String nodeId) {
        return this.toBuilder().nodeId(nodeId).build();
    }
}