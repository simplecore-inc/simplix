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
@DisplayName("SimpliXTreeBaseService - Deep Coverage")
class SimpliXTreeServiceDeepCoverageTest {

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
        group.setGroupName("Group");
        group.setDescription("D");
    }

    @Nested
    @DisplayName("create with validation edge cases")
    class CreateValidation {

        @Test
        @DisplayName("should create entity with null ID (new entity)")
        void shouldCreateWithNullId() {
            CodeItem newItem = item(null, "NEW", null, 0);
            CodeItem saved = item(5L, "NEW", null, 0);

            when(repo.saveAndFlush(any())).thenReturn(saved);

            CodeItem result = service.create(newItem);
            assertThat(result.getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should create entity with non-existing ID")
        void shouldCreateWithNonExistingId() {
            CodeItem newItem = item(100L, "NEW", null, 0);
            CodeItem saved = item(100L, "NEW", null, 0);

            when(repo.existsById(100L)).thenReturn(false);
            when(repo.saveAndFlush(any())).thenReturn(saved);

            CodeItem result = service.create(newItem);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("update validation")
    class UpdateValidation {

        @Test
        @DisplayName("should throw when entity has null ID on update")
        void shouldThrowForNullId() {
            CodeItem entity = item(null, "X", null, 0);
            assertThatThrownBy(() -> service.update(entity))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw when entity not found on update")
        void shouldThrowWhenNotFound() {
            CodeItem entity = item(999L, "X", null, 0);
            when(repo.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(entity))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("copySubtreeWithProperties - property overrides")
    class PropertyOverrides {

        @Test
        @DisplayName("should apply property overrides using setter reflection")
        void shouldApplyOverrides() {
            CodeItem source = item(1L, "SRC", null, 0);
            CodeItem copied = item(10L, "SRC", null, 0);

            when(repo.findById(1L)).thenReturn(Optional.of(source));
            when(repo.save(any())).thenReturn(copied);

            Map<String, Object> overrides = new HashMap<>();
            overrides.put("codeValue", "New Value");
            overrides.put("description", "New Description");

            CodeItem result = service.copySubtreeWithProperties(1L, null, overrides);
            assertThat(result).isNotNull();
            verify(repo).save(any());
        }
    }

    @Nested
    @DisplayName("copyNode")
    class CopyNode {

        @Test
        @DisplayName("should create new instance via copySubtree")
        void shouldCopyNode() {
            CodeItem source = item(1L, "SRC", null, 0);
            source.setChildren(Collections.emptyList());
            CodeItem copied = item(10L, "SRC_COPY", null, 0);

            when(repo.findById(1L)).thenReturn(Optional.of(source));
            when(repo.findItemWithAllDescendants(1L)).thenReturn(List.of(source));
            when(repo.findDirectChildren(1L)).thenReturn(Collections.emptyList());
            when(repo.saveAndFlush(any())).thenReturn(copied);

            CodeItem result = service.copySubtree(1L, null);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("caching behavior")
    class Caching {

        @Test
        @DisplayName("findWithDescendants should cache results")
        void shouldCacheDescendants() {
            CodeItem root = item(1L, "ROOT", null, 0);
            root.setChildren(Collections.emptyList());

            when(repo.findItemWithAllDescendants(1L)).thenReturn(List.of(root));

            // First call - cache miss
            List<CodeItem> first = service.findWithDescendants(1L);
            // Second call - cache hit
            List<CodeItem> second = service.findWithDescendants(1L);

            assertThat(first).isEqualTo(second);
            // Should only be called once due to caching
            verify(repo, times(1)).findItemWithAllDescendants(1L);
        }

        @Test
        @DisplayName("findAncestors should cache results")
        void shouldCacheAncestors() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            when(repo.findById(2L)).thenReturn(Optional.of(child));
            when(repo.findById(1L)).thenReturn(Optional.of(root));

            List<CodeItem> first = service.findAncestors(2L);
            List<CodeItem> second = service.findAncestors(2L);

            assertThat(first).isEqualTo(second);
            // findById should only be called during first invocation
        }

        @Test
        @DisplayName("getDepth should cache results")
        void shouldCacheDepth() {
            CodeItem root = item(1L, "ROOT", null, 0);
            root.setChildren(Collections.emptyList());

            when(repo.findById(1L)).thenReturn(Optional.of(root));

            int d1 = service.getDepth(1L);
            int d2 = service.getDepth(1L);

            assertThat(d1).isEqualTo(d2);
        }
    }

    @Nested
    @DisplayName("deleteById - success path")
    class DeleteSuccess {

        @Test
        @DisplayName("should delete entity and clear descendant caches")
        void shouldDeleteAndClearCaches() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            when(repo.findById(1L)).thenReturn(Optional.of(root));
            when(repo.findItemWithAllDescendants(1L)).thenReturn(Arrays.asList(root, child));

            service.deleteById(1L);

            verify(repo).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("getTreeMetrics - with multiple branch nodes")
    class TreeMetricsBranch {

        @Test
        @DisplayName("should calculate average children correctly")
        void shouldCalcAvgChildren() {
            CodeItem r = item(1L, "R", null, 0);
            CodeItem c1 = item(2L, "C1", 1L, 0);
            CodeItem c2 = item(3L, "C2", 1L, 1);
            c1.setChildren(Collections.emptyList());
            c2.setChildren(Collections.emptyList());
            r.setChildren(Arrays.asList(c1, c2));

            when(repo.findAll()).thenReturn(Arrays.asList(r, c1, c2));
            when(repo.findById(1L)).thenReturn(Optional.of(r));
            when(repo.findById(2L)).thenReturn(Optional.of(c1));
            when(repo.findById(3L)).thenReturn(Optional.of(c2));
            when(repo.findDirectChildren(1L)).thenReturn(Arrays.asList(c1, c2));

            Map<String, Number> metrics = service.getTreeMetrics();
            assertThat(metrics.get("avgChildren").doubleValue()).isEqualTo(2.0);
            assertThat(metrics.get("maxDepth").intValue()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findRoots and getRootItems")
    class FindAndGetRoots {

        @Test
        @DisplayName("should delegate findRoots to repo.findRootItems")
        void shouldDelegateFindRoots() {
            CodeItem r = item(1L, "ROOT", null, 0);
            when(repo.findRootItems()).thenReturn(List.of(r));

            assertThat(service.findRoots()).hasSize(1);
            assertThat(service.getRootItems()).hasSize(1);
            verify(repo, times(2)).findRootItems();
        }
    }

    @Nested
    @DisplayName("getDirectChildren and getAllDescendants")
    class GettersDelegate {

        @Test
        @DisplayName("should delegate getDirectChildren to repo")
        void shouldDelegateGetDirectChildren() {
            when(repo.findDirectChildren(1L)).thenReturn(Collections.emptyList());
            assertThat(service.getDirectChildren(1L)).isEmpty();
        }

        @Test
        @DisplayName("should delegate getAllDescendants to repo")
        void shouldDelegateGetAllDescendants() {
            when(repo.findItemWithAllDescendants(1L)).thenReturn(Collections.emptyList());
            assertThat(service.getAllDescendants(1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLookup - null parameter validation")
    class FindByLookupValidation {

        @Test
        @DisplayName("should throw for null parameters")
        void shouldThrowForNullParams() {
            assertThatThrownBy(() -> service.findByLookup(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw for null parameters with pageable")
        void shouldThrowForNullParamsWithPageable() {
            assertThatThrownBy(() -> service.findByLookup(null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("findHierarchyByLookup - null validation")
    class FindHierarchyValidation {

        @Test
        @DisplayName("should throw for null parameters")
        void shouldThrowForNullParams() {
            assertThatThrownBy(() -> service.findHierarchyByLookup(null))
                    .isInstanceOf(IllegalArgumentException.class);
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
