package dev.simplecore.simplix.core.tree.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for defining searchable columns in tree entities.
 * 
 * This annotation is used within @TreeEntityAttributes to specify columns
 * that can be used for efficient searching and filtering operations. Each
 * lookup column defines a database column that supports optimized queries
 * through the tree service's findByLookup methods.
 * 
 * Key Features:
 * - Type-safe column definitions with proper data type mapping
 * - Integration with database query generation
 * - Support for various data types (string, number, boolean)
 * - Automatic parameter binding and type conversion
 * 
 * Usage Context:
 * This annotation is used as a component within @TreeEntityAttributes:
 * 
 * <pre>
 * {@code
 * @TreeEntityAttributes(
 *     tableName = "products",
 *     idColumn = "product_id",
 *     parentIdColumn = "parent_product_id",
 *     sortOrderColumn = "sort_order",
 *     lookupColumns = {
 *         @LookupColumn(name = "name", type = ColumnType.STRING),
 *         @LookupColumn(name = "active", type = ColumnType.BOOLEAN),
 *         @LookupColumn(name = "price", type = ColumnType.NUMBER)
 *     }
 * )
 * }
 * </pre>
 * 
 * Query Generation:
 * - STRING columns support exact matches and pattern matching
 * - NUMBER columns support equality and range comparisons  
 * - BOOLEAN columns support true/false value matching
 * - Proper SQL parameter binding prevents injection attacks
 * 
 * Performance Considerations:
 * - Define database indexes on frequently searched lookup columns
 * - Limit the number of lookup columns to essential search fields
 * - Consider column selectivity when choosing lookup columns
 * 
 * @author System Generated
 * @since 1.0.0
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface LookupColumn {
    
    /**
     * Specifies the database column name for lookup operations.
     * 
     * This should be the exact column name as it appears in the database
     * table. The column name is used to generate WHERE clauses in search
     * queries and should be properly indexed for optimal performance.
     * 
     * Examples:
     * - "name" for text-based searches
     * - "active" for boolean flag filtering  
     * - "created_date" for date-based filtering
     * - "priority_level" for numeric comparisons
     * 
     * @return The database column name used for searching
     */
    String name();

    /**
     * Specifies the data type of the lookup column.
     * 
     * The column type determines how search parameters are processed
     * and converted for database queries. It ensures type safety and
     * proper SQL parameter binding.
     * 
     * Supported Types:
     * - STRING: For text-based columns (VARCHAR, CHAR, TEXT)
     * - NUMBER: For numeric columns (INT, BIGINT, DECIMAL, FLOAT)
     * - BOOLEAN: For boolean columns (BOOLEAN, BIT, TINYINT)
     * 
     * Type Conversion:
     * - STRING values are used as-is for exact matching
     * - NUMBER values are converted from string to appropriate numeric type
     * - BOOLEAN values accept "true"/"false", "1"/"0", "yes"/"no"
     * 
     * @return The data type of the column for proper query generation
     */
    ColumnType type();


    public enum ColumnType {
    
        /**
         * String/text data type for character-based columns.
         * 
         * Used for columns that store textual data such as names, descriptions,
         * codes, and other string values. Supports exact matching and pattern
         * matching operations.
         * 
         * Corresponding SQL Types:
         * - VARCHAR, CHAR, TEXT
         * - NVARCHAR, NCHAR (Unicode variants)
         * - CLOB (for large text content)
         * 
         * Query Behavior:
         * - Exact string matching: WHERE column = 'value'
         * - Case sensitivity depends on database collation
         * - Support for LIKE operations with wildcards
         * 
         * Usage Examples:
         * - Product names, descriptions
         * - Category codes, identifiers  
         * - User input text fields
         */
        STRING,
        
        /**
         * Numeric data type for integer and decimal columns.
         * 
         * Used for columns that store numeric values including integers,
         * decimals, and floating-point numbers. Supports equality and
         * range comparison operations.
         * 
         * Corresponding SQL Types:
         * - INT, BIGINT, SMALLINT, TINYINT
         * - DECIMAL, NUMERIC (with precision/scale)
         * - FLOAT, DOUBLE, REAL
         * 
         * Query Behavior:
         * - Exact numeric matching: WHERE column = 123
         * - Automatic type conversion from string parameters
         * - Support for range operations (>, <, >=, <=)
         * 
         * Usage Examples:
         * - Prices, quantities, scores
         * - Priority levels, sort orders
         * - Age, count, measurement values
         */
        NUMBER,
        
        /**
         * Boolean data type for true/false columns.
         * 
         * Used for columns that store binary true/false values, flags,
         * and boolean states. Supports exact boolean matching with
         * flexible input value interpretation.
         * 
         * Corresponding SQL Types:
         * - BOOLEAN (PostgreSQL, H2)
         * - BIT (SQL Server, MySQL)
         * - TINYINT(1) (MySQL boolean emulation)
         * - NUMBER(1) (Oracle boolean emulation)
         * 
         * Query Behavior:
         * - Boolean matching: WHERE column = true/false
         * - Database-specific boolean representation
         * - Automatic conversion from various input formats
         * 
         * Input Value Conversion:
         * - "true", "1", "yes", "on" → true
         * - "false", "0", "no", "off" → false
         * - Case-insensitive interpretation
         * 
         * Usage Examples:
         * - Active/inactive flags
         * - Enabled/disabled states
         * - Published/draft status
         */
        BOOLEAN
    } 
} 