package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.Unique;
import dev.simplecore.simplix.core.validator.entity.TestUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new user with @Unique validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserDto {

    @Unique(entity = TestUser.class, field = "email", message = "Email already exists")
    private String email;

    @Unique(entity = TestUser.class, field = "username", message = "Username already exists")
    private String username;

    private String name;
}
