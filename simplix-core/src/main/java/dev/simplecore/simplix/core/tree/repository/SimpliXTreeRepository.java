package dev.simplecore.simplix.core.tree.repository;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Map;

/**
 * Base repository interface for tree-structured entities
 * @param <T> Entity type
 * @param <ID> ID type
 */
@NoRepositoryBean
public interface SimpliXTreeRepository<T extends TreeEntity<T, ID>, ID> extends JpaRepository<T, ID> {
    /**
     * Retrieves the entire hierarchy
     */
    List<T> findCompleteHierarchy();
    
    /**
     * Retrieves a specific item and all its descendants
     */
    List<T> findItemWithAllDescendants(ID itemId);
    
    /**
     * Retrieves root items
     */
    List<T> findRootItems();
    
    /**
     * Retrieves direct children of a specific item
     */
    List<T> findDirectChildren(ID parentId);
    
    /**
     * Builds the hierarchy in memory
     */
    List<T> buildHierarchy(List<T> allItems);

    /**
     * Retrieves items with additional search conditions
     * @param parameters Search conditions (column name: value)
     */
    List<T> findByLookup(Map<String, String> parameters);

    /**
     * Counts direct children for each parent ID using a single GROUP BY query.
     * <p>
     * Returns a list of Object[] where:
     * <ul>
     *   <li>[0] = parentId (ID type)</li>
     *   <li>[1] = count (Number, typically Long or BigInteger depending on database)</li>
     * </ul>
     * <p>
     * Parents with no children will not appear in the result.
     * Use this method to efficiently determine hasChildren/childCount for multiple nodes
     * without N+1 query problems.
     *
     * @return list of [parentId, count] pairs
     */
    List<Object[]> countChildrenByParentId();

    /**
     * Retrieves root items with child count in a single query.
     * <p>
     * Uses a correlated subquery to include child_count for each root item,
     * eliminating the need for a separate GROUP BY query.
     * <p>
     * Returns a list of Object[] where:
     * <ul>
     *   <li>[0..n-1] = entity columns</li>
     *   <li>[n] = child_count (Number)</li>
     * </ul>
     *
     * @return list of [entity, childCount] pairs
     */
    List<Object[]> findRootItemsWithChildCount();

    /**
     * Retrieves direct children with child count in a single query.
     * <p>
     * Uses a correlated subquery to include child_count for each child item,
     * eliminating the need for a separate GROUP BY query.
     * <p>
     * Returns a list of Object[] where:
     * <ul>
     *   <li>[0..n-1] = entity columns</li>
     *   <li>[n] = child_count (Number)</li>
     * </ul>
     *
     * @param parentId the parent ID to find children for
     * @return list of [entity, childCount] pairs
     */
    List<Object[]> findDirectChildrenWithChildCount(ID parentId);
} 