package dev.simplecore.simplix.core.validator;

import dev.simplecore.simplix.core.validator.dto.CreateUserDto;
import dev.simplecore.simplix.core.validator.dto.UpdateUserDto;
import dev.simplecore.simplix.core.validator.entity.TestUser;
import dev.simplecore.simplix.core.validator.repository.TestUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test configuration for validator testing.
 */
@SpringBootApplication
@EntityScan(basePackages = "dev.simplecore.simplix.core.validator.entity")
@EnableJpaRepositories(basePackages = "dev.simplecore.simplix.core.validator.repository")
@ComponentScan(basePackages = "dev.simplecore.simplix.core.validator")
class ValidatorTestApplication {
}

/**
 * Integration tests for {@link UniqueValidator} and {@link UniqueFieldsValidator}.
 */
@SpringBootTest(classes = ValidatorTestApplication.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Unique Validator Tests")
class UniqueValidatorTest {

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private Validator validator;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("@Unique field-level validation")
    class UniqueAnnotationTest {

        @Test
        @DisplayName("should pass when email is unique")
        void shouldPassWhenEmailIsUnique() {
            // given
            CreateUserDto dto = new CreateUserDto("new@example.com", "newuser", "New User");

            // when
            Set<ConstraintViolation<CreateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when email already exists")
        void shouldFailWhenEmailAlreadyExists() {
            // given
            TestUser existing = new TestUser("existing@example.com", "existinguser", "Existing User");
            userRepository.saveAndFlush(existing);
            entityManager.clear();

            CreateUserDto dto = new CreateUserDto("existing@example.com", "newuser", "New User");

            // when
            Set<ConstraintViolation<CreateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Email already exists");
        }

        @Test
        @DisplayName("should fail when username already exists")
        void shouldFailWhenUsernameAlreadyExists() {
            // given
            TestUser existing = new TestUser("existing@example.com", "existinguser", "Existing User");
            userRepository.saveAndFlush(existing);
            entityManager.clear();

            CreateUserDto dto = new CreateUserDto("new@example.com", "existinguser", "New User");

            // when
            Set<ConstraintViolation<CreateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Username already exists");
        }

        @Test
        @DisplayName("should fail when both email and username already exist")
        void shouldFailWhenBothEmailAndUsernameAlreadyExist() {
            // given
            TestUser existing = new TestUser("existing@example.com", "existinguser", "Existing User");
            userRepository.saveAndFlush(existing);
            entityManager.clear();

            CreateUserDto dto = new CreateUserDto("existing@example.com", "existinguser", "New User");

            // when
            Set<ConstraintViolation<CreateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(2);
        }

        @Test
        @DisplayName("should pass when email is null")
        void shouldPassWhenEmailIsNull() {
            // given
            CreateUserDto dto = new CreateUserDto(null, "newuser", "New User");

            // when
            Set<ConstraintViolation<CreateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when email is empty string")
        void shouldPassWhenEmailIsEmpty() {
            // given
            CreateUserDto dto = new CreateUserDto("", "newuser", "New User");

            // when
            Set<ConstraintViolation<CreateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("@UniqueFields class-level validation")
    class UniqueFieldsAnnotationTest {

        @Test
        @DisplayName("should pass when updating with same email for same user")
        void shouldPassWhenUpdatingSameUser() {
            // given
            TestUser existingUser = new TestUser("existing@example.com", "existinguser", "Existing User");
            userRepository.saveAndFlush(existingUser);
            entityManager.clear();

            UpdateUserDto dto = new UpdateUserDto(
                existingUser.getId(),
                "existing@example.com",  // same email
                "existinguser",          // same username
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when updating with new unique email")
        void shouldPassWhenUpdatingWithNewUniqueEmail() {
            // given
            TestUser existingUser = new TestUser("existing@example.com", "existinguser", "Existing User");
            userRepository.saveAndFlush(existingUser);
            entityManager.clear();

            UpdateUserDto dto = new UpdateUserDto(
                existingUser.getId(),
                "new@example.com",       // new unique email
                "existinguser",          // same username
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when updating with email that belongs to another user")
        void shouldFailWhenUpdatingWithAnotherUsersEmail() {
            // given
            TestUser user1 = new TestUser("user1@example.com", "user1", "User 1");
            TestUser user2 = new TestUser("user2@example.com", "user2", "User 2");
            userRepository.saveAndFlush(user1);
            userRepository.saveAndFlush(user2);
            entityManager.clear();

            // Try to update user2 with user1's email
            UpdateUserDto dto = new UpdateUserDto(
                user2.getId(),
                "user1@example.com",     // user1's email
                "user2",                 // user2's username
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Email already exists");
        }

        @Test
        @DisplayName("should fail when updating with username that belongs to another user")
        void shouldFailWhenUpdatingWithAnotherUsersUsername() {
            // given
            TestUser user1 = new TestUser("user1@example.com", "user1", "User 1");
            TestUser user2 = new TestUser("user2@example.com", "user2", "User 2");
            userRepository.saveAndFlush(user1);
            userRepository.saveAndFlush(user2);
            entityManager.clear();

            // Try to update user2 with user1's username
            UpdateUserDto dto = new UpdateUserDto(
                user2.getId(),
                "user2@example.com",     // user2's email
                "user1",                 // user1's username
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Username already exists");
        }

        @Test
        @DisplayName("should pass when ID is null (create scenario)")
        void shouldPassWhenIdIsNullAndValuesAreUnique() {
            // given
            UpdateUserDto dto = new UpdateUserDto(
                null,                    // no ID - create scenario
                "new@example.com",
                "newuser",
                "New User"
            );

            // when
            Set<ConstraintViolation<UpdateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when ID is null and email already exists")
        void shouldFailWhenIdIsNullAndEmailExists() {
            // given
            TestUser existing = new TestUser("existing@example.com", "existinguser", "Existing User");
            userRepository.saveAndFlush(existing);
            entityManager.clear();

            UpdateUserDto dto = new UpdateUserDto(
                null,                    // no ID - create scenario
                "existing@example.com",  // existing email
                "newuser",
                "New User"
            );

            // when
            Set<ConstraintViolation<UpdateUserDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Email already exists");
        }
    }
}
