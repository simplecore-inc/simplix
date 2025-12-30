package dev.simplecore.simplix.core.validator;

import dev.simplecore.simplix.core.validator.dto.CreateEmployeeDto;
import dev.simplecore.simplix.core.validator.dto.UpdateEmployeeDto;
import dev.simplecore.simplix.core.validator.entity.TestEmployee;
import dev.simplecore.simplix.core.validator.repository.TestEmployeeRepository;
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
 * Integration tests for {@link UniqueCompositeValidator}.
 */
@SpringBootTest(classes = ValidatorTestApplication.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("UniqueComposite Validator Tests")
class UniqueCompositeValidatorTest {

    @Autowired
    private TestEmployeeRepository employeeRepository;

    @Autowired
    private Validator validator;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("Create scenario (no ID)")
    class CreateScenarioTest {

        @Test
        @DisplayName("should pass when composite key is unique")
        void shouldPassWhenCompositeKeyIsUnique() {
            // given
            CreateEmployeeDto dto = new CreateEmployeeDto(1L, "EMP001", "John Doe");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when same employee number in different organization")
        void shouldPassWhenSameEmployeeNumberInDifferentOrganization() {
            // given
            TestEmployee existing = new TestEmployee(1L, "EMP001", "Existing Employee");
            employeeRepository.saveAndFlush(existing);
            entityManager.clear();

            // Same employee number but different organization
            CreateEmployeeDto dto = new CreateEmployeeDto(2L, "EMP001", "John Doe");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when composite key already exists")
        void shouldFailWhenCompositeKeyAlreadyExists() {
            // given
            TestEmployee existing = new TestEmployee(1L, "EMP001", "Existing Employee");
            employeeRepository.saveAndFlush(existing);
            entityManager.clear();

            // Same organization and employee number
            CreateEmployeeDto dto = new CreateEmployeeDto(1L, "EMP001", "John Doe");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Employee number already exists in this organization");
        }

        @Test
        @DisplayName("should pass when organizationId is null")
        void shouldPassWhenOrganizationIdIsNull() {
            // given
            CreateEmployeeDto dto = new CreateEmployeeDto(null, "EMP001", "John Doe");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when employeeNumber is null")
        void shouldPassWhenEmployeeNumberIsNull() {
            // given
            CreateEmployeeDto dto = new CreateEmployeeDto(1L, null, "John Doe");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when employeeNumber is empty")
        void shouldPassWhenEmployeeNumberIsEmpty() {
            // given
            CreateEmployeeDto dto = new CreateEmployeeDto(1L, "", "John Doe");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Update scenario (with ID)")
    class UpdateScenarioTest {

        @Test
        @DisplayName("should pass when updating with same composite key for same employee")
        void shouldPassWhenUpdatingSameEmployee() {
            // given
            TestEmployee existing = new TestEmployee(1L, "EMP001", "Existing Employee");
            employeeRepository.saveAndFlush(existing);
            entityManager.clear();

            UpdateEmployeeDto dto = new UpdateEmployeeDto(
                existing.getId(),
                1L,          // same organization
                "EMP001",    // same employee number
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when updating to new unique composite key")
        void shouldPassWhenUpdatingToNewUniqueCompositeKey() {
            // given
            TestEmployee existing = new TestEmployee(1L, "EMP001", "Existing Employee");
            employeeRepository.saveAndFlush(existing);
            entityManager.clear();

            UpdateEmployeeDto dto = new UpdateEmployeeDto(
                existing.getId(),
                1L,          // same organization
                "EMP002",    // new employee number
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when updating with composite key that belongs to another employee")
        void shouldFailWhenUpdatingWithAnotherEmployeesCompositeKey() {
            // given
            TestEmployee emp1 = new TestEmployee(1L, "EMP001", "Employee 1");
            TestEmployee emp2 = new TestEmployee(1L, "EMP002", "Employee 2");
            employeeRepository.saveAndFlush(emp1);
            employeeRepository.saveAndFlush(emp2);
            entityManager.clear();

            // Try to update emp2 with emp1's composite key
            UpdateEmployeeDto dto = new UpdateEmployeeDto(
                emp2.getId(),
                1L,          // same organization
                "EMP001",    // emp1's employee number
                "Updated Name"
            );

            // when
            Set<ConstraintViolation<UpdateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Employee number already exists in this organization");
        }

        @Test
        @DisplayName("should pass when moving to different organization with same employee number")
        void shouldPassWhenMovingToDifferentOrganization() {
            // given
            TestEmployee emp1 = new TestEmployee(1L, "EMP001", "Employee 1");
            employeeRepository.saveAndFlush(emp1);
            entityManager.clear();

            // Move to different organization
            UpdateEmployeeDto dto = new UpdateEmployeeDto(
                emp1.getId(),
                2L,          // different organization
                "EMP001",    // same employee number
                "Employee 1"
            );

            // when
            Set<ConstraintViolation<UpdateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass when ID is null and composite key is unique")
        void shouldPassWhenIdIsNullAndCompositeKeyIsUnique() {
            // given
            UpdateEmployeeDto dto = new UpdateEmployeeDto(
                null,        // no ID
                1L,
                "EMP001",
                "New Employee"
            );

            // when
            Set<ConstraintViolation<UpdateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when ID is null and composite key already exists")
        void shouldFailWhenIdIsNullAndCompositeKeyExists() {
            // given
            TestEmployee existing = new TestEmployee(1L, "EMP001", "Existing Employee");
            employeeRepository.saveAndFlush(existing);
            entityManager.clear();

            UpdateEmployeeDto dto = new UpdateEmployeeDto(
                null,        // no ID - create scenario
                1L,
                "EMP001",
                "New Employee"
            );

            // when
            Set<ConstraintViolation<UpdateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Multiple organizations")
    class MultipleOrganizationsTest {

        @Test
        @DisplayName("should allow same employee number in different organizations")
        void shouldAllowSameEmployeeNumberInDifferentOrganizations() {
            // given
            TestEmployee emp1 = new TestEmployee(1L, "EMP001", "Org1 Employee");
            TestEmployee emp2 = new TestEmployee(2L, "EMP001", "Org2 Employee");
            employeeRepository.saveAndFlush(emp1);
            employeeRepository.saveAndFlush(emp2);
            entityManager.clear();

            // Create in third organization with same employee number
            CreateEmployeeDto dto = new CreateEmployeeDto(3L, "EMP001", "Org3 Employee");

            // when
            Set<ConstraintViolation<CreateEmployeeDto>> violations = validator.validate(dto);

            // then
            assertThat(violations).isEmpty();
        }
    }
}