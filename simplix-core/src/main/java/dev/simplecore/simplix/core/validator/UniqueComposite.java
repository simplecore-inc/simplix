package dev.simplecore.simplix.core.validator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a composite unique constraint for use with {@link UniqueComposites}.
 * <p>
 * This annotation validates that a combination of multiple fields is unique
 * within the entity table. This is useful for scenarios like:
 * <ul>
 *   <li>Employee number unique within an organization</li>
 *   <li>Product code unique within a category</li>
 *   <li>Username unique within a tenant</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * @UniqueComposites({
 *     @UniqueComposite(
 *         entity = Employee.class,
 *         fields = {"organizationId", "employeeNumber"},
 *         properties = {"organizationId", "employeeNumber"}
 *     )
 * })
 * public class EmployeeCreateDTO {
 *     private Long organizationId;
 *     private String employeeNumber;
 * }
 * }</pre>
 *
 * @see UniqueComposites
 * @see UniqueCompositeValidator
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueComposite {

    /**
     * The entity class to check for uniqueness.
     *
     * @return entity class
     */
    Class<?> entity();

    /**
     * The field names in the entity that form the composite unique constraint.
     * <p>
     * The order of fields should match the order of {@link #properties()}.
     *
     * @return array of entity field names
     */
    String[] fields();

    /**
     * The property names in the DTO that contain the values to check.
     * <p>
     * The order of properties should match the order of {@link #fields()}.
     *
     * @return array of DTO property names
     */
    String[] properties();

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
     * The property to attach validation error message.
     * <p>
     * When validation fails, the error will be attached to this property.
     * If not specified, defaults to the first property in {@link #properties()}.
     *
     * @return property name for error message
     */
    String errorProperty() default "";

    /**
     * Error message when validation fails.
     *
     * @return error message
     */
    String message() default "{validation.unique.composite}";
}