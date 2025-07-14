package dev.simplecore.simplix.demo.domain.common.user.repository;

import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRoleRepositoryTest {

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private EntityManager entityManager;
    
    // Faker instance for generating test data
    private Faker faker;
    
    // Counter for generating unique item orders
    private AtomicReference<BigDecimal> itemOrderCounter;
    
    @BeforeEach
    void setUp() {
        faker = new Faker(Locale.US);
        // Use current time + random number to ensure uniqueness across test runs
        BigDecimal initialValue = BigDecimal.valueOf(System.currentTimeMillis() % 10000)
            .add(BigDecimal.valueOf(faker.random().nextInt(1000)));
        itemOrderCounter = new AtomicReference<>(initialValue);
    }
    
    /**
     * Creates a UserRole with random data
     */
    private UserRole createRandomUserRole() {
        UserRole userRole = new UserRole();
        userRole.setName(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        userRole.setRole(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        userRole.setDescription(faker.lorem().sentence(8));
        userRole.setItemOrder(itemOrderCounter.getAndUpdate(current -> current.add(BigDecimal.ONE)));
        return userRole;
    }

    @Test
    @DisplayName("Save and Find UserRole Test")
    void saveAndFindUserRole() {
        // Given
        UserRole newUserRole = createRandomUserRole();

        // When
        UserRole savedUserRole = userRoleRepository.save(newUserRole);
        entityManager.flush();
        entityManager.clear();
        
        Optional<UserRole> foundUserRole = userRoleRepository.findById(savedUserRole.getId());

        // Then
        assertThat(foundUserRole).isPresent();
        assertThat(foundUserRole.get().getName()).isEqualTo(newUserRole.getName());
        assertThat(foundUserRole.get().getRole()).isEqualTo(newUserRole.getRole());
        assertThat(foundUserRole.get().getDescription()).isEqualTo(newUserRole.getDescription());
        assertThat(foundUserRole.get().getItemOrder()).isEqualTo(newUserRole.getItemOrder());
    }

    @Test
    @DisplayName("Find All User Roles Test")
    void findAllUserRoles() {
        // Given
        userRoleRepository.save(createRandomUserRole());
        userRoleRepository.save(createRandomUserRole());
        userRoleRepository.save(createRandomUserRole());
        entityManager.flush();
        entityManager.clear();

        // When
        List<UserRole> roles = userRoleRepository.findAll();

        // Then
        assertThat(roles).hasSize(3);
    }

    @Test
    @DisplayName("Update User Role Test")
    void updateUserRole() {
        // Given
        UserRole userRole = createRandomUserRole();
        UserRole savedRole = userRoleRepository.save(userRole);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<UserRole> foundRole = userRoleRepository.findById(savedRole.getId());
        String updatedName = "Updated " + faker.lorem().word();
        foundRole.get().setName(updatedName);
        userRoleRepository.save(foundRole.get());
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<UserRole> updatedRole = userRoleRepository.findById(savedRole.getId());
        assertThat(updatedRole).isPresent();
        assertThat(updatedRole.get().getName()).isEqualTo(updatedName);
    }

    @Test
    @DisplayName("Delete User Role Test")
    void deleteUserRole() {
        // Given
        UserRole userRole = createRandomUserRole();
        UserRole savedRole = userRoleRepository.save(userRole);
        entityManager.flush();
        entityManager.clear();

        // When
        userRoleRepository.deleteById(savedRole.getId());
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<UserRole> deletedRole = userRoleRepository.findById(savedRole.getId());
        assertThat(deletedRole).isEmpty();
    }

    @Test
    @DisplayName("Unique Name Constraint Test")
    void uniqueNameConstraintTest() {
        // Given
        String duplicateName = "Duplicate " + faker.lorem().word();
        
        UserRole role = createRandomUserRole();
        role.setName(duplicateName);
        userRoleRepository.save(role);
        entityManager.flush();

        // When & Then
        UserRole duplicateRole = createRandomUserRole();
        duplicateRole.setName(duplicateName);
        
        userRoleRepository.save(duplicateRole);
        assertThatThrownBy(() -> entityManager.flush())
            .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("Unique Role Code Constraint Test")
    void uniqueRoleConstraintTest() {
        // Given
        String duplicateRoleCode = faker.lorem().word();
        
        UserRole role = createRandomUserRole();
        role.setRole(duplicateRoleCode);
        userRoleRepository.save(role);
        entityManager.flush();

        // When & Then
        UserRole duplicateRole = createRandomUserRole();
        duplicateRole.setRole(duplicateRoleCode);
        
        userRoleRepository.save(duplicateRole);
        assertThatThrownBy(() -> entityManager.flush())
            .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("Unique ItemOrder Constraint Test")
    void uniqueItemOrderConstraintTest() {
        // Given
        BigDecimal duplicateOrder = BigDecimal.valueOf(999);
        
        UserRole role = createRandomUserRole();
        role.setItemOrder(duplicateOrder);
        userRoleRepository.save(role);
        entityManager.flush();

        // When & Then
        UserRole duplicateRole = createRandomUserRole();
        duplicateRole.setItemOrder(duplicateOrder);
        
        userRoleRepository.save(duplicateRole);
        assertThatThrownBy(() -> entityManager.flush())
            .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("Required Fields Test")
    void requiredFieldsTest() {
        // Given
        UserRole invalidRole = new UserRole();
        invalidRole.setDescription(faker.lorem().sentence());
        // name, role, itemOrder fields are not set

        // When & Then
        userRoleRepository.save(invalidRole);
        assertThatThrownBy(() -> entityManager.flush())
            .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("ID Auto Generation Test")
    void idGenerationTest() {
        // Given
        UserRole userRole = createRandomUserRole();

        // When
        UserRole savedRole = userRoleRepository.save(userRole);
        entityManager.flush();

        // Then
        assertThat(savedRole.getId()).isNotNull();
        assertThat(savedRole.getRoleId()).isNotNull();
        assertThat(savedRole.getId()).isEqualTo(savedRole.getRoleId());
    }
}