package dev.simplecore.simplix.core.tree.entity;

/**
 * Extension of TreeEntity for entities that support explicit sort ordering.
 * <p>
 * This interface adds the ability to set a sort order value, enabling
 * the base service to provide default implementations for reordering operations.
 * <p>
 * Use this interface when your tree entity has a dedicated sortOrder field
 * that can be modified programmatically (e.g., Integer sortOrder).
 * <p>
 * For entities that sort by immutable fields (createdAt, name, etc.),
 * use the base TreeEntity interface instead and implement getSortKey().
 * <p>
 * Example implementation:
 * <pre>{@code
 * @Entity
 * public class Category implements SortableTreeEntity<Category, String> {
 *     @Id
 *     private String id;
 *
 *     private String parentId;
 *
 *     private Integer sortOrder;
 *
 *     @Override
 *     public Integer getSortOrder() {
 *         return sortOrder;
 *     }
 *
 *     @Override
 *     public void setSortOrder(Integer sortOrder) {
 *         this.sortOrder = sortOrder;
 *     }
 *
 *     @Override
 *     public Comparable<?> getSortKey() {
 *         return sortOrder;
 *     }
 *     // ... other implementations
 * }
 * }</pre>
 *
 * @param <T> The concrete entity type
 * @param <ID> The type of the entity's identifier
 * @author System Generated
 * @since 1.0.0
 * @see TreeEntity
 * @see dev.simplecore.simplix.core.tree.service.SimpliXSortableTreeBaseService
 */
public interface SortableTreeEntity<T extends SortableTreeEntity<T, ID>, ID>
        extends TreeEntity<T, ID> {

    /**
     * Gets the sort order for this entity.
     * <p>
     * Lower values indicate higher priority in ordering.
     * Null values should be treated as lowest priority (sorted last).
     *
     * @return The current sort order value, may be null
     */
    Integer getSortOrder();

    /**
     * Sets the sort order for this entity.
     * <p>
     * This method is typically called during reordering operations
     * to update the entity's position among its siblings.
     *
     * @param sortOrder The new sort order value
     */
    void setSortOrder(Integer sortOrder);
}