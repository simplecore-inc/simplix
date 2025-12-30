package dev.simplecore.simplix.core.validator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator implementation for {@link UniqueComposites} annotation.
 * <p>
 * Validates composite unique constraints where multiple fields together
 * must be unique within the entity table.
 *
 * @see UniqueComposites
 * @see UniqueComposite
 */
@Component
public class UniqueCompositeValidator implements ConstraintValidator<UniqueComposites, Object> {

    private static final Logger log = LoggerFactory.getLogger(UniqueCompositeValidator.class);

    @PersistenceContext
    private EntityManager entityManager;

    private UniqueComposite[] composites;

    @Override
    public void initialize(UniqueComposites constraintAnnotation) {
        this.composites = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        BeanWrapper wrapper = new BeanWrapperImpl(dto);
        boolean allValid = true;

        context.disableDefaultConstraintViolation();

        for (UniqueComposite composite : composites) {
            if (!validateComposite(wrapper, composite, context)) {
                allValid = false;
            }
        }

        return allValid;
    }

    /**
     * Validates a single composite unique constraint.
     */
    private boolean validateComposite(
            BeanWrapper wrapper,
            UniqueComposite composite,
            ConstraintValidatorContext context) {

        String[] fields = composite.fields();
        String[] properties = composite.properties();

        if (fields.length != properties.length) {
            log.error("UniqueComposite: fields and properties array length mismatch");
            return true;
        }

        // Collect field values from DTO
        List<Object> fieldValues = new ArrayList<>();
        for (String property : properties) {
            if (!wrapper.isReadableProperty(property)) {
                log.warn("UniqueComposite: property '{}' is not readable", property);
                return true;
            }
            Object value = wrapper.getPropertyValue(property);
            if (value == null) {
                // If any composite field is null, skip validation
                return true;
            }
            if (value instanceof String && !StringUtils.hasText((String) value)) {
                return true;
            }
            fieldValues.add(value);
        }

        // Get ID value for update exclusion
        Object idValue = null;
        if (StringUtils.hasText(composite.idProperty())) {
            if (wrapper.isReadableProperty(composite.idProperty())) {
                idValue = wrapper.getPropertyValue(composite.idProperty());
            }
        }

        // Check uniqueness
        boolean isUnique = checkCompositeUniqueness(
                composite.entity(),
                fields,
                fieldValues,
                composite.idField(),
                idValue,
                composite.softDeleteField(),
                composite.softDeleteType()
        );

        if (!isUnique) {
            String errorProperty = StringUtils.hasText(composite.errorProperty())
                    ? composite.errorProperty()
                    : properties[0];

            context.buildConstraintViolationWithTemplate(composite.message())
                    .addPropertyNode(errorProperty)
                    .addConstraintViolation();
        }

        return isUnique;
    }

    /**
     * Checks if the given combination of values is unique in the database.
     *
     * @param entityClass     the entity class
     * @param fieldNames      the field names in entity
     * @param fieldValues     the values to check (same order as fieldNames)
     * @param idFieldName     the ID field name in entity
     * @param idValue         the current entity's ID (for update exclusion), or null
     * @param softDeleteField the soft delete field name
     * @param softDeleteType  the soft delete type
     * @return true if the combination is unique
     */
    private boolean checkCompositeUniqueness(
            Class<?> entityClass,
            String[] fieldNames,
            List<Object> fieldValues,
            String idFieldName,
            Object idValue,
            String softDeleteField,
            SoftDeleteType softDeleteType) {

        String entityName = entityClass.getSimpleName();
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT COUNT(e) FROM ").append(entityName).append(" e WHERE ");

        // Build composite field conditions
        for (int i = 0; i < fieldNames.length; i++) {
            if (i > 0) {
                jpql.append(" AND ");
            }
            jpql.append("e.").append(fieldNames[i]).append(" = :value").append(i);
        }

        // Add ID exclusion for update scenarios
        if (idValue != null) {
            jpql.append(" AND e.").append(idFieldName).append(" != :id");
        }

        // Add soft delete condition
        UniqueValidator.appendSoftDeleteCondition(jpql, softDeleteField, softDeleteType);

        // Create and execute query
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);

        // Set composite field parameters
        for (int i = 0; i < fieldValues.size(); i++) {
            query.setParameter("value" + i, fieldValues.get(i));
        }

        // Set ID parameter for update exclusion
        if (idValue != null) {
            query.setParameter("id", idValue);
        }

        Long count = query.getSingleResult();
        return count == 0;
    }
}