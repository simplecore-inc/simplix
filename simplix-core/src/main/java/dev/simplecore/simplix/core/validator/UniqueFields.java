package dev.simplecore.simplix.core.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation to validate multiple unique field constraints.
 * <p>
 * This annotation is applied at the class level and validates that specified
 * field values are unique within their respective entity tables. For update
 * operations, it can exclude the current entity from the uniqueness check.
 * <p>
 * Usage example:
 * <pre>{@code
 * // For CreateDTO
 * @UniqueFields({
 *     @UniqueField(entity = CmsChannel.class, field = "channelCode", property = "channelCode"),
 *     @UniqueField(entity = CmsChannel.class, field = "channelName", property = "channelName")
 * })
 * public class CmsChannelCreateDTO {
 *     private String channelCode;
 *     private String channelName;
 * }
 *
 * // For UpdateDTO (inherits from CreateDTO, adds ID exclusion)
 * @UniqueFields({
 *     @UniqueField(entity = CmsChannel.class, field = "channelCode", property = "channelCode",
 *                  idField = "channelId", idProperty = "channelId"),
 *     @UniqueField(entity = CmsChannel.class, field = "channelName", property = "channelName",
 *                  idField = "channelId", idProperty = "channelId")
 * })
 * public class CmsChannelUpdateDTO extends CmsChannelCreateDTO {
 *     private String channelId;
 * }
 * }</pre>
 *
 * @see UniqueField
 * @see UniqueFieldsValidator
 */
@Documented
@Constraint(validatedBy = UniqueFieldsValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueFields {

    /**
     * The unique field constraints to validate.
     *
     * @return array of unique field definitions
     */
    UniqueField[] value();

    /**
     * Error message when validation fails (used as default).
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