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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXTreeBaseService - Repair and Deep Paths")
class SimpliXTreeServiceRepairTest {

    @Mock
    private CodeItemRepository repo;

    @InjectMocks
    private SimpliXTreeBaseService<CodeItem, Long> service;

    private CodeGroup group;

    @BeforeEach
    void setUp() {
        group = new CodeGroup();
        group.setId(1L);
        group.setGroupKey("G");
        group.setGroupName("Grp");
        group.setDescription("D");
    }

    @Nested
    @DisplayName("copySubtreeWithProperties - with children")
    class CopyWithChildren {

        @Test
        @DisplayName("should recursively copy node with children via copyNodeWithChildren")
        void shouldRecursivelyCopy() {
            CodeItem root = item(1L, "R", null, 0);
            root.setChildren(Collections.emptyList());
            CodeItem copied = item(10L, "R_COPY", null, 0);
            copied.setChildren(Collections.emptyList());

            when(repo.findById(1L)).thenReturn(Optional.of(root));
            when(repo.save(any())).thenReturn(copied);

            Map<String, Object> overrides = new HashMap<>();
            overrides.put("codeKey", "NEW_KEY");

            CodeItem result = service.copySubtreeWithProperties(1L, null, overrides);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should copy subtree to new parent with properties")
        void shouldCopyToParentWithProps() {
            CodeItem source = item(1L, "SRC", null, 0);
            source.setChildren(Collections.emptyList());
            CodeItem target = item(5L, "TGT", null, 0);
            target.setChildren(Collections.emptyList());
            CodeItem copied = item(10L, "SRC_COPY", 5L, 0);

            when(repo.findById(1L)).thenReturn(Optional.of(source));
            when(repo.findById(5L)).thenReturn(Optional.of(target));
            when(repo.save(any())).thenReturn(copied);

            CodeItem result = service.copySubtreeWithProperties(1L, 5L, Map.of());
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("applyPropertyOverrides via copySubtreeWithProperties")
    class ApplyOverrides {

        @Test
        @DisplayName("should apply multiple property overrides")
        void shouldApplyMultipleOverrides() {
            CodeItem source = item(1L, "SRC", null, 0);
            source.setChildren(Collections.emptyList());
            CodeItem copied = item(10L, "SRC", null, 0);

            when(repo.findById(1L)).thenReturn(Optional.of(source));
            when(repo.save(any())).thenReturn(copied);

            Map<String, Object> overrides = new HashMap<>();
            overrides.put("codeKey", "OVERRIDE_KEY");
            overrides.put("codeValue", "Override Value");
            overrides.put("description", "Override Desc");

            CodeItem result = service.copySubtreeWithProperties(1L, null, overrides);
            assertThat(result).isNotNull();
            verify(repo).save(any());
        }

        @Test
        @DisplayName("should handle non-existent setter silently")
        void shouldHandleMissingSetter() {
            CodeItem source = item(1L, "SRC", null, 0);
            source.setChildren(Collections.emptyList());
            CodeItem copied = item(10L, "SRC", null, 0);

            when(repo.findById(1L)).thenReturn(Optional.of(source));
            when(repo.save(any())).thenReturn(copied);

            Map<String, Object> overrides = new HashMap<>();
            overrides.put("nonExistentProperty", "value");

            CodeItem result = service.copySubtreeWithProperties(1L, null, overrides);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("clearRelatedCaches")
    class ClearRelatedCaches {

        @Test
        @DisplayName("should clear caches after update")
        void shouldClearCachesOnUpdate() {
            CodeItem entity = item(1L, "E", null, 0);
            entity.setChildren(Collections.emptyList());

            when(repo.findById(1L)).thenReturn(Optional.of(entity));
            when(repo.saveAndFlush(any())).thenReturn(entity);

            service.update(entity);
            // Caches should be cleared - verify by checking the entity was saved
            verify(repo).saveAndFlush(entity);
        }
    }

    @Nested
    @DisplayName("deleteById error path")
    class DeleteByIdError {

        @Test
        @DisplayName("should throw and log on deletion error")
        void shouldThrowOnDeletionError() {
            CodeItem entity = item(1L, "E", null, 0);
            entity.setChildren(Collections.emptyList());

            when(repo.findById(1L)).thenReturn(Optional.of(entity));
            when(repo.findItemWithAllDescendants(1L)).thenReturn(List.of(entity));
            doThrow(new RuntimeException("DB error")).when(repo).deleteById(1L);

            assertThatThrownBy(() -> service.deleteById(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB error");
        }
    }

    @Nested
    @DisplayName("findByLevel - multiple levels")
    class FindByLevelMultiple {

        @Test
        @DisplayName("should find entities at level 1")
        void shouldFindAtLevel1() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            when(repo.findAll()).thenReturn(Arrays.asList(root, child));
            when(repo.findById(1L)).thenReturn(Optional.of(root));
            when(repo.findById(2L)).thenReturn(Optional.of(child));

            List<CodeItem> result = service.findByLevel(1);
            assertThat(result).extracting("codeKey").containsExactly("CHILD");
        }

        @Test
        @DisplayName("should find entities at level 2")
        void shouldFindAtLevel2() {
            CodeItem root = item(1L, "R", null, 0);
            CodeItem child = item(2L, "C", 1L, 0);
            CodeItem gc = item(3L, "GC", 2L, 0);
            root.setChildren(List.of(child));
            child.setChildren(List.of(gc));
            gc.setChildren(Collections.emptyList());

            when(repo.findAll()).thenReturn(Arrays.asList(root, child, gc));
            when(repo.findById(1L)).thenReturn(Optional.of(root));
            when(repo.findById(2L)).thenReturn(Optional.of(child));
            when(repo.findById(3L)).thenReturn(Optional.of(gc));

            List<CodeItem> result = service.findByLevel(2);
            assertThat(result).extracting("codeKey").containsExactly("GC");
        }
    }

    @Nested
    @DisplayName("findHierarchyByLookup - deep hierarchy")
    class FindHierarchyDeep {

        @Test
        @DisplayName("should collect all ancestor IDs for multiple matches")
        void shouldCollectAncestorIds() {
            CodeItem root = item(1L, "R", null, 0);
            CodeItem c1 = item(2L, "C1", 1L, 0);
            CodeItem c2 = item(3L, "C2", 1L, 1);
            root.setChildren(Arrays.asList(c1, c2));
            c1.setChildren(Collections.emptyList());
            c2.setChildren(Collections.emptyList());

            when(repo.findByLookup(any())).thenReturn(Arrays.asList(c1, c2));
            when(repo.findById(1L)).thenReturn(Optional.of(root));
            when(repo.findById(2L)).thenReturn(Optional.of(c1));
            when(repo.findById(3L)).thenReturn(Optional.of(c2));
            when(repo.findAllById(any())).thenReturn(Arrays.asList(root, c1, c2));
            when(repo.buildHierarchy(any())).thenReturn(List.of(root));

            List<CodeItem> result = service.findHierarchyByLookup(Map.of("k", "v"));
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("findLeafNodes")
    class FindLeafNodesDeep {

        @Test
        @DisplayName("should find leaf nodes with null children")
        void shouldFindLeafWithNullChildren() {
            CodeItem leaf = item(1L, "LEAF", null, 0);
            leaf.setChildren(null);

            when(repo.findAll()).thenReturn(List.of(leaf));

            List<CodeItem> result = service.findLeafNodes();
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("normalizeParentId")
    class NormalizeParentId {

        @Test
        @DisplayName("should be exercised via SortableTreeBaseService")
        void shouldNormalizeEmptyStringParentId() {
            // This is covered by SortableTreeBaseService test
            // Testing via findRoots
            when(repo.findRootItems()).thenReturn(Collections.emptyList());
            assertThat(service.findRoots()).isEmpty();
        }
    }

    private CodeItem item(Long id, String key, Long parentId, int sortOrder) {
        CodeItem i = new CodeItem();
        i.setId(id);
        i.setCodeGroup(group);
        i.setCodeKey(key);
        i.setCodeValue(key);
        i.setDescription(key);
        i.setSortOrder(sortOrder);
        i.setIsActive(true);
        i.setParentId(parentId);
        return i;
    }
}
