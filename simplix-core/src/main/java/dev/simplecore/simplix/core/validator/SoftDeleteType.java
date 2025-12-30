package dev.simplecore.simplix.core.validator;

/**
 * Defines the type of soft delete field for unique validation.
 * <p>
 * Used by {@link Unique} and {@link UniqueField} annotations to determine
 * how to exclude soft-deleted records from uniqueness checks.
 *
 * @see Unique#softDeleteType()
 * @see UniqueField#softDeleteType()
 */
public enum SoftDeleteType {

    /**
     * No soft delete filtering (default).
     * All records are included in the uniqueness check.
     */
    NONE,

    /**
     * Boolean-based soft delete.
     * <p>
     * Excludes records where the soft delete field is {@code true}.
     * Query condition: {@code (field IS NULL OR field = false)}
     * <p>
     * Example fields: {@code deleted}, {@code isDeleted}
     */
    BOOLEAN,

    /**
     * Timestamp-based soft delete using date/time types.
     * <p>
     * Excludes records where the soft delete field is not null.
     * Query condition: {@code field IS NULL}
     * <p>
     * Use this for {@code LocalDateTime}, {@code Instant}, or {@code Date} fields.
     * <p>
     * Example fields: {@code deletedAt}, {@code deletedDate}
     */
    TIMESTAMP,

    /**
     * Timestamp-based soft delete using Long (epoch milliseconds).
     * <p>
     * Excludes records where the soft delete field has a positive value.
     * Query condition: {@code (field IS NULL OR field < 0)}
     * <p>
     * Use this when storing epoch time as Long and using -1 or negative values
     * to indicate "not deleted".
     * <p>
     * Example fields: {@code deletedAt} (Long type with -1 for not deleted)
     */
    LONG_TIMESTAMP
}
