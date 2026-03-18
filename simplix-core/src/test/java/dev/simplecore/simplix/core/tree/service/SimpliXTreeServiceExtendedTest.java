package dev.simplecore.simplix.core.tree.service;

import dev.simplecore.simplix.core.tree.entity.CodeGroup;
import dev.simplecore.simplix.core.tree.entity.CodeItem;
import dev.simplecore.simplix.core.tree.repository.CodeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXTreeBaseService - Extended Coverage")
class SimpliXTreeServiceExtendedTest {

    @Mock
    private CodeItemRepository treeRepository;

    @InjectMocks
    private SimpliXTreeBaseService<CodeItem, Long> treeService;

    private CodeGroup codeGroup;
    private CodeItem root;
    private CodeItem child1;
    private CodeItem child2;
    private CodeItem grandChild1;

    @BeforeEach
    void setUp() {
        codeGroup = new CodeGroup();
        codeGroup.setId(1L);
        codeGroup.setGroupKey("TEST");
        codeGroup.setGroupName("Test Group");
        codeGroup.setDescription("Test");

        root = createItem(1L, "ROOT", null, 1);
        child1 = createItem(2L, "CHILD1", 1L, 1);
        child2 = createItem(3L, "CHILD2", 1L, 2);
        grandChild1 = createItem(4L, "GRANDCHILD1", 2L, 1);

        root.setChildren(Arrays.asList(child1, child2));
        child1.setChildren(Collections.singletonList(grandChild1));
        child2.setChildren(Collections.emptyList());
        grandChild1.setChildren(Collections.emptyList());
    }

    @Nested
    @DisplayName("findAll with pagination")
    class FindAllPaginated {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepo() {
            Pageable pageable = PageRequest.of(0, 10);
            when(treeRepository.findAll(pageable)).thenReturn(Page.empty());

            Page<CodeItem> result = treeService.findAll(pageable);
            assertThat(result).isEmpty();
            verify(treeRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("deleteById - entity not found")
    class DeleteByIdNotFound {

        @Test
        @DisplayName("should throw when entity does not exist")
        void shouldThrowWhenNotFound() {
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> treeService.deleteById(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Entity not found");
        }
    }

    @Nested
    @DisplayName("findByLookup")
    class FindByLookup {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepo() {
            Map<String, String> params = Map.of("code_key", "TEST");
            when(treeRepository.findByLookup(params)).thenReturn(List.of(root));

            List<CodeItem> result = treeService.findByLookup(params);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should support pagination")
        void shouldSupportPagination() {
            Map<String, String> params = Map.of("code_key", "TEST");
            when(treeRepository.findByLookup(params)).thenReturn(Arrays.asList(root, child1, child2));

            Page<CodeItem> result = treeService.findByLookup(params, PageRequest.of(0, 2));
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findHierarchyByLookup")
    class FindHierarchyByLookup {

        @Test
        @DisplayName("should include matching items and their ancestors")
        void shouldIncludeAncestors() {
            Map<String, String> params = Map.of("code_key", "CHILD1");
            when(treeRepository.findByLookup(params)).thenReturn(List.of(child1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findAllById(any())).thenReturn(Arrays.asList(root, child1));
            when(treeRepository.buildHierarchy(any())).thenReturn(List.of(root));

            List<CodeItem> result = treeService.findHierarchyByLookup(params);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("findByLevel")
    class FindByLevel {

        @Test
        @DisplayName("should return entities at specified depth")
        void shouldReturnAtLevel() {
            when(treeRepository.findAll()).thenReturn(Arrays.asList(root, child1, child2, grandChild1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(child2.getId())).thenReturn(Optional.of(child2));
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));

            List<CodeItem> level0 = treeService.findByLevel(0);
            assertThat(level0).extracting("codeKey").contains("ROOT");
        }
    }

    @Nested
    @DisplayName("findLeafNodes")
    class FindLeafNodes {

        @Test
        @DisplayName("should return nodes without children")
        void shouldReturnLeaves() {
            when(treeRepository.findAll()).thenReturn(Arrays.asList(root, child1, child2, grandChild1));

            List<CodeItem> leaves = treeService.findLeafNodes();
            assertThat(leaves).extracting("codeKey").contains("CHILD2", "GRANDCHILD1");
        }
    }

    @Nested
    @DisplayName("findByPredicate")
    class FindByPredicate {

        @Test
        @DisplayName("should filter by custom predicate")
        void shouldFilterByPredicate() {
            when(treeRepository.findAll()).thenReturn(Arrays.asList(root, child1, child2, grandChild1));

            List<CodeItem> result = treeService.findByPredicate(
                    item -> item.getCodeKey().startsWith("CHILD"));
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Batch operations")
    class BatchOperations {

        @Test
        @DisplayName("createBatch should save all entities")
        void createBatchShouldSaveAll() {
            CodeItem item1 = createItem(null, "B1", null, 1);
            CodeItem item2 = createItem(null, "B2", null, 2);
            List<CodeItem> items = Arrays.asList(item1, item2);

            CodeItem saved1 = createItem(10L, "B1", null, 1);
            CodeItem saved2 = createItem(11L, "B2", null, 2);
            when(treeRepository.saveAllAndFlush(items)).thenReturn(Arrays.asList(saved1, saved2));

            List<CodeItem> result = treeService.createBatch(items);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("updateBatch should save all entities")
        void updateBatchShouldSaveAll() {
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(child2.getId())).thenReturn(Optional.of(child2));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.saveAllAndFlush(any())).thenReturn(Arrays.asList(child1, child2));

            List<CodeItem> result = treeService.updateBatch(Arrays.asList(child1, child2));
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("deleteBatch should delete all entities without children")
        void deleteBatchShouldDeleteAll() {
            when(treeRepository.findById(child2.getId())).thenReturn(Optional.of(child2));
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findDirectChildren(child2.getId())).thenReturn(Collections.emptyList());
            when(treeRepository.findDirectChildren(grandChild1.getId())).thenReturn(Collections.emptyList());
            when(treeRepository.findItemWithAllDescendants(any())).thenReturn(Collections.emptyList());

            treeService.deleteBatch(Arrays.asList(child2.getId(), grandChild1.getId()));
            verify(treeRepository).deleteAllById(any());
        }

        @Test
        @DisplayName("deleteBatch should throw for entities with children")
        void deleteBatchShouldThrowForEntitiesWithChildren() {
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findDirectChildren(root.getId())).thenReturn(Arrays.asList(child1, child2));

            assertThatThrownBy(() -> treeService.deleteBatch(List.of(root.getId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("child(ren)");
        }

        @Test
        @DisplayName("deleteBatch should throw for non-existent entities")
        void deleteBatchShouldThrowForNonExistent() {
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> treeService.deleteBatch(List.of(999L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Entity not found");
        }
    }

    @Nested
    @DisplayName("getTreeMetrics")
    class GetTreeMetrics {

        @Test
        @DisplayName("should calculate comprehensive metrics")
        void shouldCalculateMetrics() {
            when(treeRepository.findAll()).thenReturn(Arrays.asList(root, child1, child2, grandChild1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(child2.getId())).thenReturn(Optional.of(child2));
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findDirectChildren(root.getId())).thenReturn(Arrays.asList(child1, child2));
            when(treeRepository.findDirectChildren(child1.getId())).thenReturn(List.of(grandChild1));

            Map<String, Number> metrics = treeService.getTreeMetrics();

            assertThat(metrics.get("totalNodes")).isEqualTo(4);
            assertThat(metrics.get("rootNodes").longValue()).isEqualTo(1L);
            assertThat(metrics.get("leafNodes").longValue()).isEqualTo(2L);
            assertThat(metrics.get("branchNodes").longValue()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should handle empty tree")
        void shouldHandleEmptyTree() {
            when(treeRepository.findAll()).thenReturn(Collections.emptyList());

            Map<String, Number> metrics = treeService.getTreeMetrics();
            assertThat(metrics.get("totalNodes")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("validateTreeIntegrity")
    class ValidateTreeIntegrity {

        @Test
        @DisplayName("should detect orphaned entities")
        void shouldDetectOrphans() {
            CodeItem orphan = createItem(10L, "ORPHAN", 999L, 1);
            orphan.setChildren(Collections.emptyList());

            when(treeRepository.findAll()).thenReturn(Arrays.asList(root, child1, orphan));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(orphan.getId())).thenReturn(Optional.of(orphan));
            when(treeRepository.existsById(1L)).thenReturn(true);
            when(treeRepository.existsById(999L)).thenReturn(false);

            Map<String, List<String>> issues = treeService.validateTreeIntegrity();
            assertThat(issues).containsKey("orphanedEntities");
        }

        @Test
        @DisplayName("should report no issues for valid tree")
        void shouldReportNoIssues() {
            when(treeRepository.findAll()).thenReturn(Arrays.asList(root, child1, child2));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(child2.getId())).thenReturn(Optional.of(child2));
            when(treeRepository.existsById(1L)).thenReturn(true);

            Map<String, List<String>> issues = treeService.validateTreeIntegrity();
            assertThat(issues).doesNotContainKey("orphanedEntities");
        }
    }

    @Nested
    @DisplayName("repairTreeStructure")
    class RepairTreeStructure {

        @Test
        @DisplayName("should return zero when no issues found")
        void shouldReturnZeroWhenNoIssues() {
            when(treeRepository.findAll()).thenReturn(List.of(root));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            int repaired = treeService.repairTreeStructure();
            assertThat(repaired).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("move - circular reference prevention")
    class MoveCircularRef {

        @Test
        @DisplayName("should throw when moving to own descendant")
        void shouldThrowForCircularMove() {
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));

            assertThatThrownBy(() -> treeService.move(root.getId(), grandChild1.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot move a node to its own descendant");
        }
    }

    @Nested
    @DisplayName("copySubtreeWithProperties")
    class CopySubtreeWithProperties {

        @Test
        @DisplayName("should copy subtree with property overrides")
        void shouldCopyWithOverrides() {
            CodeItem copiedRoot = createItem(10L, "ROOT_COPY", null, 1);

            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.save(any())).thenReturn(copiedRoot);

            Map<String, Object> overrides = new HashMap<>();
            overrides.put("codeValue", "Overridden Value");

            CodeItem result = treeService.copySubtreeWithProperties(root.getId(), null, overrides);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw for non-existent source")
        void shouldThrowForNonExistentSource() {
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> treeService.copySubtreeWithProperties(999L, null, Map.of()))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("should throw for non-existent target parent")
        void shouldThrowForNonExistentTargetParent() {
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> treeService.copySubtreeWithProperties(root.getId(), 999L, Map.of()))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Target parent entity not found");
        }
    }

    @Nested
    @DisplayName("isDescendantOf")
    class IsDescendantOf {

        @Test
        @DisplayName("should return false for null arguments")
        void shouldReturnFalseForNull() {
            assertThat(treeService.isDescendantOf(null, 1L)).isFalse();
            assertThat(treeService.isDescendantOf(1L, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("validateNewEntity")
    class ValidateNewEntity {

        @Test
        @DisplayName("should throw if entity already exists by ID")
        void shouldThrowIfEntityExists() {
            CodeItem item = createItem(1L, "EXISTS", null, 1);
            when(treeRepository.existsById(1L)).thenReturn(true);

            assertThatThrownBy(() -> treeService.create(item))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Entity with ID already exists");
        }
    }

    private CodeItem createItem(Long id, String key, Long parentId, int sortOrder) {
        CodeItem item = new CodeItem();
        item.setId(id);
        item.setCodeGroup(codeGroup);
        item.setCodeKey(key);
        item.setCodeValue(key + " value");
        item.setDescription(key + " description");
        item.setSortOrder(sortOrder);
        item.setIsActive(true);
        item.setParentId(parentId);
        return item;
    }
}
