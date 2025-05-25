package dev.simplecore.simplix.core.tree.entity;

import java.util.List;

/**
 * Core interface for entities that implement tree structure functionality.
 * 
 * This interface defines the essential operations and properties required for any entity
 * that participates in a hierarchical tree structure. It provides a standardized way
 * to handle parent-child relationships, navigation, and tree manipulation operations.
 * 
 * Key Features:
 * - Parent-child relationship management
 * - Hierarchical navigation support
 * - Sort order management for sibling ordering
 * - Utility methods for tree structure analysis
 * 
 * Implementation Guidelines:
 * - Entities should implement proper equals() and hashCode() based on ID
 * - Parent ID should be validated to prevent circular references
 * - Children list should be lazily loaded when possible
 * - Sort order should be used for consistent ordering of siblings
 * 
 * Performance Considerations:
 * - Use lazy loading for children collections to avoid deep loading
 * - Consider caching frequently accessed tree paths
 * - Implement efficient bulk operations for large tree modifications
 * 
 * @param <T> The concrete entity type that implements this interface
 * @param <ID> The type of the entity's identifier (e.g., Long, String, UUID)
 * 
 * @author System Generated
 * @since 1.0.0
 */
public interface TreeEntity<T extends TreeEntity<T, ID>, ID> {
    
    /**
     * Returns the unique identifier of this entity.
     * 
     * This ID is used to establish relationships between entities and should be
     * unique within the tree structure. The ID may be null for new entities
     * that haven't been persisted yet.
     * 
     * @return The entity's unique identifier, may be null for new entities
     */
    ID getId();
    
    /**
     * Sets the unique identifier for this entity.
     * 
     * This method is typically called by the persistence framework when
     * creating new entities. Manual ID assignment should be done with care
     * to avoid conflicts.
     * 
     * @param id The unique identifier to assign to this entity
     */
    void setId(ID id);
    
    /**
     * Returns the identifier of this entity's parent in the tree hierarchy.
     * 
     * A null parent ID indicates that this entity is a root node. The parent ID
     * should always refer to an existing entity to maintain tree integrity.
     * 
     * @return The parent entity's ID, or null if this is a root entity
     */
    ID getParentId();
    
    /**
     * Sets the parent identifier for this entity.
     * 
     * Setting the parent ID establishes the hierarchical relationship. Setting
     * it to null makes the entity a root node. Implementations should validate
     * that the new parent exists and that the operation doesn't create circular
     * references.
     * 
     * @param parentId The ID of the new parent entity, or null for root level
     */
    void setParentId(ID parentId);
    
    /**
     * Returns the collection of direct child entities.
     * 
     * This collection represents the immediate children of this entity in the
     * tree hierarchy. It should not include grandchildren or deeper descendants.
     * The collection may be empty for leaf nodes.
     * 
     * @return List of direct child entities, never null but may be empty
     */
    List<T> getChildren();
    
    /**
     * Sets the collection of direct child entities.
     * 
     * This method updates the children collection. When setting children,
     * ensure that their parent IDs are properly updated to maintain consistency.
     * 
     * @param children The new collection of child entities, should not be null
     */
    void setChildren(List<T> children);

    /**
     * Returns the sort order value for ordering siblings.
     * 
     * The sort order is used to maintain a consistent ordering of sibling
     * entities within the same parent. Lower values appear first in the order.
     * The default implementation returns 0.
     * 
     * @return The sort order value, typically a non-negative integer
     */
    default Integer getSortOrder() {
        return 0;
    }

    /**
     * Sets the sort order value for this entity.
     * 
     * The sort order determines the position of this entity among its siblings.
     * Implementations should ensure that sort orders are properly maintained
     * when entities are reordered.
     * 
     * @param sortOrder The new sort order value
     */
    void setSortOrder(Integer sortOrder);

    /**
     * Determines if this entity is a root node in the tree.
     * 
     * A root node is an entity that has no parent (parent ID is null).
     * Root nodes are the entry points into tree hierarchies.
     * 
     * @return true if this entity has no parent, false otherwise
     */
    default boolean isRoot() {
        return getParentId() == null;
    }

    /**
     * Determines if this entity is a leaf node in the tree.
     * 
     * A leaf node is an entity that has no children. Leaf nodes are the
     * terminal nodes in tree hierarchies where no further descent is possible.
     * 
     * @return true if this entity has no children, false otherwise
     */
    default boolean isLeaf() {
        List<T> children = getChildren();
        return children == null || children.isEmpty();
    }
} 