package dev.simplecore.simplix.core.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation to validate multiple composite unique constraints.
 * <p>
 * This annotation is applied at the class level and validates that specified
 * combinations of field values are unique within their respective entity tables.
 * <p>
 * Usage example:
 * <pre>{@code
 * // Employee number unique within organization
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
 *     private String name;
 * }
 *
 * // For UpdateDTO (adds ID exclusion)
 * @UniqueComposites({
 *     @UniqueComposite(
 *         entity = Employee.class,
 *         fields = {"organizationId", "employeeNumber"},
 *         properties = {"organizationId", "employeeNumber"},
 *         idField = "id",
 *         idProperty = "id"
 *     )
 * })
 * public class EmployeeUpdateDTO extends EmployeeCreateDTO {
 *     private Long id;
 * }
 * }</pre>
 *
 * @see UniqueComposite
 * @see UniqueCompositeValidator
 */
@Documented
@Constraint(validatedBy = UniqueCompositeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueComposites {

    /**
     * The composite unique constraints to validate.
     *
     * @return array of composite unique definitions
     */
    UniqueComposite[] value();

    /**
     * Error message when validation fails (used as default).
     *
     * @return error message
     */
    String message() default "{validation.unique.composite}";

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