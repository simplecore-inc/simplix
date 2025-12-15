package dev.simplecore.simplix.core.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a field value is unique within the specified entity table.
 * <p>
 * For update operations, the validator excludes the current entity from the uniqueness check
 * by using the ID field values from both the entity and the DTO.
 * <p>
 * Usage example:
 * <pre>{@code
 * // For CreateDTO (no idProperty needed)
 * @Unique(entity = CmsChannel.class, field = "channelCode")
 * private String channelCode;
 *
 * // For UpdateDTO (with idProperty to exclude self)
 * @Unique(entity = CmsChannel.class, field = "channelCode",
 *         idField = "channelId", idProperty = "channelId")
 * private String channelCode;
 * }</pre>
 *
 * @see UniqueValidator
 */
@Documented
@Constraint(validatedBy = UniqueValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Unique {

    /**
     * The entity class to check for uniqueness.
     *
     * @return entity class
     */
    Class<?> entity();

    /**
     * The field name in the entity to check for uniqueness.
     *
     * @return field name
     */
    String field();

    /**
     * The ID field name in the entity (used to exclude self during update).
     * <p>
     * Default is "id".
     *
     * @return entity ID field name
     */
    String idField() default "id";

    /**
     * The ID property name in the DTO (used to get current ID for update exclusion).
     * <p>
     * If the DTO has an ID value, the validator excludes that record from the check.
     * Leave empty for CreateDTO where no exclusion is needed.
     *
     * @return DTO ID property name
     */
    String idProperty() default "";

    /**
     * Error message when validation fails.
     *
     * @return error message
     */
    String message() default "{validation.unique}";

    /**
     * Validation groups.
     *
     * @return groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for additional metadata.
     *
     * @return payload
     */
    Class<? extends Payload>[] payload() default {};
}