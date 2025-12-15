package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.SoftDeleteType;
import dev.simplecore.simplix.core.validator.Unique;
import dev.simplecore.simplix.core.validator.entity.TestUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for testing timestamp-based soft delete unique validation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SoftDeleteTimestampDto {

    @Unique(
        entity = TestUser.class,
        field = "email",
        softDeleteField = "deletedAt",
        softDeleteType = SoftDeleteType.TIMESTAMP,
        message = "Email already exists"
    )
    private String email;

    private String name;
}
