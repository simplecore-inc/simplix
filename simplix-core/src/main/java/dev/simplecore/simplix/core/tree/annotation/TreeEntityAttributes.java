package dev.simplecore.simplix.core.tree.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for configuring tree structure metadata for entities.
 * <p>
 * This annotation is used to specify database table and column information
 * for entities that implement tree structures. It provides the necessary
 * metadata for generating efficient database queries and managing hierarchical
 * relationships at the persistence layer.
 * <p>
 * Key Features:
 * <ul>
 *   <li>Database table and column mapping configuration</li>
 *   <li>Support for custom column names and table names</li>
 *   <li>Configurable lookup columns for efficient searching</li>
 *   <li>Sort direction control (ASC/DESC)</li>
 *   <li>Cascade delete option for hierarchical deletion</li>
 *   <li>Maximum depth limit for tree operations</li>
 * </ul>
 * <p>
 * Usage Example:
 * <pre>{@code
 * @Entity
 * @TreeEntityAttributes(
 *     tableName = "categories",
 *     idColumn = "category_id",
 *     parentIdColumn = "parent_category_id",
 *     sortOrderColumn = "display_order",
 *     sortDirection = SortDirection.ASC,
 *     cascadeDelete = false,
 *     lookupColumns = {
 *         @LookupColumn(name = "name", type = ColumnType.STRING),
 *         @LookupColumn(name = "active", type = ColumnType.BOOLEAN)
 *     }
 * )
 * public class Category implements TreeEntity<Category, Long> {
 *     // Entity implementation
 * }
 * }</pre>
 * <p>
 * Database Compatibility:
 * <ul>
 *   <li>Works with all major SQL databases</li>
 *   <li>Generates optimized recursive queries when supported</li>
 *   <li>Fallback to in-memory processing for unsupported databases</li>
 * </ul>
 *
 * @author System Generated
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TreeEntityAttributes {
    
    /**
     * Specifies the database table name for this tree entity.
     * <p>
     * This should match the actual table name in the database where
     * the tree entities are stored. If not specified, the framework
     * will attempt to derive the table name from the entity class name
     * or JPA @Table annotation.
     * 
     * @return The database table name
     */
    String tableName();
    
    /**
     * Specifies the column name for the entity's primary key.
     * <p>
     * This column should contain the unique identifier for each entity
     * in the tree structure. The column type should match the ID type
     * used in the entity implementation.
     * 
     * @return The primary key column name
     */
    String idColumn();
    
    /**
     * Specifies the column name that stores parent entity references.
     * <p>
     * This column creates the hierarchical relationship between entities.
     * It should contain the ID of the parent entity, or null for root
     * entities. The column type should match the ID column type.
     * 
     * @return The parent reference column name
     */
    String parentIdColumn();

    /**
     * Specifies the column name used for ordering sibling entities.
     * <p>
     * This column determines the display order of entities that share
     * the same parent. Lower values typically appear first. The column
     * should be of integer type and may be nullable.
     * 
     * @return The sort order column name
     */
    String sortOrderColumn();

    /**
     * Defines additional columns that can be used for lookup operations.
     * <p>
     * These columns enable efficient searching and filtering of tree
     * entities using the findByLookup methods. Each lookup column
     * should specify the column name and its data type for proper
     * query generation.
     * <p>
     * Example:
     * <pre>{@code
     * lookupColumns = {
     *     @LookupColumn(name = "name", type = ColumnType.STRING),
     *     @LookupColumn(name = "active", type = ColumnType.BOOLEAN),
     *     @LookupColumn(name = "priority", type = ColumnType.NUMBER)
     * }
     * }</pre>
     *
     * @return Array of lookup column definitions, empty by default
     */
    LookupColumn[] lookupColumns() default {};

    /**
     * Specifies the sort direction for ordering sibling entities.
     * <p>
     * This determines how entities with the same parent are ordered
     * in query results. Use ASC for ascending order (default) or
     * DESC for descending order.
     *
     * @return The sort direction, defaults to ASC
     * @since 1.1.0
     */
    SortDirection sortDirection() default SortDirection.ASC;

    /**
     * Enables cascade deletion of child entities.
     * <p>
     * When set to true, deleting a parent entity will automatically
     * delete all its descendants (children, grandchildren, etc.).
     * When false (default), deletion of a parent with children will
     * throw an exception.
     * <p>
     * Note: This is handled at the service level, not database level,
     * to ensure proper cache invalidation and event handling.
     *
     * @return true to enable cascade delete, false otherwise
     * @since 1.1.0
     */
    boolean cascadeDelete() default false;

    /**
     * Limits the maximum depth of tree operations.
     * <p>
     * When set to a positive value, tree traversal operations will
     * stop at the specified depth level. This can improve performance
     * for deep hierarchies where full traversal is not needed.
     * <p>
     * A value of -1 (default) means unlimited depth.
     *
     * @return Maximum depth limit, or -1 for unlimited
     * @since 1.1.0
     */
    int maxDepth() default -1;
} 