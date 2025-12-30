package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.UniqueComposite;
import dev.simplecore.simplix.core.validator.UniqueComposites;
import dev.simplecore.simplix.core.validator.entity.TestEmployee;

/**
 * DTO for updating an employee with composite unique constraint validation.
 * Excludes current entity from uniqueness check using idProperty.
 */
@UniqueComposites({
    @UniqueComposite(
        entity = TestEmployee.class,
        fields = {"organizationId", "employeeNumber"},
        properties = {"organizationId", "employeeNumber"},
        idField = "id",
        idProperty = "id",
        message = "Employee number already exists in this organization"
    )
})
public class UpdateEmployeeDto {

    private Long id;
    private Long organizationId;
    private String employeeNumber;
    private String name;

    public UpdateEmployeeDto() {
    }

    public UpdateEmployeeDto(Long id, Long organizationId, String employeeNumber, String name) {
        this.id = id;
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