package dev.simplecore.simplix.core.tree.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Comprehensive service interface for managing tree-structured entities.
 * 
 * This service provides a complete set of operations for hierarchical data management including:
 * - Basic CRUD operations with tree structure validation
 * - Tree traversal and navigation operations
 * - Tree manipulation operations (move, copy, reorder)
 * - Bulk operations for performance optimization
 * - Tree analysis and metrics
 * - Search and filtering with hierarchical context
 * - Data validation and integrity checking
 * 
 * All operations are designed to maintain tree integrity and prevent circular references.
 * Performance optimizations include caching frequently accessed data and batch processing
 * capabilities for large-scale operations.
 * 
 * @param <T> The entity type that implements TreeEntity
 * @param <ID> The type of the entity's identifier
 * 
 * @author System Generated
 * @since 1.0.0
 */
public interface TreeService<T extends TreeEntity<T, ID>, ID> {
    
    // =================================================================================
    // BASIC CRUD OPERATIONS
    // =================================================================================
    
    /**
     * Creates a new tree entity with validation.
     * 
     * Validates the parent relationship and tree structure integrity before creation.
     * If a parent ID is specified, ensures the parent exists and the operation won't
     * create circular references.
     * 
     * @param entity The entity to create (must not be null)
     * @return The created entity with generated ID
     * @throws IllegalArgumentException if the entity is null or validation fails
     * @throws NoSuchElementException if the specified parent doesn't exist
     */
    T create(T entity);

    /**
     * Updates an existing tree entity with structure validation.
     * 
     * Validates any changes to the parent relationship and ensures tree integrity.
     * Clears relevant caches after update to maintain consistency.
     * 
     * @param entity The entity to update (must not be null, must have valid ID)
     * @return The updated entity
     * @throws IllegalArgumentException if the entity is null or validation fails
     * @throws NoSuchElementException if the entity or specified parent doesn't exist
     */
    T update(T entity);

    /**
     * Finds an entity by its ID.
     * 
     * @param id The entity ID (must not be null)
     * @return Optional containing the entity if found, empty otherwise
     */
    Optional<T> findById(ID id);

    /**
     * Retrieves all entities with pagination support.
     * 
     * @param pageable Pagination information (must not be null)
     * @return Page containing the requested entities
     */
    Page<T> findAll(Pageable pageable);

    /**
     * Deletes an entity by ID.
     * 
     * Note: This operation may leave orphaned children depending on the implementation.
     * Consider using cascading delete or reassigning children before deletion.
     * Clears all related caches after deletion.
     * 
     * @param id The ID of the entity to delete (must not be null)
     */
    void deleteById(ID id);
    
    // =================================================================================
    // TREE TRAVERSAL AND NAVIGATION
    // =================================================================================

    /**
     * Retrieves the complete tree hierarchy with all relationships populated.
     * 
     * Uses database-specific recursive queries when available for optimal performance.
     * Falls back to in-memory hierarchy building for unsupported databases.
     * 
     * @return List of root entities with their complete hierarchy
     */
    List<T> findCompleteHierarchy();

    /**
     * Finds a specific entity along with all its descendants.
     * 
     * Uses optimized recursive queries to retrieve the entire subtree
     * rooted at the specified entity.
     * 
     * @param id The root entity ID for the subtree (must not be null)
     * @return List containing the root entity and all its descendants
     */
    List<T> findWithDescendants(ID id);

    /**
     * Retrieves all root entities (entities with no parent).
     * 
     * @return List of root entities ordered by sort order if available
     */
    List<T> findRoots();

    /**
     * Finds direct children of a specific entity.
     * 
     * @param parentId The parent entity ID (must not be null)
     * @return List of direct children ordered by sort order if available
     */
    List<T> findDirectChildren(ID parentId);

    /**
     * Retrieves all ancestors of a specific entity up to the root.
     * 
     * The result is ordered from immediate parent to root.
     * Uses caching for frequently accessed ancestor paths.
     * 
     * @param id The entity ID (must not be null)
     * @return List of ancestor entities from parent to root
     */
    List<T> findAncestors(ID id);

    /**
     * Finds all sibling entities (entities with the same parent).
     * 
     * @param id The entity ID (must not be null)
     * @return List of sibling entities excluding the entity itself
     */
    List<T> findSiblings(ID id);
    
    // =================================================================================
    // TREE MANIPULATION OPERATIONS
    // =================================================================================

    /**
     * Moves an entity to a new parent with circular reference prevention.
     * 
     * Validates that the move operation won't create circular references
     * and updates all related caches. The operation is performed within
     * a transaction to ensure consistency.
     * 
     * @param id The ID of the entity to move (must not be null)
     * @param newParentId The ID of the new parent (null for root level)
     * @return The moved entity
     * @throws IllegalArgumentException if the operation would create circular references
     * @throws NoSuchElementException if the entity or new parent doesn't exist
     */
    T move(ID id, ID newParentId);

    /**
     * Copies a complete subtree to a new location.
     * 
     * Creates deep copies of the specified entity and all its descendants,
     * assigning new IDs and establishing the hierarchy under the new parent.
     * 
     * @param sourceId The root entity ID of the subtree to copy (must not be null)
     * @param targetParentId The new parent ID (null for root level)
     * @return The root entity of the copied subtree
     * @throws IllegalArgumentException if copying would create invalid structure
     * @throws NoSuchElementException if source or target entities don't exist
     */
    T copySubtree(ID sourceId, ID targetParentId);

    /**
     * Copies a subtree with property modifications.
     * 
     * Similar to copySubtree but allows specifying property overrides
     * that will be applied to all copied entities.
     * 
     * @param sourceId The root entity ID of the subtree to copy
     * @param targetParentId The new parent ID
     * @param propertyOverrides Map of property names to new values
     * @return The root entity of the copied subtree
     */
    T copySubtreeWithProperties(ID sourceId, ID targetParentId, Map<String, Object> propertyOverrides);

    /**
     * Reorders the children of a specific parent entity.
     * 
     * Updates the sort order of child entities according to the provided
     * ordered list of IDs. All current children must be included in the list.
     * 
     * @param parentId The parent entity ID (must not be null)
     * @param orderedChildIds List of child IDs in the desired order
     * @throws IllegalArgumentException if the ID list doesn't match current children
     */
    void reorderChildren(ID parentId, List<ID> orderedChildIds);
    
    // =================================================================================
    // SEARCH AND FILTERING OPERATIONS
    // =================================================================================

    /**
     * Searches entities using lookup columns defined in @TreeEntityAttributes.
     * 
     * Performs efficient queries using the predefined lookup columns
     * with appropriate type conversion and database optimization.
     * 
     * @param parameters Map of column names to search values (must not be null)
     * @return List of entities matching the search criteria
     */
    List<T> findByLookup(Map<String, String> parameters);

    /**
     * Searches entities with pagination using lookup columns.
     * 
     * @param parameters Map of column names to search values (must not be null)
     * @param pageable Pagination information (must not be null)
     * @return Page of entities matching the search criteria
     */
    Page<T> findByLookup(Map<String, String> parameters, Pageable pageable);

    /**
     * Searches entities and includes their hierarchical context.
     * 
     * Finds entities matching the criteria and includes all their ancestors
     * to provide complete hierarchical context for the results.
     * 
     * @param parameters Search parameters (must not be null)
     * @return Hierarchical structure including matching entities and their ancestors
     */
    List<T> findHierarchyByLookup(Map<String, String> parameters);

    /**
     * Finds entities at a specific depth level in the tree.
     * 
     * @param level The depth level (0 for roots, 1 for first level children, etc.)
     * @return List of entities at the specified level
     */
    List<T> findByLevel(int level);

    /**
     * Finds all leaf nodes (entities with no children).
     * 
     * @return List of leaf entities
     */
    List<T> findLeafNodes();

    /**
     * Finds entities matching a custom predicate with tree traversal.
     * 
     * Performs depth-first search to find entities matching the given predicate.
     * 
     * @param predicate The condition to match entities
     * @return List of entities matching the predicate
     */
    List<T> findByPredicate(Predicate<T> predicate);
    
    // =================================================================================
    // BULK OPERATIONS
    // =================================================================================

    /**
     * Creates multiple entities in a single batch operation.
     * 
     * Optimizes performance for large-scale insertions by using batch processing
     * and minimal validation overhead.
     * 
     * @param entities List of entities to create (must not be null or empty)
     * @return List of created entities with generated IDs
     */
    List<T> createBatch(List<T> entities);

    /**
     * Updates multiple entities in a single batch operation.
     * 
     * @param entities List of entities to update (must not be null or empty)
     * @return List of updated entities
     */
    List<T> updateBatch(List<T> entities);

    /**
     * Deletes multiple entities by their IDs.
     * 
     * @param ids List of entity IDs to delete (must not be null or empty)
     */
    void deleteBatch(List<ID> ids);
    
    // =================================================================================
    // ANALYSIS AND METRICS
    // =================================================================================

    /**
     * Calculates comprehensive tree statistics.
     * 
     * Analyzes the entire tree structure and returns key metrics
     * useful for monitoring and optimization.
     * 
     * @return Map containing various tree metrics:
     *         - totalNodes: Total number of entities
     *         - maxDepth: Maximum depth of the tree
     *         - avgDepth: Average depth of all entities
     *         - leafNodes: Number of leaf entities
     *         - branchNodes: Number of non-leaf entities
     *         - rootNodes: Number of root entities
     *         - avgChildren: Average number of children per non-leaf entity
     */
    Map<String, Number> getTreeMetrics();

    /**
     * Checks if one entity is a descendant of another.
     * 
     * Uses cached ancestor information for performance optimization.
     * 
     * @param id The potential descendant entity ID
     * @param ancestorId The potential ancestor entity ID
     * @return true if the first entity is a descendant of the second
     */
    boolean isDescendantOf(ID id, ID ancestorId);

    /**
     * Calculates the depth of an entity in the tree.
     * 
     * Root entities have depth 0, their children have depth 1, and so on.
     * Uses caching for frequently accessed depths.
     * 
     * @param id The entity ID (must not be null)
     * @return The depth of the entity (0 for roots)
     */
    int getDepth(ID id);

    /**
     * Gets the complete path from root to the specified entity.
     * 
     * @param id The entity ID (must not be null)
     * @return List of entities from root to the specified entity (inclusive)
     */
    List<T> getPath(ID id);
    
    // =================================================================================
    // VALIDATION AND INTEGRITY
    // =================================================================================

    /**
     * Validates the integrity of the entire tree structure.
     * 
     * Checks for circular references, orphaned entities, and other
     * structural inconsistencies.
     * 
     * @return Map of validation results with issue descriptions
     */
    Map<String, List<String>> validateTreeIntegrity();

    /**
     * Repairs common tree structure issues.
     * 
     * Attempts to fix orphaned entities and other recoverable issues
     * found during integrity validation.
     * 
     * @return Number of issues repaired
     */
    int repairTreeStructure();

    List<T> getDirectChildren(ID id);
    List<T> getAllDescendants(ID id);
    List<T> getRootItems();
} 