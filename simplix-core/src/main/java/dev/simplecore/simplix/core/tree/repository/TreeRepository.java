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
public interface TreeRepository<T extends TreeEntity<T, ID>, ID> extends JpaRepository<T, ID> {
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
} 