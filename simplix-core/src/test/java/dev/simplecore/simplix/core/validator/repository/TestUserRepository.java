package dev.simplecore.simplix.core.validator.repository;

import dev.simplecore.simplix.core.validator.entity.TestUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Test repository for TestUser entity.
 */
public interface TestUserRepository extends JpaRepository<TestUser, Long> {

    Optional<TestUser> findByEmail(String email);

    Optional<TestUser> findByUsername(String username);
}
