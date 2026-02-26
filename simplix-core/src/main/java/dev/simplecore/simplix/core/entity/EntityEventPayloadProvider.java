package dev.simplecore.simplix.core.entity;

import java.util.Collections;
import java.util.Map;

/**
 * Interface for entities that provide custom payload data for entity lifecycle events.
 * <p>
 * When an entity implements this interface, the {@code EntityEventPublishingListener}
 * will include the returned data in the event payload alongside the default fields
 * (entity ID and class name).
 *
 * <p>
 * Usage:
 * <pre>{@code
 * @Entity
 * @EntityEventConfig(onCreate = "ORG_CREATED")
 * public class Organization extends BaseEntity implements EntityEventPayloadProvider {
 *
 *     @Override
 *     public Map<String, Object> getEventPayloadData() {
 *         return Map.of(
 *             "name", getName(),
 *             "parentId", getParentId()
 *         );
 *     }
 * }
 * }</pre>
 */
public interface EntityEventPayloadProvider {

    /**
     * Return custom payload data to include in entity lifecycle events.
     * <p>
     * The returned map is merged into the event payload. Default fields
     * ({@code id}, {@code className}) are always included regardless.
     *
     * @return custom payload data, or empty map if none
     */
    default Map<String, Object> getEventPayloadData() {
        return Collections.emptyMap();
    }
}
