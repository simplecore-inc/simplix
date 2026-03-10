package dev.simplecore.simplix.core.tree.base;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.SortDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TreeQueries soft-delete condition injection.
 * Verifies that all query methods correctly include/exclude soft-delete
 * conditions based on whether softDeleteColumn is configured.
 */
@DisplayName("TreeQueries Soft-Delete Condition Test")
class TreeQueriesSoftDeleteTest {

    private static final String TABLE = "departments";
    private static final String ID_COL = "id";
    private static final String PARENT_COL = "parent_id";
    private static final String SORT_COL = "sort_order";
    private static final String SOFT_DELETE_COL = "deleted";

    private final TreeQueries withSoftDelete = new TreeQueries(
        TABLE, ID_COL, PARENT_COL, SORT_COL,
        SortDirection.ASC, new LookupColumn[0], SOFT_DELETE_COL
    );

    private final TreeQueries withoutSoftDelete = new TreeQueries(
        TABLE, ID_COL, PARENT_COL, SORT_COL,
        SortDirection.ASC, new LookupColumn[0], null
    );

    // =================================================================================
    // getDirectChildrenQuery
    // =================================================================================

    @Nested
    @DisplayName("getDirectChildrenQuery")
    class DirectChildren {

        @Test
        @DisplayName("includes soft-delete condition when configured")
        void withSoftDeleteCondition() {
            String sql = withSoftDelete.getDirectChildrenQuery();

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("parent_id = ?1");
        }

        @Test
        @DisplayName("omits soft-delete condition when not configured")
        void withoutSoftDeleteCondition() {
            String sql = withoutSoftDelete.getDirectChildrenQuery();

            assertThat(sql).doesNotContain("deleted");
            assertThat(sql).contains("parent_id = ?1");
        }
    }

    // =================================================================================
    // getRootItemsQuery
    // =================================================================================

    @Nested
    @DisplayName("getRootItemsQuery")
    class RootItems {

        @Test
        @DisplayName("includes condition in parameterless overload")
        void parameterless() {
            String sql = withSoftDelete.getRootItemsQuery();

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("parent_id IS NULL");
        }

        @Test
        @DisplayName("includes condition for H2")
        void h2() {
            String sql = withSoftDelete.getRootItemsQuery("h2");

            assertThat(sql).contains("AND deleted = false");
        }

        @Test
        @DisplayName("includes condition for PostgreSQL with parenthesized OR")
        void postgresql() {
            String sql = withSoftDelete.getRootItemsQuery("postgresql");

            assertThat(sql).contains("AND deleted = false");
            // OR condition must be parenthesized to prevent incorrect precedence
            assertThat(sql).contains("(parent_id IS NULL OR TRIM(parent_id) = '')");
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getRootItemsQuery("postgresql");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getHierarchyQuery (CTE recursive)
    // =================================================================================

    @Nested
    @DisplayName("getHierarchyQuery")
    class Hierarchy {

        @Test
        @DisplayName("PostgreSQL: includes condition in anchor and recursive member")
        void postgresqlBothMembers() {
            String sql = withSoftDelete.getHierarchyQuery("postgresql");

            // Anchor: root nodes must be active
            assertThat(sql).contains("AND deleted = false");
            // Recursive: child nodes must be active (aliased)
            assertThat(sql).contains("AND c.deleted = false");
        }

        @Test
        @DisplayName("MySQL: includes condition in anchor and recursive member")
        void mysqlBothMembers() {
            String sql = withSoftDelete.getHierarchyQuery("mysql");

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("AND c.deleted = false");
        }

        @Test
        @DisplayName("Oracle: includes condition in START WITH and CONNECT BY")
        void oracleBothClauses() {
            String sql = withSoftDelete.getHierarchyQuery("oracle");

            assertThat(sql).contains("AND t.deleted = false");
            // Should appear twice: once in START WITH, once in CONNECT BY
            assertThat(countOccurrences(sql, "AND t.deleted = false")).isEqualTo(2);
        }

        @Test
        @DisplayName("MSSQL: includes condition in anchor and recursive member")
        void mssqlBothMembers() {
            String sql = withSoftDelete.getHierarchyQuery("mssql");

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("AND c.deleted = false");
        }

        @Test
        @DisplayName("H2: includes condition in CTE anchor and recursive member")
        void h2BothMembers() {
            String sql = withSoftDelete.getHierarchyQuery("h2");

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("AND c.deleted = false");
        }

        @Test
        @DisplayName("fallback: includes condition via WHERE clause")
        void fallback() {
            String sql = withSoftDelete.getHierarchyQuery("unknown");

            assertThat(sql).contains("WHERE deleted = false");
        }

        @Test
        @DisplayName("omits all conditions when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getHierarchyQuery("postgresql");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getDescendantsQuery (CTE recursive)
    // =================================================================================

    @Nested
    @DisplayName("getDescendantsQuery")
    class Descendants {

        @Test
        @DisplayName("PostgreSQL: includes condition in anchor and recursive member")
        void postgresqlBothMembers() {
            String sql = withSoftDelete.getDescendantsQuery("postgresql");

            // Anchor: starting node must be active
            assertThat(sql).contains("id = ?1 AND deleted = false");
            // Recursive: child traversal excludes deleted
            assertThat(sql).contains("AND c.deleted = false");
        }

        @Test
        @DisplayName("Oracle: includes condition in START WITH and CONNECT BY")
        void oracleBothClauses() {
            String sql = withSoftDelete.getDescendantsQuery("oracle");

            assertThat(sql).contains("AND t.deleted = false");
            assertThat(countOccurrences(sql, "AND t.deleted = false")).isEqualTo(2);
        }

        @Test
        @DisplayName("H2: includes condition in CTE anchor and recursive member")
        void h2BothMembers() {
            String sql = withSoftDelete.getDescendantsQuery("h2");

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("AND c.deleted = false");
        }

        @Test
        @DisplayName("omits all conditions when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getDescendantsQuery("postgresql");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getAncestorsQuery (CTE recursive)
    // =================================================================================

    @Nested
    @DisplayName("getAncestorsQuery")
    class Ancestors {

        @Test
        @DisplayName("PostgreSQL: includes condition only in recursive member (not anchor)")
        void postgresqlRecursiveMemberOnly() {
            String sql = withSoftDelete.getAncestorsQuery("postgresql");

            // Anchor: no soft-delete condition (starting node is known)
            assertThat(sql).contains("FROM " + TABLE + " WHERE id = ?1 ");
            // Recursive: parent traversal excludes deleted ancestors
            assertThat(sql).contains("AND p.deleted = false");
        }

        @Test
        @DisplayName("Oracle: includes condition in CONNECT BY (not START WITH)")
        void oracleConnectByOnly() {
            String sql = withSoftDelete.getAncestorsQuery("oracle");

            assertThat(sql).contains("AND t.deleted = false");
            // Only in CONNECT BY, not in START WITH
            assertThat(countOccurrences(sql, "AND t.deleted = false")).isEqualTo(1);
        }

        @Test
        @DisplayName("H2: includes condition only in recursive member")
        void h2RecursiveMemberOnly() {
            String sql = withSoftDelete.getAncestorsQuery("h2");

            assertThat(sql).contains("AND p.deleted = false");
        }

        @Test
        @DisplayName("omits all conditions when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getAncestorsQuery("postgresql");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getSiblingsQuery
    // =================================================================================

    @Nested
    @DisplayName("getSiblingsQuery")
    class Siblings {

        @Test
        @DisplayName("includes condition in outer query")
        void includesCondition() {
            String sql = withSoftDelete.getSiblingsQuery();

            assertThat(sql).contains("AND deleted = false");
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getSiblingsQuery();

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getLeafNodesQuery
    // =================================================================================

    @Nested
    @DisplayName("getLeafNodesQuery")
    class LeafNodes {

        @Test
        @DisplayName("includes condition in both outer and inner query")
        void includesInBothQueries() {
            String sql = withSoftDelete.getLeafNodesQuery();

            // Should appear twice: once in outer WHERE, once in subquery WHERE
            assertThat(countOccurrences(sql, "AND deleted = false")).isEqualTo(2);
        }

        @Test
        @DisplayName("includes condition for H2")
        void h2() {
            String sql = withSoftDelete.getLeafNodesQuery("h2");

            assertThat(countOccurrences(sql, "AND deleted = false")).isEqualTo(2);
        }

        @Test
        @DisplayName("includes condition for PostgreSQL")
        void postgresql() {
            String sql = withSoftDelete.getLeafNodesQuery("postgresql");

            assertThat(countOccurrences(sql, "AND deleted = false")).isEqualTo(2);
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getLeafNodesQuery();

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getChildCountQuery
    // =================================================================================

    @Nested
    @DisplayName("getChildCountQuery")
    class ChildCount {

        @Test
        @DisplayName("includes condition in WHERE clause")
        void includesCondition() {
            String sql = withSoftDelete.getChildCountQuery("h2");

            assertThat(sql).contains("AND deleted = false");
            assertThat(sql).contains("GROUP BY");
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getChildCountQuery("h2");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getRootItemsWithChildCountQuery (includes subquery)
    // =================================================================================

    @Nested
    @DisplayName("getRootItemsWithChildCountQuery")
    class RootItemsWithChildCount {

        @Test
        @DisplayName("includes condition in outer query and child count subquery")
        void includesInBothQueries() {
            String sql = withSoftDelete.getRootItemsWithChildCountQuery("h2");

            // Outer: t.deleted = false
            assertThat(sql).contains("AND t.deleted = false");
            // Subquery: child.deleted = false
            assertThat(sql).contains("AND child.deleted = false");
        }

        @Test
        @DisplayName("PostgreSQL: parenthesizes OR condition with soft-delete AND")
        void postgresqlParenthesized() {
            String sql = withSoftDelete.getRootItemsWithChildCountQuery("postgresql");

            assertThat(sql).contains("(t.parent_id IS NULL OR TRIM(t.parent_id) = '')");
            assertThat(sql).contains("AND t.deleted = false");
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getRootItemsWithChildCountQuery("h2");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getDirectChildrenWithChildCountQuery (includes subquery)
    // =================================================================================

    @Nested
    @DisplayName("getDirectChildrenWithChildCountQuery")
    class DirectChildrenWithChildCount {

        @Test
        @DisplayName("includes condition in outer query and child count subquery")
        void includesInBothQueries() {
            String sql = withSoftDelete.getDirectChildrenWithChildCountQuery("h2");

            // Outer: t.deleted = false
            assertThat(sql).contains("AND t.deleted = false");
            // Subquery: child.deleted = false
            assertThat(sql).contains("AND child.deleted = false");
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getDirectChildrenWithChildCountQuery("h2");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // getLookupQuery
    // =================================================================================

    @Nested
    @DisplayName("getLookupQuery")
    class Lookup {

        @Test
        @DisplayName("includes condition before lookup conditions")
        void includesConditionBeforeLookup() {
            String sql = withSoftDelete.getLookupQuery(Map.of(), "h2");

            assertThat(sql).contains("AND deleted = false");
        }

        @Test
        @DisplayName("omits condition when not configured")
        void noSoftDelete() {
            String sql = withoutSoftDelete.getLookupQuery(Map.of(), "h2");

            assertThat(sql).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // Backward compatibility
    // =================================================================================

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompat {

        @Test
        @DisplayName("4-param constructor produces queries without soft-delete conditions")
        void fourParamConstructor() {
            TreeQueries queries = new TreeQueries(TABLE, ID_COL, PARENT_COL, SORT_COL);

            assertThat(queries.getDirectChildrenQuery()).doesNotContain("deleted");
            assertThat(queries.getRootItemsQuery()).doesNotContain("deleted");
            assertThat(queries.getHierarchyQuery("h2")).doesNotContain("deleted");
            assertThat(queries.getDescendantsQuery("h2")).doesNotContain("deleted");
        }

        @Test
        @DisplayName("6-param constructor produces queries without soft-delete conditions")
        void sixParamConstructor() {
            TreeQueries queries = new TreeQueries(
                TABLE, ID_COL, PARENT_COL, SORT_COL, SortDirection.ASC, new LookupColumn[0]
            );

            assertThat(queries.getDirectChildrenQuery()).doesNotContain("deleted");
            assertThat(queries.getChildCountQuery("h2")).doesNotContain("deleted");
        }
    }

    // =================================================================================
    // Helper
    // =================================================================================

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
