package dev.simplecore.simplix.core.tree.base;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn.ColumnType;
import dev.simplecore.simplix.core.tree.annotation.SortDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TreeQueries - Extended Coverage")
class TreeQueriesExtendedTest {

    private LookupColumn createLookupColumn(String name, ColumnType type) {
        LookupColumn col = mock(LookupColumn.class);
        when(col.name()).thenReturn(name);
        when(col.type()).thenReturn(type);
        return col;
    }

    @Nested
    @DisplayName("getHierarchyQuery")
    class HierarchyQuery {

        @Test
        @DisplayName("should generate Oracle-specific query")
        void shouldGenerateOracleQuery() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getHierarchyQuery("oracle");
            assertThat(sql).contains("CONNECT BY NOCYCLE").contains("START WITH");
        }

        @Test
        @DisplayName("should generate MSSQL-specific query")
        void shouldGenerateMssqlQuery() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getHierarchyQuery("mssql");
            assertThat(sql).contains("WITH RECURSIVE").contains("LTRIM(RTRIM");
        }

        @Test
        @DisplayName("should generate H2-specific query")
        void shouldGenerateH2Query() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getHierarchyQuery("h2");
            assertThat(sql).contains("WITH RECURSIVE").contains("INNER JOIN");
        }

        @Test
        @DisplayName("should generate fallback for unknown database")
        void shouldGenerateFallbackForUnknown() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getHierarchyQuery("unknown");
            assertThat(sql).contains("SELECT *, 1 as level FROM items");
        }
    }

    @Nested
    @DisplayName("getDescendantsQuery")
    class DescendantsQuery {

        @Test
        @DisplayName("should generate Oracle descendants query")
        void shouldGenerateOracleDescendants() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getDescendantsQuery("oracle");
            assertThat(sql).contains("CONNECT BY NOCYCLE").contains("START WITH");
        }

        @Test
        @DisplayName("should generate MSSQL descendants query")
        void shouldGenerateMssqlDescendants() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getDescendantsQuery("mssql");
            assertThat(sql).contains("WITH RECURSIVE");
        }

        @Test
        @DisplayName("should generate H2 descendants query")
        void shouldGenerateH2Descendants() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getDescendantsQuery("h2");
            assertThat(sql).contains("WITH RECURSIVE").contains("INNER JOIN");
        }

        @Test
        @DisplayName("should generate fallback descendants query")
        void shouldGenerateFallbackDescendants() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getDescendantsQuery("unknown");
            assertThat(sql).contains("SELECT *, 1 as level FROM items WHERE id = ?1");
        }
    }

    @Nested
    @DisplayName("getAncestorsQuery")
    class AncestorsQuery {

        @Test
        @DisplayName("should generate Oracle ancestors query")
        void shouldGenerateOracleAncestors() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getAncestorsQuery("oracle");
            assertThat(sql).contains("CONNECT BY NOCYCLE");
        }

        @Test
        @DisplayName("should generate H2 ancestors query")
        void shouldGenerateH2Ancestors() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getAncestorsQuery("h2");
            assertThat(sql).contains("WITH RECURSIVE ancestors");
        }

        @Test
        @DisplayName("should generate fallback ancestors query")
        void shouldGenerateFallbackAncestors() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getAncestorsQuery("unknown");
            assertThat(sql).contains("SELECT * FROM items WHERE id = ?1");
        }

        @Test
        @DisplayName("should generate MySQL/PostgreSQL ancestors query")
        void shouldGeneratePostgresAncestors() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getAncestorsQuery("postgresql");
            assertThat(sql).contains("WITH RECURSIVE ancestors");
        }

        @Test
        @DisplayName("should generate MSSQL ancestors query")
        void shouldGenerateMssqlAncestors() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getAncestorsQuery("mssql");
            assertThat(sql).contains("WITH RECURSIVE ancestors");
        }
    }

    @Nested
    @DisplayName("getRootItemsQuery")
    class RootItemsQuery {

        @Test
        @DisplayName("should generate H2 root query")
        void shouldGenerateH2Root() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getRootItemsQuery("h2");
            assertThat(sql).contains("parent_id IS NULL");
        }

        @Test
        @DisplayName("should generate non-H2 root query with TRIM")
        void shouldGenerateNonH2Root() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getRootItemsQuery("postgresql");
            assertThat(sql).contains("TRIM(parent_id)");
        }
    }

    @Nested
    @DisplayName("getLeafNodesQuery with dbType")
    class LeafNodesQuery {

        @Test
        @DisplayName("should generate H2 leaf nodes query")
        void shouldGenerateH2LeafNodes() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getLeafNodesQuery("h2");
            assertThat(sql).contains("NOT IN");
        }

        @Test
        @DisplayName("should generate non-H2 leaf nodes query with TRIM")
        void shouldGenerateNonH2LeafNodes() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getLeafNodesQuery("postgresql");
            assertThat(sql).contains("TRIM(parent_id)");
        }
    }

    @Nested
    @DisplayName("Sort direction")
    class SortDirectionTests {

        @Test
        @DisplayName("should generate DESC ordering")
        void shouldGenerateDescOrdering() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    SortDirection.DESC, new LookupColumn[0]);
            String sql = queries.getRootItemsQuery();
            assertThat(sql).contains("DESC");
        }

        @Test
        @DisplayName("should handle null sortDirection")
        void shouldHandleNullSortDirection() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    null, new LookupColumn[0]);
            String sql = queries.getRootItemsQuery();
            assertThat(sql).contains("ASC");
        }
    }

    @Nested
    @DisplayName("Lookup queries")
    class LookupQueries {

        @Test
        @DisplayName("should build query with STRING lookup column")
        void shouldBuildWithStringLookup() {
            LookupColumn col = createLookupColumn("name", ColumnType.STRING);
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    new LookupColumn[]{col});

            String sql = queries.getLookupQuery(Map.of("name", "test"), "postgresql");
            assertThat(sql).contains("name = 'test'");
        }

        @Test
        @DisplayName("should build query with NUMBER lookup column")
        void shouldBuildWithNumberLookup() {
            LookupColumn col = createLookupColumn("amount", ColumnType.NUMBER);
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    new LookupColumn[]{col});

            String sql = queries.getLookupQuery(Map.of("amount", "42"), "postgresql");
            assertThat(sql).contains("amount = 42");
        }

        @Test
        @DisplayName("should build query with BOOLEAN lookup column")
        void shouldBuildWithBooleanLookup() {
            LookupColumn col = createLookupColumn("active", ColumnType.BOOLEAN);
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    new LookupColumn[]{col});

            String sql = queries.getLookupQuery(Map.of("active", "true"), "postgresql");
            assertThat(sql).contains("active = true");
        }

        @Test
        @DisplayName("should handle Oracle BOOLEAN as 1/0")
        void shouldHandleOracleBoolean() {
            LookupColumn col = createLookupColumn("active", ColumnType.BOOLEAN);
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    new LookupColumn[]{col});

            String sql = queries.getLookupQuery(Map.of("active", "true"), "oracle");
            assertThat(sql).contains("active = 1");
        }

        @Test
        @DisplayName("should handle empty parameters")
        void shouldHandleEmptyParams() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getLookupQuery(Map.of(), "postgresql");
            assertThat(sql).contains("WHERE 1=1");
        }
    }

    @Nested
    @DisplayName("Soft delete queries")
    class SoftDeleteQueries {

        @Test
        @DisplayName("should include soft delete condition in queries")
        void shouldIncludeSoftDelete() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    SortDirection.ASC, new LookupColumn[0], "deleted");

            String rootQuery = queries.getRootItemsQuery();
            assertThat(rootQuery).contains("deleted = false");

            String childrenQuery = queries.getDirectChildrenQuery();
            assertThat(childrenQuery).contains("deleted = false");
        }
    }

    @Nested
    @DisplayName("Child count queries")
    class ChildCountQueries {

        @Test
        @DisplayName("should generate child count query")
        void shouldGenerateChildCountQuery() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getChildCountQuery("postgresql");
            assertThat(sql).contains("COUNT(*)").contains("GROUP BY");
        }

        @Test
        @DisplayName("should generate root items with child count query for H2")
        void shouldGenerateRootWithCountH2() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getRootItemsWithChildCountQuery("h2");
            assertThat(sql).contains("child_count").contains("parent_id IS NULL");
        }

        @Test
        @DisplayName("should generate root items with child count query for non-H2")
        void shouldGenerateRootWithCountNonH2() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getRootItemsWithChildCountQuery("postgresql");
            assertThat(sql).contains("child_count").contains("TRIM");
        }

        @Test
        @DisplayName("should generate children with child count query")
        void shouldGenerateChildrenWithCount() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getDirectChildrenWithChildCountQuery("postgresql");
            assertThat(sql).contains("child_count").contains("parent_id = ?1");
        }
    }

    @Nested
    @DisplayName("Siblings query")
    class SiblingsQuery {

        @Test
        @DisplayName("should generate siblings query")
        void shouldGenerateSiblingsQuery() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getSiblingsQuery();
            assertThat(sql).contains("parent_id = (").contains("id != ?1");
        }
    }

    @Nested
    @DisplayName("Leaf nodes query (no dbType)")
    class LeafNodesQueryNoDbType {

        @Test
        @DisplayName("should generate leaf nodes query without dbType")
        void shouldGenerateLeafNodesQuery() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order");
            String sql = queries.getLeafNodesQuery();
            assertThat(sql).contains("NOT IN");
        }
    }

    @Nested
    @DisplayName("NULLS LAST for PostgreSQL and Oracle")
    class NullsLast {

        @Test
        @DisplayName("should include NULLS LAST for PostgreSQL")
        void shouldIncludeNullsLastPostgres() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    SortDirection.ASC, new LookupColumn[0]);
            String sql = queries.getRootItemsQuery("postgresql");
            assertThat(sql).contains("NULLS LAST");
        }

        @Test
        @DisplayName("should include NULLS FIRST for DESC on PostgreSQL")
        void shouldIncludeNullsFirstForDesc() {
            TreeQueries queries = new TreeQueries("items", "id", "parent_id", "sort_order",
                    SortDirection.DESC, new LookupColumn[0]);
            String sql = queries.getRootItemsQuery("postgresql");
            assertThat(sql).contains("NULLS FIRST");
        }
    }
}
