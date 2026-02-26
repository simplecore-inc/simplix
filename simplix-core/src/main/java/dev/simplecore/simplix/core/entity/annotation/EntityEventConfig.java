package dev.simplecore.simplix.core.entity.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures entity lifecycle event publishing for JPA entities.
 * <p>
 * Entities annotated with this annotation will automatically publish events
 * via Spring's {@code ApplicationEventPublisher} when they are created, updated, or deleted.
 * The published event is an {@code EventMessage} record.
 * <p>
 * Requires {@code EntityEventPublishingListener} to be registered as an {@code @EntityListeners}
 * on the entity (or on a base entity class).
 *
 * <p>
 * Usage:
 * <pre>{@code
 * @Entity
 * @EntityEventConfig(
 *     onCreate = "USER_CREATED",
 *     onUpdate = "USER_UPDATED",
 *     onDelete = "USER_DELETED",
 *     ignoreProperties = {"updatedAt", "version"}
 * )
 * public class User extends BaseEntity { ... }
 * }</pre>
 *
 * <p>
 * Property filtering:
 * <ul>
 *   <li>{@code ignoreProperties}: Update events are NOT fired when ONLY these properties change.</li>
 *   <li>{@code watchProperties}: Update events are ONLY fired when at least one of these properties changes.</li>
 *   <li>These two options are mutually exclusive. Setting both will cause a validation error at startup.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityEventConfig {

    /**
     * Event type string for entity creation events.
     */
    String onCreate() default "ENTITY_CREATED";

    /**
     * Event type string for entity update events.
     */
    String onUpdate() default "ENTITY_UPDATED";

    /**
     * Event type string for entity deletion events.
     */
    String onDelete() default "ENTITY_DELETED";

    /**
     * Whether event publishing is enabled for this entity.
     */
    boolean enabled() default true;

    /**
     * Whether to publish events on entity creation.
     */
    boolean publishCreate() default true;

    /**
     * Whether to publish events on entity update.
     */
    boolean publishUpdate() default true;

    /**
     * Whether to publish events on entity deletion.
     */
    boolean publishDelete() default true;

    /**
     * Properties to ignore when determining if an update event should fire.
     * If ONLY these properties changed, no update event is published.
     * <p>
     * Commonly used to exclude audit fields like {@code updatedAt}, {@code version}, {@code updatedBy}.
     * <p>
     * Mutually exclusive with {@link #watchProperties()}.
     */
    String[] ignoreProperties() default {};

    /**
     * If set, only fire update event when at least one of these properties changes.
     * <p>
     * Mutually exclusive with {@link #ignoreProperties()}.
     */
    String[] watchProperties() default {};
}
