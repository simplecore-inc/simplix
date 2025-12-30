package dev.simplecore.simplix.core.validator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Test entity for composite unique constraint validation.
 * Employee number is unique within an organization.
 */
@Entity
@Table(name = "test_employees", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"organization_id", "employee_number"})
})
public class TestEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "employee_number", nullable = false)
    private String employeeNumber;

    @Column(nullable = false)
    private String name;

    public TestEmployee() {
    }

    public TestEmployee(Long organizationId, String employeeNumber, String name) {
        this.organizationId = organizationId;
        this.employeeNumber = employeeNumber;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}