package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.UniqueField;
import dev.simplecore.simplix.core.validator.UniqueFields;
import dev.simplecore.simplix.core.validator.entity.TestUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating a user with @UniqueFields validation.
 * Supports ID exclusion for update scenarios.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@UniqueFields({
    @UniqueField(
        entity = TestUser.class,
        field = "email",
        property = "email",
        idField = "id",
        idProperty = "id",
        message = "Email already exists"
    ),
    @UniqueField(
        entity = TestUser.class,
        field = "username",
        property = "username",
        idField = "id",
        idProperty = "id",
        message = "Username already exists"
    )
})
public class UpdateUserDto {

    private Long id;

    private String email;

    private String username;

    private String name;
}
