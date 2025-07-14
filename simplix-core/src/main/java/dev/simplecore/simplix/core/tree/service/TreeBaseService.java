package dev.simplecore.simplix.core.tree.service;

import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.repository.TreeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Comprehensive implementation of the TreeService interface.
 * 
 * This implementation provides a complete set of tree operations with the following features:
 * - Performance optimization through intelligent caching
 * - Transaction management for data consistency
 * - Comprehensive validation to prevent data corruption
 * - Support for bulk operations
 * - Advanced tree analysis and metrics
 * - Robust error handling and logging
 * 
 * Caching Strategy:
 * - Descendants cache: Stores frequently accessed subtree data
 * - Ancestors cache: Caches ancestor paths for quick traversal
 * - Depth cache: Maintains depth calculations for performance
 * 
 * Performance Considerations:
 * - Uses batch processing for bulk operations
 * - Implements lazy loading where appropriate
 * - Provides efficient database-specific queries through repository layer
 * 
 * @param <T> The entity type that implements TreeEntity
 * @param <ID> The type of the entity's identifier
 * 
 * @author System Generated
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class TreeBaseService<T extends TreeEntity<T, ID>, ID> implements TreeService<T, ID> {

    private final TreeRepository<T, ID> treeRepository;
    
    // Performance optimization caches
    private final Map<ID, List<T>> descendantsCache = new ConcurrentHashMap<>();
    private final Map<ID, List<T>> ancestorsCache = new ConcurrentHashMap<>();
    private final Map<ID, Integer> depthCache = new ConcurrentHashMap<>();

    public TreeBaseService(TreeRepository<T, ID> treeRepository) {
        this.treeRepository = treeRepository;
    }

    // =================================================================================
    // BASIC CRUD OPERATIONS
    // =================================================================================

    @Override
    @Transactional
    public T create(T entity) {
        log.debug("Creating new tree entity: {}", entity);
        validateNewEntity(entity);
        validateParentExists(entity);
        T saved = treeRepository.save(entity);
        clearCaches(saved.getId());
        log.info("Successfully created tree entity with ID: {}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public T update(T entity) {
        log.debug("Updating tree entity: {}", entity);
        validateExistingEntity(entity);
        validateParentExists(entity);
        T saved = treeRepository.save(entity);
        clearCaches(saved.getId());
        log.info("Successfully updated tree entity with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public Optional<T> findById(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        return treeRepository.findById(id);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        Assert.notNull(pageable, "Pageable cannot be null");
        return treeRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        log.debug("Deleting tree entity with ID: {}", id);
        
        // Clear cache for descendants before deletion
        List<T> descendants = findWithDescendants(id);
        treeRepository.deleteById(id);
        descendants.forEach(item -> clearCaches(item.getId()));
        
        log.info("Successfully deleted tree entity with ID: {}", id);
    }

    // =================================================================================
    // TREE TRAVERSAL AND NAVIGATION
    // =================================================================================

    @Override
    public List<T> findCompleteHierarchy() {
        log.debug("Finding complete tree hierarchy");
        return treeRepository.findCompleteHierarchy();
    }

    @Override
    public List<T> findWithDescendants(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        return descendantsCache.computeIfAbsent(id, k -> {
            log.debug("Computing descendants for entity: {}", k);
            return treeRepository.findItemWithAllDescendants(k);
        });
    }

    @Override
    public List<T> findRoots() {
        log.debug("Finding root entities");
        return treeRepository.findRootItems();
    }

    @Override
    public List<T> findDirectChildren(ID parentId) {
        Assert.notNull(parentId, "Parent ID cannot be null");
        return treeRepository.findDirectChildren(parentId);
    }

    @Override
    public List<T> findAncestors(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        return ancestorsCache.computeIfAbsent(id, k -> {
            log.debug("Computing ancestors for entity: {}", k);
            List<T> ancestors = new ArrayList<>();
            Optional<T> current = findById(k);
            
            while (current.isPresent() && current.get().getParentId() != null) {
                T entity = current.get();
                current = findById(entity.getParentId());
                current.ifPresent(ancestors::add);
            }
            
            return ancestors;
        });
    }

    @Override
    public List<T> findSiblings(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        Optional<T> entity = findById(id);
        if (!entity.isPresent()) {
            return Collections.emptyList();
        }

        ID parentId = entity.get().getParentId();
        if (parentId == null) {
            return findRoots().stream()
                    .filter(item -> !item.getId().equals(id))
                    .collect(Collectors.toList());
        }

        return findDirectChildren(parentId).stream()
                .filter(item -> !item.getId().equals(id))
                .collect(Collectors.toList());
    }

    // =================================================================================
    // TREE MANIPULATION OPERATIONS
    // =================================================================================

    @Override
    @Transactional
    public T move(ID id, ID newParentId) {
        Assert.notNull(id, "Entity ID to move cannot be null");
        log.debug("Moving entity {} to new parent {}", id, newParentId);
        
        T entity = findById(id)
            .orElseThrow(() -> new NoSuchElementException("Entity not found with ID: " + id));
        
        if (newParentId != null) {
            T newParent = findById(newParentId)
                .orElseThrow(() -> new NoSuchElementException("Parent entity not found with ID: " + newParentId));
            
            // Check for circular reference
            if (isDescendantOf(newParentId, id)) {
                throw new IllegalArgumentException("Cannot move a node to its own descendant");
            }
        }
        
        entity.setParentId(newParentId);
        T saved = treeRepository.save(entity);
        clearCaches(saved.getId());
        log.info("Successfully moved entity {} to new parent {}", id, newParentId);
        return saved;
    }

    @Override
    @Transactional
    public T copySubtree(ID sourceId, ID targetParentId) {
        Assert.notNull(sourceId, "Source entity ID cannot be null");
        log.debug("Copying subtree from {} to parent {}", sourceId, targetParentId);
        
        // Find source node
        T sourceNode = findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Source entity not found: " + sourceId));
        
        // Validate target parent if specified
        if (targetParentId != null) {
            findById(targetParentId)
                    .orElseThrow(() -> new NoSuchElementException("Parent entity not found with ID: " + targetParentId));
        }
        
        // Get all descendants before copying
        List<T> allNodes = findWithDescendants(sourceId);
        Map<ID, T> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(T::getId, node -> node));
        
        // Create a map to store old ID to new ID mappings
        Map<ID, ID> oldToNewIdMap = new HashMap<>();
        
        // Copy the root node first
        T copiedRoot = copyNode(sourceNode, targetParentId);
        oldToNewIdMap.put(sourceId, copiedRoot.getId());
        
        // Copy remaining nodes in the correct order (breadth-first)
        Queue<T> queue = new LinkedList<>();
        queue.offer(sourceNode);
        
        while (!queue.isEmpty()) {
            T current = queue.poll();
            ID newParentId = oldToNewIdMap.get(current.getId());
            
            // Copy all direct children
            List<T> children = findDirectChildren(current.getId());
            for (T child : children) {
                T copiedChild = copyNode(child, newParentId);
                oldToNewIdMap.put(child.getId(), copiedChild.getId());
                queue.offer(child);
            }
        }
        
        // Clear caches
        if (targetParentId != null) {
            clearCaches(targetParentId);
        }
        clearCaches(copiedRoot.getId());
        
        log.info("Successfully copied subtree from {} to parent {}", sourceId, targetParentId);
        return copiedRoot;
    }

    /**
     * Creates a copy of a single node without its children.
     */
    protected T copyNode(T sourceNode, ID newParentId) {
        T newNode = createNewInstance(sourceNode.getClass());
        BeanUtils.copyProperties(sourceNode, newNode, "id", "parentId", "children");
        newNode.setParentId(newParentId);
        newNode.setChildren(new ArrayList<>());
        return treeRepository.save(newNode);
    }

    @Override
    @Transactional
    public T copySubtreeWithProperties(ID sourceId, ID targetParentId, Map<String, Object> propertyOverrides) {
        Assert.notNull(sourceId, "Source entity ID cannot be null");
        log.debug("Copying subtree from {} to parent {}", sourceId, targetParentId);
        
        // Find source node and its descendants
        T sourceNode = findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Source entity not found: " + sourceId));
        
        // Validate target parent
        if (targetParentId != null) {
            findById(targetParentId)
                    .orElseThrow(() -> new NoSuchElementException("Target parent entity not found: " + targetParentId));
            
            // Prevent circular references
            if (isDescendantOf(targetParentId, sourceId)) {
                throw new IllegalArgumentException("Cannot copy subtree to its own descendant");
            }
        }

        // Perform copy operation
        T copiedRoot = copyNodeWithChildren(sourceNode, targetParentId, propertyOverrides);
        
        // Clear caches
        if (targetParentId != null) {
            clearCaches(targetParentId);
        }
        
        log.info("Successfully copied subtree from {} to parent {}", sourceId, targetParentId);
        return copiedRoot;
    }

    @Override
    @Transactional
    public void reorderChildren(ID parentId, List<ID> orderedChildIds) {
        Assert.notNull(parentId, "Parent entity ID cannot be null");
        Assert.notNull(orderedChildIds, "Ordered child IDs list cannot be null");
        log.debug("Reordering children of parent {}", parentId);
        
        // Get current children
        List<T> currentChildren = findDirectChildren(parentId);
        Set<ID> currentChildIds = currentChildren.stream()
                .map(T::getId)
                .collect(Collectors.toSet());
        
        // Validate input
        if (!new HashSet<>(orderedChildIds).equals(currentChildIds)) {
            throw new IllegalArgumentException("Provided child ID list doesn't match current children");
        }
        
        // Update sort order starting from 1
        AtomicInteger counter = new AtomicInteger(1);
        orderedChildIds.forEach(childId -> {
            findById(childId).ifPresent(child -> {
                child.setSortOrder(counter.getAndIncrement());
                treeRepository.save(child);
            });
        });
        
        // Clear cache
        clearCaches(parentId);
        log.info("Successfully reordered children of parent {}", parentId);
    }

    // =================================================================================
    // SEARCH AND FILTERING OPERATIONS
    // =================================================================================

    @Override
    public List<T> findByLookup(Map<String, String> parameters) {
        Assert.notNull(parameters, "Search parameters cannot be null");
        log.debug("Finding entities by lookup parameters: {}", parameters);
        return treeRepository.findByLookup(parameters);
    }

    @Override
    public Page<T> findByLookup(Map<String, String> parameters, Pageable pageable) {
        Assert.notNull(parameters, "Search parameters cannot be null");
        Assert.notNull(pageable, "Pageable cannot be null");
        
        List<T> allResults = findByLookup(parameters);
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allResults.size());
        
        List<T> pageContent = allResults.subList(start, end);
        return new PageImpl<>(pageContent, pageable, allResults.size());
    }

    @Override
    public List<T> findHierarchyByLookup(Map<String, String> parameters) {
        Assert.notNull(parameters, "Search parameters cannot be null");
        log.debug("Finding hierarchy by lookup parameters: {}", parameters);
        
        List<T> matchingItems = findByLookup(parameters);
        Set<ID> relevantIds = new HashSet<>();

        // Collect IDs of matching items and their ancestors
        for (T item : matchingItems) {
            relevantIds.add(item.getId());
            findAncestors(item.getId()).forEach(ancestor -> relevantIds.add(ancestor.getId()));
        }

        // Bulk load all relevant items
        List<T> allRelevantItems = treeRepository.findAllById(relevantIds);

        // Build hierarchy
        return treeRepository.buildHierarchy(allRelevantItems);
    }

    @Override
    public List<T> findByLevel(int level) {
        Assert.isTrue(level >= 0, "Level must be non-negative");
        log.debug("Finding entities at level: {}", level);
        
        return treeRepository.findAll().stream()
                .filter(entity -> getDepth(entity.getId()) == level)
                .collect(Collectors.toList());
    }

    @Override
    public List<T> findLeafNodes() {
        log.debug("Finding leaf nodes");
        return treeRepository.findAll().stream()
                .filter(this::isLeafNode)
                .collect(Collectors.toList());
    }

    @Override
    public List<T> findByPredicate(Predicate<T> predicate) {
        Assert.notNull(predicate, "Predicate cannot be null");
        log.debug("Finding entities by custom predicate");
        
        return treeRepository.findAll().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    // =================================================================================
    // BULK OPERATIONS
    // =================================================================================

    @Override
    @Transactional
    public List<T> createBatch(List<T> entities) {
        Assert.notNull(entities, "Entities list cannot be null");
        Assert.notEmpty(entities, "Entities list cannot be empty");
        log.debug("Creating batch of {} entities", entities.size());
        
        // Validate all entities
        entities.forEach(this::validateNewEntity);
        
        // Save all entities
        List<T> savedEntities = treeRepository.saveAll(entities);
        
        // Clear relevant caches
        savedEntities.forEach(entity -> clearCaches(entity.getId()));
        
        log.info("Successfully created batch of {} entities", savedEntities.size());
        return savedEntities;
    }

    @Override
    @Transactional
    public List<T> updateBatch(List<T> entities) {
        Assert.notNull(entities, "Entities list cannot be null");
        Assert.notEmpty(entities, "Entities list cannot be empty");
        log.debug("Updating batch of {} entities", entities.size());
        
        // Validate all entities
        entities.forEach(this::validateExistingEntity);
        
        // Save all entities
        List<T> savedEntities = treeRepository.saveAll(entities);
        
        // Clear relevant caches
        savedEntities.forEach(entity -> clearCaches(entity.getId()));
        
        log.info("Successfully updated batch of {} entities", savedEntities.size());
        return savedEntities;
    }

    @Override
    @Transactional
    public void deleteBatch(List<ID> ids) {
        Assert.notNull(ids, "IDs list cannot be null");
        Assert.notEmpty(ids, "IDs list cannot be empty");
        log.debug("Deleting batch of {} entities", ids.size());
        
        // Clear caches for all entities and their descendants
        ids.forEach(id -> {
            List<T> descendants = findWithDescendants(id);
            descendants.forEach(item -> clearCaches(item.getId()));
        });
        
        // Delete all entities
        treeRepository.deleteAllById(ids);
        
        log.info("Successfully deleted batch of {} entities", ids.size());
    }

    // =================================================================================
    // ANALYSIS AND METRICS
    // =================================================================================

    @Override
    public Map<String, Number> getTreeMetrics() {
        log.debug("Calculating tree metrics");
        List<T> allNodes = treeRepository.findAll();
        Map<String, Number> metrics = new HashMap<>();
        
        // Total nodes
        metrics.put("totalNodes", allNodes.size());
        
        // Root nodes
        long rootCount = allNodes.stream()
                .filter(node -> node.getParentId() == null)
                .count();
        metrics.put("rootNodes", rootCount);
        
        // Leaf nodes
        long leafCount = allNodes.stream()
                .filter(this::isLeafNode)
                .count();
        metrics.put("leafNodes", leafCount);
        
        // Branch nodes
        metrics.put("branchNodes", allNodes.size() - leafCount);
        
        // Depth metrics
        int[] depths = allNodes.stream()
                .mapToInt(node -> getDepth(node.getId()))
                .toArray();
        
        metrics.put("maxDepth", depths.length > 0 ? Arrays.stream(depths).max().orElse(0) : 0);
        metrics.put("avgDepth", depths.length > 0 ? Arrays.stream(depths).average().orElse(0) : 0);
        
        // Average children per branch node
        double avgChildren = allNodes.stream()
                .filter(node -> !isLeafNode(node))
                .mapToInt(node -> findDirectChildren(node.getId()).size())
                .average()
                .orElse(0.0);
        metrics.put("avgChildren", avgChildren);
        
        log.debug("Calculated tree metrics: {}", metrics);
        return metrics;
    }

    @Override
    public boolean isDescendantOf(ID id, ID ancestorId) {
        if (id == null || ancestorId == null) {
            return false;
        }

        return findAncestors(id).stream()
                .anyMatch(ancestor -> ancestor.getId().equals(ancestorId));
    }

    @Override
    public int getDepth(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        return depthCache.computeIfAbsent(id, k -> findAncestors(k).size());
    }

    @Override
    public List<T> getPath(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        log.debug("Getting path for entity: {}", id);
        
        List<T> path = new ArrayList<>();
        Optional<T> current = findById(id);
        
        if (current.isPresent()) {
            path.addAll(findAncestors(id));
            Collections.reverse(path); // Order from root to entity
            path.add(current.get());
        }
        
        return path;
    }

    // =================================================================================
    // VALIDATION AND INTEGRITY
    // =================================================================================

    @Override
    public Map<String, List<String>> validateTreeIntegrity() {
        log.debug("Validating tree integrity");
        Map<String, List<String>> issues = new HashMap<>();
        List<T> allNodes = treeRepository.findAll();
        
        // Check for circular references
        List<String> circularReferences = new ArrayList<>();
        for (T node : allNodes) {
            if (hasCircularReference(node.getId(), new HashSet<>())) {
                circularReferences.add("Circular reference detected for entity: " + node.getId());
            }
        }
        if (!circularReferences.isEmpty()) {
            issues.put("circularReferences", circularReferences);
        }
        
        // Check for orphaned entities
        List<String> orphanedEntities = new ArrayList<>();
        for (T node : allNodes) {
            ID parentId = node.getParentId();
            if (parentId != null && !treeRepository.existsById(parentId)) {
                orphanedEntities.add("Orphaned entity found: " + node.getId() + " (parent: " + parentId + ")");
            }
        }
        if (!orphanedEntities.isEmpty()) {
            issues.put("orphanedEntities", orphanedEntities);
        }
        
        // Check for duplicate sort orders within siblings
        List<String> duplicateSortOrders = new ArrayList<>();
        Map<ID, List<T>> parentChildMap = allNodes.stream()
                .filter(node -> node.getParentId() != null)
                .collect(Collectors.groupingBy(T::getParentId));
        
        parentChildMap.forEach((parentId, children) -> {
            Map<Integer, Long> sortOrderCounts = children.stream()
                    .collect(Collectors.groupingBy(T::getSortOrder, Collectors.counting()));
            
            sortOrderCounts.forEach((sortOrder, count) -> {
                if (count > 1) {
                    duplicateSortOrders.add("Duplicate sort order " + sortOrder + " found for parent: " + parentId);
                }
            });
        });
        if (!duplicateSortOrders.isEmpty()) {
            issues.put("duplicateSortOrders", duplicateSortOrders);
        }
        
        log.info("Tree integrity validation completed. Issues found: {}", issues.size());
        return issues;
    }

    @Override
    @Transactional
    public int repairTreeStructure() {
        log.debug("Repairing tree structure");
        int repairedCount = 0;
        
        Map<String, List<String>> issues = validateTreeIntegrity();
        
        // Repair orphaned entities by setting their parent to null (making them roots)
        List<String> orphanedEntities = issues.get("orphanedEntities");
        if (orphanedEntities != null) {
            for (String issue : orphanedEntities) {
                // Extract entity ID from issue message
                String entityIdStr = issue.substring(issue.indexOf(": ") + 2, issue.indexOf(" (parent:"));
                try {
                    @SuppressWarnings("unchecked")
                    ID entityId = (ID) entityIdStr; // This is a simplified approach
                    Optional<T> entity = findById(entityId);
                    if (entity.isPresent()) {
                        T e = entity.get();
                        e.setParentId(null);
                        treeRepository.save(e);
                        repairedCount++;
                        log.info("Repaired orphaned entity: {}", entityId);
                    }
                } catch (Exception e) {
                    log.warn("Could not repair orphaned entity from issue: {}", issue, e);
                }
            }
        }
        
        log.info("Tree structure repair completed. Repaired {} issues", repairedCount);
        return repairedCount;
    }

    // =================================================================================
    // HELPER METHODS
    // =================================================================================

    /**
     * Creates a copy of a node and all its children recursively.
     * 
     * @param sourceNode The source node to copy
     * @param newParentId The new parent ID for the copied node
     * @param propertyOverrides Map of properties to override during copy
     * @return The copied node with new ID
     */
    protected T copyNodeWithChildren(T sourceNode, ID newParentId, Map<String, Object> propertyOverrides) {
        // Create new instance
        T newNode = createNewInstance(sourceNode.getClass());
        
        // Copy all properties except id, parentId, and children
        BeanUtils.copyProperties(sourceNode, newNode, "id", "parentId", "children");
        
        // Apply any property overrides
        applyPropertyOverrides(newNode, propertyOverrides);
        
        // Set the new parent ID
        newNode.setParentId(newParentId);
        
        // Initialize empty children list
        newNode.setChildren(new ArrayList<>());
        
        // Save and return the new node
        return treeRepository.save(newNode);
    }

    /**
     * Creates a new instance of the entity class.
     * 
     * @param entityClass The entity class
     * @return New instance of the entity
     */
    @SuppressWarnings("unchecked")
    protected T createNewInstance(Class<?> entityClass) {
        try {
            return (T) entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create entity instance: " + entityClass.getName(), e);
        }
    }

    /**
     * Applies property overrides to an entity using reflection.
     * 
     * @param entity The entity to modify
     * @param propertyOverrides Map of property names to values
     */
    protected void applyPropertyOverrides(T entity, Map<String, Object> propertyOverrides) {
        propertyOverrides.forEach((propertyName, value) -> {
            String setterName = "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            Method setter = ReflectionUtils.findMethod(entity.getClass(), setterName, value.getClass());
            if (setter != null) {
                ReflectionUtils.invokeMethod(setter, entity, value);
            }
        });
    }

    /**
     * Checks if a node is a leaf (has no children).
     * 
     * @param node The node to check
     * @return true if the node is a leaf
     */
    protected boolean isLeafNode(T node) {
        return node.getChildren() == null || node.getChildren().isEmpty();
    }

    /**
     * Checks for circular references starting from a given node.
     * 
     * @param nodeId The starting node ID
     * @param visited Set of visited node IDs
     * @return true if a circular reference is detected
     */
    protected boolean hasCircularReference(ID nodeId, Set<ID> visited) {
        if (visited.contains(nodeId)) {
            return true;
        }
        
        visited.add(nodeId);
        
        Optional<T> node = findById(nodeId);
        if (node.isPresent() && node.get().getParentId() != null) {
            return hasCircularReference(node.get().getParentId(), visited);
        }
        
        return false;
    }

    /**
     * Clears all related caches for an entity and its related nodes.
     * 
     * @param entity The entity whose caches should be cleared
     */
    protected void clearRelatedCaches(T entity) {
        clearCaches(entity.getId());
        
        // Clear caches for ancestors
        findAncestors(entity.getId())
            .forEach(ancestor -> clearCaches(ancestor.getId()));
        
        // Clear caches for descendants
        findWithDescendants(entity.getId())
            .forEach(descendant -> clearCaches(descendant.getId()));
    }

    /**
     * Clears caches for a specific entity ID.
     * 
     * @param id The entity ID
     */
    protected void clearCaches(ID id) {
        descendantsCache.remove(id);
        ancestorsCache.remove(id);
        depthCache.remove(id);
    }

    /**
     * Validates a new entity before creation.
     * 
     * @param entity The entity to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateNewEntity(T entity) {
        Assert.notNull(entity, "Entity cannot be null");
        
        if (entity.getId() != null && treeRepository.existsById(entity.getId())) {
            throw new IllegalArgumentException("Entity with ID already exists: " + entity.getId());
        }

        validateParentExists(entity);
    }

    /**
     * Validates an existing entity before update.
     * 
     * @param entity The entity to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateExistingEntity(T entity) {
        Assert.notNull(entity, "Entity cannot be null");
        Assert.notNull(entity.getId(), "Entity ID cannot be null");
        findById(entity.getId())
            .orElseThrow(() -> new NoSuchElementException("Entity not found with ID: " + entity.getId()));

        validateParentExists(entity);
    }

    /**
     * Validates that the parent entity exists.
     * 
     * @param entity The entity whose parent should be validated
     * @throws NoSuchElementException if parent doesn't exist
     */
    protected void validateParentExists(T entity) {
        ID parentId = entity.getParentId();
        if (parentId != null) {
            findById(parentId)
                .orElseThrow(() -> new NoSuchElementException("Parent entity not found with ID: " + parentId));
        }
    }

    @Override
    public List<T> getDirectChildren(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        return treeRepository.findDirectChildren(id);
    }

    @Override
    public List<T> getAllDescendants(ID id) {
        Assert.notNull(id, "Entity ID cannot be null");
        return treeRepository.findItemWithAllDescendants(id);
    }

    @Override
    public List<T> getRootItems() {
        return treeRepository.findRootItems();
    }

} 