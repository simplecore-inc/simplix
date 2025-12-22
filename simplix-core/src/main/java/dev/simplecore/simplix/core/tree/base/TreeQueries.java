package dev.simplecore.simplix.core.tree.base;


import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn.ColumnType;
import dev.simplecore.simplix.core.tree.annotation.SortDirection;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Database-specific query generator for tree-structured entities.
 * <p>
 * Supports configurable sort direction (ASC/DESC) for ordering query results.
 */
@Slf4j
public class TreeQueries {
    private final String tableName;
    private final String idColumn;
    private final String parentIdColumn;
    private final String sortOrderColumn;
    private final SortDirection sortDirection;
    private final Map<String, ColumnType> lookupColumns;

    public TreeQueries(String tableName, String idColumn, String parentIdColumn, String sortOrderColumn) {
        this(tableName, idColumn, parentIdColumn, sortOrderColumn, SortDirection.ASC, new LookupColumn[0]);
    }

    public TreeQueries(String tableName, String idColumn, String parentIdColumn, String sortOrderColumn, LookupColumn[] lookupColumns) {
        this(tableName, idColumn, parentIdColumn, sortOrderColumn, SortDirection.ASC, lookupColumns);
    }

    public TreeQueries(String tableName, String idColumn, String parentIdColumn, String sortOrderColumn, SortDirection sortDirection, LookupColumn[] lookupColumns) {
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.parentIdColumn = parentIdColumn;
        this.sortOrderColumn = sortOrderColumn;
        this.sortDirection = sortDirection != null ? sortDirection : SortDirection.ASC;
        this.lookupColumns = parseLookupColumns(lookupColumns);
    }

    private Map<String, ColumnType> parseLookupColumns(LookupColumn[] columns) {
        Map<String, ColumnType> result = new LinkedHashMap<>();
        if (columns != null) {
            for (LookupColumn column : columns) {
                result.put(column.name(), column.type());
            }
        }
        return result;
    }
    private String getParameterPlaceholder(ColumnType columnType, String parameterValue, String dbType) {
        if (columnType == ColumnType.STRING) {
            return String.format("'%s'", parameterValue);
        } else if (columnType == ColumnType.NUMBER) {
            return parameterValue;
        } else if (columnType == ColumnType.BOOLEAN) {
            if (dbType.equals("oracle")) {
                return Boolean.parseBoolean(parameterValue) ? "1" : "0";
            }
            return parameterValue.toLowerCase();
        }
        return parameterValue;
    }

    private String buildLookupCondition(Map<String, String> parameters, String dbType) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        StringBuilder condition = new StringBuilder(" AND ");
        int i = 0;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String columnName = entry.getKey();
            ColumnType columnType = lookupColumns.get(columnName);
            
            if (columnType == null) {
                log.warn("Undefined lookup column: {}", columnName);
                continue;
            }

            if (i > 0) {
                condition.append(" AND ");
            }
            
            String paramValue = entry.getValue();
            condition.append(columnName)
                    .append(" = ")
                    .append(getParameterPlaceholder(columnType, paramValue, dbType));
            i++;
        }
        return condition.toString();
    }

    private String getSortDirectionSql() {
        return sortDirection == SortDirection.DESC ? "DESC" : "ASC";
    }

    private String getOrderByClause() {
        String dir = getSortDirectionSql();
        return sortOrderColumn != null ?
            String.format("ORDER BY %s %s, %s %s", sortOrderColumn, dir, idColumn, dir) :
            String.format("ORDER BY %s %s", idColumn, dir);
    }

    private String getOrderByClauseWithNullsLast(String dbType) {
        String dir = getSortDirectionSql();
        String nullsLast = sortDirection == SortDirection.DESC ? "NULLS FIRST" : "NULLS LAST";

        if ("postgresql".equals(dbType) || "oracle".equals(dbType)) {
            return sortOrderColumn != null ?
                String.format("ORDER BY %s %s %s, %s %s", sortOrderColumn, dir, nullsLast, idColumn, dir) :
                String.format("ORDER BY %s %s", idColumn, dir);
        } else {
            return getOrderByClause();
        }
    }

    /**
     * Generates a query to retrieve items with additional search conditions
     */
    public String getLookupQuery(Map<String, String> parameters, String dbType) {
        String condition = buildLookupCondition(parameters, dbType);
        return String.format("SELECT * FROM %s WHERE 1=1%s %s",
            tableName, condition, getOrderByClauseWithNullsLast(dbType));
    }

    /**
     * Generates a query to retrieve the entire hierarchy
     */
    public String getHierarchyQuery(String dbType) {
        try {
            if ("postgresql".equals(dbType) || "mysql".equals(dbType) || "mariadb".equals(dbType)) {
                return String.format(
                    "WITH RECURSIVE hierarchy AS ( " +
                    "    SELECT *, 1 as level " +
                    "    FROM %s WHERE %s IS NULL OR TRIM(%s) = '' " +
                    "    UNION ALL " +
                    "    SELECT c.*, h.level + 1 " +
                    "    FROM %s c " +
                    "    JOIN hierarchy h ON c.%s = h.%s " +
                    ") " +
                    "SELECT hierarchy.* FROM hierarchy %s",
                    tableName, parentIdColumn, parentIdColumn,
                    tableName, parentIdColumn, idColumn,
                    getOrderByClauseWithNullsLast(dbType));
            } else if ("oracle".equals(dbType)) {
                return String.format(
                    "SELECT t.*, LEVEL as tree_level " +
                    "FROM %s t " +
                    "START WITH t.%s IS NULL OR TRIM(t.%s) = '' " +
                    "CONNECT BY NOCYCLE PRIOR t.%s = t.%s " +
                    "%s",
                    tableName,
                    parentIdColumn, parentIdColumn,
                    idColumn, parentIdColumn,
                    getOrderByClauseWithNullsLast(dbType));
            } else if ("mssql".equals(dbType)) {
                return String.format(
                    "WITH RECURSIVE hierarchy AS ( " +
                    "    SELECT *, 1 as level " +
                    "    FROM %s WHERE %s IS NULL OR LTRIM(RTRIM(%s)) = '' " +
                    "    UNION ALL " +
                    "    SELECT c.*, h.level + 1 " +
                    "    FROM %s c " +
                    "    JOIN hierarchy h ON c.%s = h.%s " +
                    ") " +
                    "SELECT hierarchy.* FROM hierarchy %s",
                    tableName, parentIdColumn, parentIdColumn,
                    tableName, parentIdColumn, idColumn,
                    getOrderByClauseWithNullsLast(dbType));
            } else if ("h2".equals(dbType)) {
                // H2 Recursive Queries
                // https://www.h2database.com/html/advanced.html?highlight=recursive&search=re#recursive_queries
                return String.format(
                    "WITH RECURSIVE hierarchy(%s, %s, tree_level) AS ( " +
                    "    SELECT %s, %s, 0 " +
                    "    FROM %s WHERE %s IS NULL OR %s = '' " +
                    "    UNION ALL " +
                    "    SELECT c.%s, c.%s, h.tree_level + 1 " +
                    "    FROM %s c " +
                    "    INNER JOIN hierarchy h ON c.%s = h.%s " +
                    ") " +
                    "SELECT t.* FROM %s t " +
                    "INNER JOIN hierarchy h ON t.%s = h.%s %s",
                    idColumn, parentIdColumn,
                    idColumn, parentIdColumn, tableName, parentIdColumn, parentIdColumn,
                    idColumn, parentIdColumn, tableName, parentIdColumn, idColumn,
                    tableName, idColumn, idColumn, getOrderByClauseWithNullsLast(dbType));
            } else {
                return String.format(
                    "SELECT *, 1 as level FROM %s %s",
                    tableName,
                    getOrderByClauseWithNullsLast(dbType));
            }
        } catch (Exception e) {
            log.error("Error generating hierarchy query for database type: {}", dbType, e);
            return String.format("SELECT * FROM %s %s", tableName, getOrderByClauseWithNullsLast(dbType));
        }
    }

    /**
     * Generates a query to retrieve all descendants of a specific item
     */
    public String getDescendantsQuery(String dbType) {
        try {
            if ("postgresql".equals(dbType) || "mysql".equals(dbType) || "mariadb".equals(dbType)) {
                return String.format(
                    "WITH RECURSIVE sub_items AS ( " +
                    "    SELECT *, 1 as level " + 
                    "    FROM %s WHERE %s = ?1 " +
                    "    UNION ALL " +
                    "    SELECT c.*, s.level + 1 " +
                    "    FROM %s c " +
                    "    JOIN sub_items s ON c.%s = s.%s " +
                    ") " +
                    "SELECT sub_items.* FROM sub_items %s",
                    tableName, idColumn,
                    tableName, parentIdColumn, idColumn, 
                    getOrderByClauseWithNullsLast(dbType));
            } else if ("oracle".equals(dbType)) {
                return String.format(
                    "SELECT t.*, LEVEL as tree_level " +
                    "FROM %s t " +
                    "START WITH t.%s = ?1 " +
                    "CONNECT BY NOCYCLE PRIOR t.%s = t.%s " +
                    "%s",
                    tableName,
                    idColumn,
                    idColumn, parentIdColumn,
                    getOrderByClauseWithNullsLast(dbType));
            } else if ("mssql".equals(dbType)) {
                return String.format(
                    "WITH RECURSIVE sub_items AS ( " +
                    "    SELECT *, 1 as level " +
                    "    FROM %s " +
                    "    WHERE %s = ?1 " +
                    "    UNION ALL " +
                    "    SELECT t.*, s.level + 1 " +
                    "    FROM %s t " +
                    "    INNER JOIN sub_items s ON t.%s = s.%s " +
                    ") " +
                    "SELECT * FROM sub_items %s",
                    tableName,
                    idColumn,
                    tableName,
                    parentIdColumn, idColumn,
                    getOrderByClauseWithNullsLast(dbType));
            } else if ("h2".equals(dbType)) {
                // H2 Recursive Queries
                // https://www.h2database.com/html/advanced.html?highlight=recursive&search=re#recursive_queries
                return String.format(
                    "WITH RECURSIVE sub_items(%s, %s, tree_level) AS ( " +
                    "    SELECT %s, %s, 0 " +
                    "    FROM %s WHERE %s = ?1 " +
                    "    UNION ALL " +
                    "    SELECT c.%s, c.%s, s.tree_level + 1 " +
                    "    FROM %s c " +
                    "    INNER JOIN sub_items s ON c.%s = s.%s " +
                    ") " +
                    "SELECT t.* FROM %s t " +
                    "INNER JOIN sub_items s ON t.%s = s.%s %s",
                    idColumn, parentIdColumn,
                    idColumn, parentIdColumn, tableName, idColumn,
                    idColumn, parentIdColumn, tableName, parentIdColumn, idColumn,
                    tableName, idColumn, idColumn, getOrderByClauseWithNullsLast(dbType));
            } else {
                return String.format(
                    "SELECT *, 1 as level FROM %s WHERE %s = ?1 %s",
                    tableName, idColumn,
                    getOrderByClauseWithNullsLast(dbType));
            }
        } catch (Exception e) {
            log.error("Error generating descendants query for database type: {}", dbType, e);
            return String.format("SELECT * FROM %s WHERE %s = ?1 %s",
                tableName, idColumn, getOrderByClauseWithNullsLast(dbType));
        }
    }

    /**
     * Generates a query to retrieve root items
     */
    public String getRootItemsQuery() {
        return String.format("SELECT * FROM %s WHERE %s IS NULL %s",
            tableName, parentIdColumn, getOrderByClauseWithNullsLast(null));
    }

    /**
     * Generates a query to retrieve root items with database type consideration
     */
    public String getRootItemsQuery(String dbType) {
        if ("h2".equals(dbType)) {
            return String.format("SELECT * FROM %s WHERE %s IS NULL %s",
                tableName, parentIdColumn, getOrderByClauseWithNullsLast(dbType));
        } else {
            return String.format("SELECT * FROM %s WHERE %s IS NULL OR TRIM(%s) = '' %s",
                tableName, parentIdColumn, parentIdColumn, getOrderByClauseWithNullsLast(dbType));
        }
    }

    /**
     * Generates a query to retrieve direct children of a specific item
     */
    public String getDirectChildrenQuery() {
        return String.format("SELECT * FROM %s WHERE %s = ?1 %s",
            tableName, parentIdColumn, getOrderByClauseWithNullsLast(null));
    }

    /**
     * Generates a query to retrieve all ancestors of a specific item
     */
    public String getAncestorsQuery(String dbType) {
        try {
            String query;
            if ("postgresql".equals(dbType) || "mysql".equals(dbType) || "mariadb".equals(dbType) || "mssql".equals(dbType)) {
                query = String.format(
                    "WITH RECURSIVE ancestors AS ( " +
                    "    SELECT *, 1 as level " +
                    "    FROM %s WHERE %s = ?1 " +
                    "    UNION ALL " +
                    "    SELECT p.*, a.level + 1 " +
                    "    FROM %s p " +
                    "    JOIN ancestors a ON p.%s = a.%s " +
                    ") " +
                    "SELECT ancestors.* FROM ancestors ORDER BY level DESC",
                    tableName, idColumn,
                    tableName, idColumn, parentIdColumn);
            } else if ("oracle".equals(dbType)) {
                query = String.format(
                    "SELECT t.*, LEVEL as tree_level " +
                    "FROM %s t " +
                    "START WITH t.%s = ?1 " +
                    "CONNECT BY NOCYCLE PRIOR t.%s = t.%s " +
                    "ORDER BY tree_level DESC",
                    tableName, idColumn,
                    parentIdColumn, idColumn);
            } else if ("h2".equals(dbType)) {
                // H2 Recursive Queries
                // https://www.h2database.com/html/advanced.html?highlight=recursive&search=re#recursive_queries
                query = String.format(
                    "WITH RECURSIVE ancestors(%s, %s, tree_level) AS ( " +
                    "    SELECT %s, %s, 0 " +
                    "    FROM %s WHERE %s = ?1 " +
                    "    UNION ALL " +
                    "    SELECT p.%s, p.%s, a.tree_level + 1 " +
                    "    FROM %s p " +
                    "    INNER JOIN ancestors a ON p.%s = a.%s " +
                    ") " +
                    "SELECT t.* FROM %s t " +
                    "INNER JOIN ancestors a ON t.%s = a.%s ORDER BY a.tree_level DESC",
                    idColumn, parentIdColumn,
                    idColumn, parentIdColumn, tableName, idColumn,
                    idColumn, parentIdColumn, tableName, idColumn, parentIdColumn,
                    tableName, idColumn, idColumn);
            } else {
                query = String.format(
                    "SELECT * FROM %s WHERE %s = ?1",
                    tableName, idColumn);
            }
            return query;
        } catch (Exception e) {
            log.error("Error generating ancestors query for database type: {}", dbType, e);
            return String.format("SELECT * FROM %s WHERE %s = ?1", tableName, idColumn);
        }
    }

    /**
     * Generates a query to retrieve siblings of a specific item
     */
    public String getSiblingsQuery() {
        return String.format(
            "SELECT * FROM %s WHERE %s = (" +
            "    SELECT %s FROM %s WHERE %s = ?1" +
            ") AND %s != ?1 %s",
            tableName, parentIdColumn,
            parentIdColumn, tableName, idColumn,
            idColumn, getOrderByClauseWithNullsLast(null));
    }

    /**
     * Generates a query to retrieve leaf nodes (items with no children)
     */
    public String getLeafNodesQuery() {
        return String.format(
            "SELECT * FROM %s WHERE %s NOT IN (" +
            "    SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL AND %s != ''" +
            ") %s",
            tableName, idColumn,
            parentIdColumn, tableName, parentIdColumn, parentIdColumn,
            getOrderByClauseWithNullsLast(null));
    }

    /**
     * Generates a query to retrieve leaf nodes (items with no children) with database type consideration
     */
    public String getLeafNodesQuery(String dbType) {
        if ("h2".equals(dbType)) {
            return String.format(
                "SELECT * FROM %s WHERE %s NOT IN (" +
                "    SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL AND %s != ''" +
                ") %s",
                tableName, idColumn,
                parentIdColumn, tableName, parentIdColumn, parentIdColumn,
                getOrderByClauseWithNullsLast(dbType));
        } else {
            return String.format(
                "SELECT * FROM %s WHERE %s NOT IN (" +
                "    SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL AND TRIM(%s) != ''" +
                ") %s",
                tableName, idColumn,
                parentIdColumn, tableName, parentIdColumn, parentIdColumn,
                getOrderByClauseWithNullsLast(dbType));
        }
    }

    /**
     * Generates a query to count direct children grouped by parent ID.
     * <p>
     * Returns rows with [parentId, count] pairs for parents that have children.
     * Parents with no children will not appear in the result.
     * <p>
     * Note: This query only checks for NOT NULL since parent_id can be numeric or string.
     * For string-type parent_id columns with empty string values, use findRoots/findDirectChildren methods.
     *
     * @param dbType the database type
     * @return SQL query string
     */
    public String getChildCountQuery(String dbType) {
        // Only check for NOT NULL to support both numeric and string ID types
        return String.format(
            "SELECT %s, COUNT(*) FROM %s " +
            "WHERE %s IS NOT NULL " +
            "GROUP BY %s",
            parentIdColumn, tableName,
            parentIdColumn,
            parentIdColumn);
    }

    /**
     * Generates a correlated subquery for counting direct children.
     * <p>
     * This subquery counts children where parent_id matches the outer query's id.
     *
     * @return SQL subquery string for child count
     */
    private String getChildCountSubquery() {
        return String.format(
            "(SELECT COUNT(*) FROM %s child WHERE child.%s = t.%s)",
            tableName, parentIdColumn, idColumn);
    }

    /**
     * Generates a query to retrieve root items with child count in a single query.
     * <p>
     * Uses a correlated subquery to include child_count for each root item.
     * This eliminates the need for a separate GROUP BY query.
     *
     * @param dbType the database type
     * @return SQL query string that returns entity columns plus child_count
     */
    public String getRootItemsWithChildCountQuery(String dbType) {
        String countSubquery = getChildCountSubquery();

        if ("h2".equals(dbType)) {
            return String.format(
                "SELECT t.*, %s as child_count FROM %s t " +
                "WHERE t.%s IS NULL %s",
                countSubquery, tableName, parentIdColumn,
                getOrderByClauseWithAlias(dbType));
        } else {
            return String.format(
                "SELECT t.*, %s as child_count FROM %s t " +
                "WHERE t.%s IS NULL OR TRIM(t.%s) = '' %s",
                countSubquery, tableName, parentIdColumn, parentIdColumn,
                getOrderByClauseWithAlias(dbType));
        }
    }

    /**
     * Generates a query to retrieve direct children with child count in a single query.
     * <p>
     * Uses a correlated subquery to include child_count for each child item.
     * This eliminates the need for a separate GROUP BY query.
     *
     * @param dbType the database type
     * @return SQL query string that returns entity columns plus child_count
     */
    public String getDirectChildrenWithChildCountQuery(String dbType) {
        String countSubquery = getChildCountSubquery();

        return String.format(
            "SELECT t.*, %s as child_count FROM %s t " +
            "WHERE t.%s = ?1 %s",
            countSubquery, tableName, parentIdColumn,
            getOrderByClauseWithAlias(dbType));
    }

    /**
     * Generates ORDER BY clause with table alias for queries using 't' alias.
     *
     * @param dbType the database type
     * @return ORDER BY clause with 't.' prefix on column names
     */
    private String getOrderByClauseWithAlias(String dbType) {
        String dir = getSortDirectionSql();
        String nullsLast = sortDirection == SortDirection.DESC ? "NULLS FIRST" : "NULLS LAST";

        if ("postgresql".equals(dbType) || "oracle".equals(dbType)) {
            return sortOrderColumn != null ?
                String.format("ORDER BY t.%s %s %s, t.%s %s", sortOrderColumn, dir, nullsLast, idColumn, dir) :
                String.format("ORDER BY t.%s %s", idColumn, dir);
        } else {
            return sortOrderColumn != null ?
                String.format("ORDER BY t.%s %s, t.%s %s", sortOrderColumn, dir, idColumn, dir) :
                String.format("ORDER BY t.%s %s", idColumn, dir);
        }
    }
} 