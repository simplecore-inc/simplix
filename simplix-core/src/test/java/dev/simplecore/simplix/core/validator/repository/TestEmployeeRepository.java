package dev.simplecore.simplix.core.validator.repository;

import dev.simplecore.simplix.core.validator.entity.TestEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for TestEmployee entity.
 */
@Repository
public interface TestEmployeeRepository extends JpaRepository<TestEmployee, Long> {
}