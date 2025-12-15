package dev.simplecore.simplix.core.validator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validator implementation for {@link Unique} annotation.
 * <p>
 * Uses JPA EntityManager to check if a field value already exists in the database.
 * <p>
 * Note: This field-level validator does NOT support ID exclusion for update operations.
 * For update scenarios where you need to exclude the current entity from uniqueness check,
 * use the class-level {@link UniqueFields} annotation instead.
 *
 * @see Unique
 * @see UniqueFields
 */
@Component
public class UniqueValidator implements ConstraintValidator<Unique, Object> {

    @PersistenceContext
    private EntityManager entityManager;

    private Class<?> entityClass;
    private String fieldName;
    private String softDeleteField;
    private SoftDeleteType softDeleteType;

    @Override
    public void initialize(Unique constraintAnnotation) {
        this.entityClass = constraintAnnotation.entity();
        this.fieldName = constraintAnnotation.field();
        this.softDeleteField = constraintAnnotation.softDeleteField();
        this.softDeleteType = constraintAnnotation.softDeleteType();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // Null values are valid (use @NotNull or @NotBlank for null checks)
        if (value == null) {
            return true;
        }

        // Empty strings are valid (use @NotBlank for empty string checks)
        if (value instanceof String && !StringUtils.hasText((String) value)) {
            return true;
        }

        return isUnique(value);
    }

    /**
     * Checks if the given value is unique in the database.
     *
     * @param fieldValue the field value to check
     * @return true if the value is unique
     */
    private boolean isUnique(Object fieldValue) {
        String entityName = entityClass.getSimpleName();
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT COUNT(e) FROM ").append(entityName)
            .append(" e WHERE e.").append(fieldName).append(" = :value");

        // Add soft delete condition
        appendSoftDeleteCondition(jpql, softDeleteField, softDeleteType);

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        query.setParameter("value", fieldValue);

        Long count = query.getSingleResult();
        return count == 0;
    }

    /**
     * Appends soft delete condition to the JPQL query.
     *
     * @param jpql            the JPQL builder
     * @param softDeleteField the soft delete field name
     * @param softDeleteType  the soft delete type
     */
    static void appendSoftDeleteCondition(StringBuilder jpql, String softDeleteField, SoftDeleteType softDeleteType) {
        if (softDeleteType == SoftDeleteType.NONE || !StringUtils.hasText(softDeleteField)) {
            return;
        }

        switch (softDeleteType) {
            case BOOLEAN:
                // Include records where deleted is null or false
                jpql.append(" AND (e.").append(softDeleteField)
                    .append(" IS NULL OR e.").append(softDeleteField).append(" = false)");
                break;
            case TIMESTAMP:
                // Include records where deletedAt is null
                jpql.append(" AND e.").append(softDeleteField).append(" IS NULL");
                break;
            default:
                break;
        }
    }

    /**
     * Static utility method to perform unique validation with ID exclusion.
     * <p>
     * Can be used directly from service layer when more control is needed.
     *
     * @param entityManager the entity manager
     * @param entityClass   the entity class
     * @param fieldName     the field name to check
     * @param fieldValue    the field value to check
     * @param idFieldName   the ID field name in entity
     * @param idValue       the current ID value (null for create, non-null for update)
     * @return true if the value is unique (or belongs to the current entity)
     */
    public static boolean isUnique(
            EntityManager entityManager,
            Class<?> entityClass,
            String fieldName,
            Object fieldValue,
            String idFieldName,
            Object idValue) {
        return isUnique(entityManager, entityClass, fieldName, fieldValue,
                idFieldName, idValue, null, SoftDeleteType.NONE);
    }

    /**
     * Static utility method to perform unique validation with ID exclusion and soft delete support.
     * <p>
     * Can be used directly from service layer when more control is needed.
     *
     * @param entityManager   the entity manager
     * @param entityClass     the entity class
     * @param fieldName       the field name to check
     * @param fieldValue      the field value to check
     * @param idFieldName     the ID field name in entity
     * @param idValue         the current ID value (null for create, non-null for update)
     * @param softDeleteField the soft delete field name (null or empty to disable)
     * @param softDeleteType  the soft delete type
     * @return true if the value is unique (or belongs to the current entity)
     */
    public static boolean isUnique(
            EntityManager entityManager,
            Class<?> entityClass,
            String fieldName,
            Object fieldValue,
            String idFieldName,
            Object idValue,
            String softDeleteField,
            SoftDeleteType softDeleteType) {

        if (fieldValue == null) {
            return true;
        }

        if (fieldValue instanceof String && !StringUtils.hasText((String) fieldValue)) {
            return true;
        }

        String entityName = entityClass.getSimpleName();
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT COUNT(e) FROM ").append(entityName)
            .append(" e WHERE e.").append(fieldName).append(" = :value");

        if (idValue != null) {
            jpql.append(" AND e.").append(idFieldName).append(" != :id");
        }

        // Add soft delete condition
        appendSoftDeleteCondition(jpql, softDeleteField, softDeleteType);

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        query.setParameter("value", fieldValue);

        if (idValue != null) {
            query.setParameter("id", idValue);
        }

        Long count = query.getSingleResult();
        return count == 0;
    }
}