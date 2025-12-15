package dev.simplecore.simplix.core.validator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a unique field constraint for use with {@link UniqueFields}.
 * <p>
 * This annotation is used as a repeatable element within {@link UniqueFields}
 * to define multiple unique constraints on a DTO class.
 *
 * @see UniqueFields
 * @see UniqueFieldsValidator
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueField {

    /**
     * The entity class to check for uniqueness.
     *
     * @return entity class
     */
    Class<?> entity();

    /**
     * The field name in the entity to check for uniqueness.
     *
     * @return entity field name
     */
    String field();

    /**
     * The property name in the DTO that contains the value to check.
     *
     * @return DTO property name
     */
    String property();

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
     * The soft delete field name in the entity.
     * <p>
     * When specified, soft-deleted records are excluded from the uniqueness check.
     * Leave empty if the entity does not use soft delete.
     *
     * @return soft delete field name (e.g., "deleted", "deletedAt")
     */
    String softDeleteField() default "";

    /**
     * The type of soft delete field.
     * <p>
     * Determines how to filter out soft-deleted records:
     * <ul>
     *   <li>{@link SoftDeleteType#NONE} - No filtering (default)</li>
     *   <li>{@link SoftDeleteType#BOOLEAN} - Filter where field = false</li>
     *   <li>{@link SoftDeleteType#TIMESTAMP} - Filter where field IS NULL</li>
     * </ul>
     *
     * @return soft delete type
     */
    SoftDeleteType softDeleteType() default SoftDeleteType.NONE;

    /**
     * Error message when validation fails.
     *
     * @return error message
     */
    String message() default "{validation.unique}";
}