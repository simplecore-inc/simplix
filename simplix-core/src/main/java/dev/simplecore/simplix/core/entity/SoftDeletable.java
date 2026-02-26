package dev.simplecore.simplix.core.entity;

/**
 * Marker interface for entities that use soft delete via {@code @SQLDelete}.
 * <p>
 * When an entity uses {@code @SQLDelete(sql = "UPDATE ... SET deleted = true ...")},
 * JPA fires {@code @PostUpdate} instead of {@code @PostRemove}. The
 * {@code EntityEventPublishingListener} detects this interface and automatically
 * converts the update event to a DELETE event when {@link #isDeleted()} returns true.
 * <p>
 * The resulting event will have {@code metadata.softDelete = true} to distinguish
 * it from hard deletes.
 *
 * <p>
 * Usage:
 * <pre>{@code
 * @Entity
 * @SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")
 * @EntityEventConfig(onDelete = "USER_DELETED")
 * public class User extends BaseEntity implements SoftDeletable {
 *     private boolean deleted = false;
 *
 *     @Override
 *     public boolean isDeleted() {
 *         return deleted;
 *     }
 * }
 * }</pre>
 */
public interface SoftDeletable {

    /**
     * Check if this entity has been soft-deleted.
     *
     * @return true if the entity is in a deleted state
     */
    boolean isDeleted();
}
