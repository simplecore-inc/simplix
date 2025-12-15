package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.SoftDeleteType;
import dev.simplecore.simplix.core.validator.Unique;
import dev.simplecore.simplix.core.validator.entity.TestUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for testing boolean-based soft delete unique validation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SoftDeleteBooleanDto {

    @Unique(
        entity = TestUser.class,
        field = "email",
        softDeleteField = "deleted",
        softDeleteType = SoftDeleteType.BOOLEAN,
        message = "Email already exists"
    )
    private String email;

    private String name;
}
