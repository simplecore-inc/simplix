package dev.simplecore.simplix.core.tree.service;

import dev.simplecore.simplix.core.tree.entity.SortableTreeEntity;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extended base service for sortable tree entities.
 * <p>
 * Provides default implementation for {@link #reorderChildren(Object, List)} when entity
 * implements {@link SortableTreeEntity} interface with setSortOrder() method.
 * <p>
 * Use this service as base class when your tree entity supports explicit sort ordering.
 * For entities without sortOrder field, use {@link SimpliXTreeBaseService} instead.
 * <p>
 * Example usage:
 * <pre>{@code
 * @Service
 * public class CategoryTreeService extends SimpliXSortableTreeBaseService<Category, String> {
 *
 *     public CategoryTreeService(SimpliXTreeRepository<Category, String> repository) {
 *         super(repository);
 *     }
 *
 *     // reorderChildren() is now available with default implementation
 * }
 * }</pre>
 *
 * @param <T> Entity type implementing SortableTreeEntity
 * @param <ID> ID type
 * @author System Generated
 * @since 1.0.0
 * @see SortableTreeEntity
 * @see SimpliXTreeBaseService
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SimpliXSortableTreeBaseService<T extends SortableTreeEntity<T, ID>, ID>
        extends SimpliXTreeBaseService<T, ID> {

    public SimpliXSortableTreeBaseService(SimpliXTreeRepository<T, ID> repository) {
        super(repository);
    }

    /**
     * Reorders children of a parent node by updating their sortOrder.
     * <p>
     * This implementation assigns sequential sortOrder values (0, 1, 2, ...)
     * based on the order of IDs in the provided list.
     * <p>
     * Only children that exist and belong to the specified parent will be updated.
     * IDs not found or belonging to different parents are silently skipped.
     *
     * @param parentId The parent entity ID (null for root level siblings)
     * @param orderedChildIds List of child IDs in the desired order
     */
    @Override
    @Transactional
    public void reorderChildren(ID parentId, List<ID> orderedChildIds) {
        Assert.notNull(orderedChildIds, "Ordered child IDs list cannot be null");

        ID normalizedParentId = normalizeParentId(parentId);
        log.debug("Reordering {} children for parent: {}", orderedChildIds.size(), normalizedParentId);

        // Get current children for the parent
        List<T> children = normalizedParentId == null
                ? findRoots()
                : findDirectChildren(normalizedParentId);

        // Create map for quick lookup
        Map<ID, T> childMap = children.stream()
                .collect(Collectors.toMap(T::getId, c -> c));

        // Update sort order based on position in the ordered list
        int order = 0;
        for (ID childId : orderedChildIds) {
            T child = childMap.get(childId);
            if (child != null) {
                child.setSortOrder(order++);
                update(child);
                log.trace("Set sortOrder {} for child: {}", order - 1, childId);
            } else {
                log.debug("Child {} not found or doesn't belong to parent {}, skipping",
                        childId, normalizedParentId);
            }
        }

        log.info("Successfully reordered {} children for parent: {}",
                order, normalizedParentId);
    }
}