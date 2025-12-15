package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.SoftDeleteType;
import dev.simplecore.simplix.core.validator.UniqueField;
import dev.simplecore.simplix.core.validator.UniqueFields;
import dev.simplecore.simplix.core.validator.entity.TestUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for testing class-level unique validation with soft delete support.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@UniqueFields({
    @UniqueField(
        entity = TestUser.class,
        field = "email",
        property = "email",
        idField = "id",
        idProperty = "id",
        softDeleteField = "deleted",
        softDeleteType = SoftDeleteType.BOOLEAN,
        message = "Email already exists"
    ),
    @UniqueField(
        entity = TestUser.class,
        field = "username",
        property = "username",
        idField = "id",
        idProperty = "id",
        softDeleteField = "deletedAt",
        softDeleteType = SoftDeleteType.TIMESTAMP,
        message = "Username already exists"
    )
})
public class SoftDeleteFieldsDto {

    private Long id;
    private String email;
    private String username;
    private String name;
}
