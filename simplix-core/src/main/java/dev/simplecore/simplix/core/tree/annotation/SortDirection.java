package dev.simplecore.simplix.core.tree.annotation;

/**
 * Defines the sort direction for ordering tree entities.
 * <p>
 * Used in conjunction with {@link TreeEntityAttributes#sortDirection()} to specify
 * how sibling entities should be ordered in query results.
 *
 * @author System Generated
 * @since 1.1.0
 */
public enum SortDirection {

    /**
     * Ascending order (smallest/oldest first).
     * <p>
     * For numeric values: 1, 2, 3, ...
     * For timestamps: oldest to newest
     * For strings: A to Z
     */
    ASC,

    /**
     * Descending order (largest/newest first).
     * <p>
     * For numeric values: 3, 2, 1, ...
     * For timestamps: newest to oldest
     * For strings: Z to A
     */
    DESC
}
