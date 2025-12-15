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
     * Timestamp-based soft delete.
     * <p>
     * Excludes records where the soft delete field is not null.
     * Query condition: {@code field IS NULL}
     * <p>
     * Example fields: {@code deletedAt}, {@code deletedDate}
     */
    TIMESTAMP
}
