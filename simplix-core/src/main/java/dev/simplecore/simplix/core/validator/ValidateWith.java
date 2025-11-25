package dev.simplecore.simplix.core.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for validating field values or DTOs using service methods.
 * This validator delegates the validation logic to a specified service method.
 * 
 * <p>The service method must:
 * <ul>
 *   <li>Return a boolean value</li>
 *   <li>Accept a single parameter matching the annotated field/class type</li>
 *   <li>Be public</li>
 * </ul>
 * 
 * <p>Usage examples:
 * <pre>
 * // Field validation
 * {@literal @}ValidateWith(
 *     service = "userPositionService.validateId",
 *     message = "Invalid position ID"
 * )
 * private String positionId;
 * 
 * // DTO validation
 * {@literal @}ValidateWith(
 *     service = "userAccountService.validateCreateDto",
 *     message = "Invalid user account data"
 * )
 * public class UserAccountCreateDTO {
 *     private String username;
 *     private String position;
 * }
 * </pre>
 * 
 * <p>Service method examples:
 * <pre>
 * // Field validation
 * {@literal @}Service
 * public class UserPositionService {
 *     public boolean validateId(String id) {
 *         return existsById(id);
 *     }
 * }
 * 
 * // DTO validation
 * {@literal @}Service
 * public class UserAccountService {
 *     public boolean validateCreateDto(UserAccountCreateDTO dto) {
 *         return validateBusinessRules(dto);
 *     }
 * }
 * </pre>
 *
 * @see jakarta.validation.Constraint
 * @see jakarta.validation.Valid
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidateWithValidator.class)
public @interface ValidateWith {
    String message() default "Invalid value";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the service and method to use for validation.
     * Format: "serviceBeanName.methodName"
     * 
     * @return the service method path
     */
    String service();
}