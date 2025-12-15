package dev.simplecore.simplix.core.validator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validator implementation for {@link UniqueFields} annotation.
 * <p>
 * Validates multiple unique field constraints at the class level.
 * Has access to the full DTO object, enabling reliable ID extraction
 * for update exclusion scenarios.
 *
 * @see UniqueFields
 * @see UniqueField
 */
@Component
public class UniqueFieldsValidator implements ConstraintValidator<UniqueFields, Object> {

    @PersistenceContext
    private EntityManager entityManager;

    private UniqueField[] uniqueFields;

    @Override
    public void initialize(UniqueFields constraintAnnotation) {
        this.uniqueFields = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        BeanWrapper wrapper = new BeanWrapperImpl(dto);
        boolean allValid = true;

        // Disable default constraint violation
        context.disableDefaultConstraintViolation();

        for (UniqueField field : uniqueFields) {
            if (!validateUniqueField(wrapper, field, context)) {
                allValid = false;
            }
        }

        return allValid;
    }

    /**
     * Validates a single unique field constraint.
     *
     * @param wrapper the bean wrapper for the DTO
     * @param field   the unique field definition
     * @param context the constraint validator context
     * @return true if the field value is unique
     */
    private boolean validateUniqueField(
            BeanWrapper wrapper,
            UniqueField field,
            ConstraintValidatorContext context) {

        // Get the field value from DTO
        Object fieldValue = null;
        if (wrapper.isReadableProperty(field.property())) {
            fieldValue = wrapper.getPropertyValue(field.property());
        }

        // Skip validation if field value is null or empty
        if (fieldValue == null) {
            return true;
        }
        if (fieldValue instanceof String && !StringUtils.hasText((String) fieldValue)) {
            return true;
        }

        // Get the ID value for update exclusion
        Object idValue = null;
        if (StringUtils.hasText(field.idProperty()) && wrapper.isReadableProperty(field.idProperty())) {
            idValue = wrapper.getPropertyValue(field.idProperty());
        }

        // Check uniqueness
        boolean isUnique = checkUniqueness(field.entity(), field.field(), fieldValue,
                field.idField(), idValue);

        if (!isUnique) {
            // Add constraint violation for the specific property
            context.buildConstraintViolationWithTemplate(field.message())
                    .addPropertyNode(field.property())
                    .addConstraintViolation();
        }

        return isUnique;
    }

    /**
     * Checks if the given value is unique in the database.
     *
     * @param entityClass the entity class
     * @param fieldName   the field name in entity
     * @param fieldValue  the value to check
     * @param idFieldName the ID field name in entity
     * @param idValue     the current entity's ID (for update exclusion), or null
     * @return true if the value is unique (or belongs to the current entity)
     */
    private boolean checkUniqueness(
            Class<?> entityClass,
            String fieldName,
            Object fieldValue,
            String idFieldName,
            Object idValue) {

        String entityName = entityClass.getSimpleName();
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT COUNT(e) FROM ").append(entityName)
            .append(" e WHERE e.").append(fieldName).append(" = :value");

        if (idValue != null) {
            jpql.append(" AND e.").append(idFieldName).append(" != :id");
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        query.setParameter("value", fieldValue);

        if (idValue != null) {
            query.setParameter("id", idValue);
        }

        Long count = query.getSingleResult();
        return count == 0;
    }
}