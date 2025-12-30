package dev.simplecore.simplix.core.validator.dto;

import dev.simplecore.simplix.core.validator.UniqueComposite;
import dev.simplecore.simplix.core.validator.UniqueComposites;
import dev.simplecore.simplix.core.validator.entity.TestEmployee;

/**
 * DTO for creating an employee with composite unique constraint validation.
 */
@UniqueComposites({
    @UniqueComposite(
        entity = TestEmployee.class,
        fields = {"organizationId", "employeeNumber"},
        properties = {"organizationId", "employeeNumber"},
        message = "Employee number already exists in this organization"
    )
})
public class CreateEmployeeDto {

    private Long organizationId;
    private String employeeNumber;
    private String name;

    public CreateEmployeeDto() {
    }

    public CreateEmployeeDto(Long organizationId, String employeeNumber, String name) {
        this.organizationId = organizationId;
        this.employeeNumber = employeeNumber;
        this.name = name;
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