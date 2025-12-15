package dev.simplecore.simplix.core.validator;

import dev.simplecore.simplix.core.validator.dto.SoftDeleteBooleanDto;
import dev.simplecore.simplix.core.validator.dto.SoftDeleteFieldsDto;
import dev.simplecore.simplix.core.validator.dto.SoftDeleteTimestampDto;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for soft delete support in {@link UniqueValidator} and {@link UniqueFieldsValidator}.
 */
@SpringBootTest(classes = ValidatorTestApplication.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Unique Validator Soft Delete Tests")
class UniqueValidatorSoftDeleteTest {

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
    @DisplayName("Boolean-based soft delete")
    class BooleanSoftDeleteTest {

        @Test
        @DisplayName("should pass when active record with same email does not exist")
        void shouldPassWhenNoActiveRecordExists() {
            // given
            SoftDeleteBooleanDto dto = new SoftDeleteBooleanDto("new@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteBooleanDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when active record with same email exists")
        void shouldFailWhenActiveRecordExists() {
            // given
            TestUser activeUser = new TestUser("existing@example.com", "activeuser", "Active User");
            activeUser.setDeleted(false);
            userRepository.saveAndFlush(activeUser);
            entityManager.clear();

            SoftDeleteBooleanDto dto = new SoftDeleteBooleanDto("existing@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteBooleanDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Email already exists");
        }

        @Test
        @DisplayName("should pass when soft-deleted record with same email exists (deleted = true)")
        void shouldPassWhenSoftDeletedRecordExists() {
            // given
            TestUser deletedUser = new TestUser("deleted@example.com", "deleteduser", "Deleted User");
            deletedUser.softDeleteBoolean();  // sets deleted = true
            userRepository.saveAndFlush(deletedUser);
            entityManager.clear();

            SoftDeleteBooleanDto dto = new SoftDeleteBooleanDto("deleted@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteBooleanDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when deleted field is null (treated as not deleted)")
        void shouldFailWhenDeletedFieldIsNull() {
            // given
            TestUser userWithNullDeleted = new TestUser("existing@example.com", "user", "User");
            userWithNullDeleted.setDeleted(null);  // null is treated as not deleted
            userRepository.saveAndFlush(userWithNullDeleted);
            entityManager.clear();

            SoftDeleteBooleanDto dto = new SoftDeleteBooleanDto("existing@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteBooleanDto>> violations = validator.validate(dto);

            // then
            // Records with deleted = null are treated as active (not deleted)
            assertThat(violations).hasSize(1);
        }

    }

    @Nested
    @DisplayName("Timestamp-based soft delete")
    class TimestampSoftDeleteTest {

        @Test
        @DisplayName("should pass when active record with same email does not exist")
        void shouldPassWhenNoActiveRecordExists() {
            // given
            SoftDeleteTimestampDto dto = new SoftDeleteTimestampDto("new@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteTimestampDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when active record with same email exists (deletedAt = null)")
        void shouldFailWhenActiveRecordExists() {
            // given
            TestUser activeUser = new TestUser("existing@example.com", "activeuser", "Active User");
            activeUser.setDeletedAt(null);  // not deleted
            userRepository.saveAndFlush(activeUser);
            entityManager.clear();

            SoftDeleteTimestampDto dto = new SoftDeleteTimestampDto("existing@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteTimestampDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Email already exists");
        }

        @Test
        @DisplayName("should pass when soft-deleted record with same email exists (deletedAt is set)")
        void shouldPassWhenSoftDeletedRecordExists() {
            // given
            TestUser deletedUser = new TestUser("deleted@example.com", "deleteduser", "Deleted User");
            deletedUser.softDeleteTimestamp();  // sets deletedAt = now
            userRepository.saveAndFlush(deletedUser);
            entityManager.clear();

            SoftDeleteTimestampDto dto = new SoftDeleteTimestampDto("deleted@example.com", "New User");

            // when
            Set<ConstraintViolation<SoftDeleteTimestampDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

    }

    @Nested
    @DisplayName("@UniqueFields with soft delete")
    class UniqueFieldsSoftDeleteTest {

        @Test
        @DisplayName("should pass when updating self with same values")
        void shouldPassWhenUpdatingSelf() {
            // given
            TestUser user = new TestUser("user@example.com", "testuser", "Test User");
            user.setDeleted(false);
            user.setDeletedAt(null);
            userRepository.saveAndFlush(user);
            entityManager.clear();

            SoftDeleteFieldsDto dto = new SoftDeleteFieldsDto(
                user.getId(),
                "user@example.com",
                "testuser",
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<SoftDeleteFieldsDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when using soft-deleted email (boolean) and username (timestamp)")
        void shouldPassWhenUsingSoftDeletedValues() {
            // given
            TestUser deletedByBoolean = new TestUser("deleted@example.com", "user1", "User 1");
            deletedByBoolean.softDeleteBoolean();
            userRepository.saveAndFlush(deletedByBoolean);

            TestUser deletedByTimestamp = new TestUser("another@example.com", "deleteduser", "User 2");
            deletedByTimestamp.softDeleteTimestamp();
            userRepository.saveAndFlush(deletedByTimestamp);
            entityManager.clear();

            SoftDeleteFieldsDto dto = new SoftDeleteFieldsDto(
                null,
                "deleted@example.com",   // belongs to boolean-deleted user
                "deleteduser",           // belongs to timestamp-deleted user
                "New User"
            );

            // when
            Set<ConstraintViolation<SoftDeleteFieldsDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when using active email")
        void shouldFailWhenUsingActiveEmail() {
            // given
            TestUser activeUser = new TestUser("active@example.com", "activeuser", "Active User");
            activeUser.setDeleted(false);
            userRepository.saveAndFlush(activeUser);
            entityManager.clear();

            SoftDeleteFieldsDto dto = new SoftDeleteFieldsDto(
                null,
                "active@example.com",
                "newuser",
                "New User"
            );

            // when
            Set<ConstraintViolation<SoftDeleteFieldsDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Email already exists");
        }

        @Test
        @DisplayName("should fail when using active username")
        void shouldFailWhenUsingActiveUsername() {
            // given
            TestUser activeUser = new TestUser("active@example.com", "activeuser", "Active User");
            activeUser.setDeletedAt(null);  // not deleted
            userRepository.saveAndFlush(activeUser);
            entityManager.clear();

            SoftDeleteFieldsDto dto = new SoftDeleteFieldsDto(
                null,
                "new@example.com",
                "activeuser",
                "New User"
            );

            // when
            Set<ConstraintViolation<SoftDeleteFieldsDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Username already exists");
        }
    }

    @Nested
    @DisplayName("Static utility method tests")
    class StaticUtilityMethodTest {

        @Test
        @DisplayName("should work with boolean soft delete via static method")
        void shouldWorkWithBooleanSoftDelete() {
            // given
            TestUser deletedUser = new TestUser("deleted@example.com", "deleteduser", "Deleted User");
            deletedUser.softDeleteBoolean();
            userRepository.saveAndFlush(deletedUser);
            entityManager.clear();

            // when
            boolean isUnique = UniqueValidator.isUnique(
                entityManager,
                TestUser.class,
                "email",
                "deleted@example.com",
                "id",
                null,
                "deleted",
                SoftDeleteType.BOOLEAN
            );

            // then
            assertThat(isUnique).isTrue();
        }

        @Test
        @DisplayName("should work with timestamp soft delete via static method")
        void shouldWorkWithTimestampSoftDelete() {
            // given
            TestUser deletedUser = new TestUser("deleted@example.com", "deleteduser", "Deleted User");
            deletedUser.softDeleteTimestamp();
            userRepository.saveAndFlush(deletedUser);
            entityManager.clear();

            // when
            boolean isUnique = UniqueValidator.isUnique(
                entityManager,
                TestUser.class,
                "email",
                "deleted@example.com",
                "id",
                null,
                "deletedAt",
                SoftDeleteType.TIMESTAMP
            );

            // then
            assertThat(isUnique).isTrue();
        }

        @Test
        @DisplayName("should return false for active record via static method")
        void shouldReturnFalseForActiveRecord() {
            // given
            TestUser activeUser = new TestUser("active@example.com", "activeuser", "Active User");
            activeUser.setDeleted(false);
            userRepository.saveAndFlush(activeUser);
            entityManager.clear();

            // when
            boolean isUnique = UniqueValidator.isUnique(
                entityManager,
                TestUser.class,
                "email",
                "active@example.com",
                "id",
                null,
                "deleted",
                SoftDeleteType.BOOLEAN
            );

            // then
            assertThat(isUnique).isFalse();
        }
    }
}
