package dev.simplecore.simplix.core.tree.service;

import dev.simplecore.simplix.core.tree.entity.CodeGroup;
import dev.simplecore.simplix.core.tree.entity.CodeItem;
import dev.simplecore.simplix.core.tree.repository.CodeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXTreeBaseService - Utility Methods")
class SimpliXTreeServiceUtilityTest {

    @Mock
    private CodeItemRepository repo;

    // Testable subclass that exposes protected methods
    private static class TestableTreeService extends SimpliXTreeBaseService<CodeItem, Long> {
        public TestableTreeService(CodeItemRepository repo) {
            super(repo);
        }

        // Expose protected methods for testing
        public void callValidateNoCircularReference(Long nodeId, Long newParentId) {
            validateNoCircularReference(nodeId, newParentId);
        }

        public <X extends RuntimeException> void callValidateNoCircularReferenceWithSupplier(
                Long nodeId, Long newParentId, java.util.function.Supplier<X> supplier) throws X {
            validateNoCircularReference(nodeId, newParentId, supplier);
        }

        public void callValidateNoChildren(Long id) {
            validateNoChildren(id);
        }

        public <X extends RuntimeException> void callValidateNoChildrenWithSupplier(
                Long id, java.util.function.Supplier<X> supplier) throws X {
            validateNoChildren(id, supplier);
        }

        public List<CodeItem> callBuildTreeFromFlatList(List<CodeItem> nodes, Long rootParentId) {
            return buildTreeFromFlatList(nodes, rootParentId);
        }

        public <D> List<D> callMapToTreeDto(
                List<CodeItem> entities,
                java.util.function.Function<CodeItem, D> mapper,
                java.util.function.Function<CodeItem, List<CodeItem>> childrenGetter,
                java.util.function.BiConsumer<D, List<D>> childrenSetter) {
            return mapToTreeDto(entities, mapper, childrenGetter, childrenSetter);
        }

        public <D> List<D> callMapToTreeDtoWithChildInfo(
                List<CodeItem> entities,
                java.util.function.Function<CodeItem, D> mapper,
                java.util.function.Function<CodeItem, List<CodeItem>> childrenGetter,
                ChildrenWithCountSetter<D> childrenWithCountSetter) {
            return mapToTreeDtoWithChildInfo(entities, mapper, childrenGetter, childrenWithCountSetter);
        }

        public Map<Long, Long> callGetChildCountMap() {
            return getChildCountMap();
        }

        public void callClearRelatedCaches(CodeItem entity) {
            clearRelatedCaches(entity);
        }

        public Long callNormalizeParentId(Long parentId) {
            return normalizeParentId(parentId);
        }
    }

    // Simple DTO for mapping tests
    static class SimpleDto {
        String name;
        List<SimpleDto> children = new ArrayList<>();
        int childCount;

        SimpleDto(String name) { this.name = name; }
    }

    private TestableTreeService service;
    private CodeGroup group;

    @BeforeEach
    void setUp() {
        service = new TestableTreeService(repo);
        group = new CodeGroup();
        group.setId(1L);
        group.setGroupKey("G");
        group.setGroupName("Group");
        group.setDescription("D");
    }

    @Nested
    @DisplayName("validateNoCircularReference")
    class ValidateNoCircularRef {

        @Test
        @DisplayName("should do nothing when newParentId is null")
        void shouldDoNothingForNullParent() {
            service.callValidateNoCircularReference(1L, null);
            // No exception thrown
        }

        @Test
        @DisplayName("should do nothing when nodeId is null")
        void shouldDoNothingForNullNode() {
            service.callValidateNoCircularReference(null, 2L);
            // No exception thrown
        }

        @Test
        @DisplayName("should throw when circular reference detected")
        void shouldThrowForCircular() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            when(repo.findById(2L)).thenReturn(Optional.of(child));
            when(repo.findById(1L)).thenReturn(Optional.of(root));

            assertThatThrownBy(() -> service.callValidateNoCircularReference(1L, 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot move node");
        }
    }

    @Nested
    @DisplayName("validateNoCircularReference with supplier")
    class ValidateNoCircularRefSupplier {

        @Test
        @DisplayName("should throw custom exception when circular reference detected")
        void shouldThrowCustomException() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            when(repo.findById(2L)).thenReturn(Optional.of(child));
            when(repo.findById(1L)).thenReturn(Optional.of(root));

            assertThatThrownBy(() ->
                    service.callValidateNoCircularReferenceWithSupplier(1L, 2L,
                            () -> new UnsupportedOperationException("Custom error")))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Custom error");
        }

        @Test
        @DisplayName("should do nothing when no circular reference")
        void shouldDoNothingWhenValid() {
            when(repo.findById(2L)).thenReturn(Optional.empty());

            service.callValidateNoCircularReferenceWithSupplier(1L, 2L,
                    () -> new RuntimeException("should not throw"));
            // No exception thrown
        }
    }

    @Nested
    @DisplayName("validateNoChildren")
    class ValidateNoChildren {

        @Test
        @DisplayName("should throw when node has children")
        void shouldThrowWhenHasChildren() {
            CodeItem child = item(2L, "CHILD", 1L, 0);
            child.setChildren(Collections.emptyList());
            when(repo.findDirectChildren(1L)).thenReturn(List.of(child));

            assertThatThrownBy(() -> service.callValidateNoChildren(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot delete node");
        }

        @Test
        @DisplayName("should do nothing when node has no children")
        void shouldDoNothingWhenNoChildren() {
            when(repo.findDirectChildren(1L)).thenReturn(Collections.emptyList());
            service.callValidateNoChildren(1L);
            // No exception
        }
    }

    @Nested
    @DisplayName("validateNoChildren with supplier")
    class ValidateNoChildrenSupplier {

        @Test
        @DisplayName("should throw custom exception when node has children")
        void shouldThrowCustom() {
            CodeItem child = item(2L, "C", 1L, 0);
            child.setChildren(Collections.emptyList());
            when(repo.findDirectChildren(1L)).thenReturn(List.of(child));

            assertThatThrownBy(() ->
                    service.callValidateNoChildrenWithSupplier(1L,
                            () -> new UnsupportedOperationException("has children")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("buildTreeFromFlatList")
    class BuildTreeFromFlatList {

        @Test
        @DisplayName("should return empty for null input")
        void shouldReturnEmptyForNull() {
            assertThat(service.callBuildTreeFromFlatList(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(service.callBuildTreeFromFlatList(Collections.emptyList(), null)).isEmpty();
        }

        @Test
        @DisplayName("should build tree from flat list with null rootParentId")
        void shouldBuildTree() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            CodeItem gc = item(3L, "GC", 2L, 0);

            List<CodeItem> result = service.callBuildTreeFromFlatList(
                    Arrays.asList(root, child, gc), null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChildren()).hasSize(1);
            assertThat(result.get(0).getChildren().get(0).getChildren()).hasSize(1);
        }

        @Test
        @DisplayName("should build subtree with non-null rootParentId")
        void shouldBuildSubtree() {
            CodeItem child1 = item(2L, "C1", 1L, 0);
            CodeItem child2 = item(3L, "C2", 1L, 1);

            List<CodeItem> result = service.callBuildTreeFromFlatList(
                    Arrays.asList(child1, child2), 1L);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("mapToTreeDto")
    class MapToTreeDto {

        @Test
        @DisplayName("should return empty for null input")
        void shouldReturnEmptyForNull() {
            List<SimpleDto> result = service.callMapToTreeDto(
                    null,
                    e -> new SimpleDto(e.getCodeKey()),
                    CodeItem::getChildren,
                    (dto, children) -> dto.children = children);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should map entities to DTOs with children")
        void shouldMapWithChildren() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            List<SimpleDto> result = service.callMapToTreeDto(
                    List.of(root),
                    e -> new SimpleDto(e.getCodeKey()),
                    CodeItem::getChildren,
                    (dto, children) -> dto.children = children);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name).isEqualTo("ROOT");
            assertThat(result.get(0).children).hasSize(1);
            assertThat(result.get(0).children.get(0).name).isEqualTo("CHILD");
        }

        @Test
        @DisplayName("should set empty children for leaf nodes")
        void shouldSetEmptyForLeaves() {
            CodeItem leaf = item(1L, "LEAF", null, 0);
            leaf.setChildren(Collections.emptyList());

            List<SimpleDto> result = service.callMapToTreeDto(
                    List.of(leaf),
                    e -> new SimpleDto(e.getCodeKey()),
                    CodeItem::getChildren,
                    (dto, children) -> dto.children = children);

            assertThat(result.get(0).children).isEmpty();
        }
    }

    @Nested
    @DisplayName("mapToTreeDtoWithChildInfo")
    class MapToTreeDtoWithChildInfo {

        @Test
        @DisplayName("should return empty for null input")
        void shouldReturnEmptyForNull() {
            List<SimpleDto> result = service.callMapToTreeDtoWithChildInfo(
                    null,
                    e -> new SimpleDto(e.getCodeKey()),
                    CodeItem::getChildren,
                    (dto, children, count) -> { dto.children = children; dto.childCount = count; });

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should map entities with child count")
        void shouldMapWithChildCount() {
            CodeItem root = item(1L, "ROOT", null, 0);
            CodeItem child = item(2L, "CHILD", 1L, 0);
            root.setChildren(List.of(child));
            child.setChildren(Collections.emptyList());

            List<SimpleDto> result = service.callMapToTreeDtoWithChildInfo(
                    List.of(root),
                    e -> new SimpleDto(e.getCodeKey()),
                    CodeItem::getChildren,
                    (dto, children, count) -> { dto.children = children; dto.childCount = count; });

            assertThat(result).hasSize(1);
            assertThat(result.get(0).childCount).isEqualTo(1);
            assertThat(result.get(0).children.get(0).childCount).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getChildCountMap")
    class GetChildCountMap {

        @Test
        @DisplayName("should return map from countChildrenByParentId results")
        void shouldReturnMap() {
            when(repo.countChildrenByParentId()).thenReturn(
                    List.of(new Object[]{1L, 3L}, new Object[]{2L, 1L}));

            Map<Long, Long> result = service.callGetChildCountMap();

            assertThat(result).containsEntry(1L, 3L);
            assertThat(result).containsEntry(2L, 1L);
        }

        @Test
        @DisplayName("should skip null parent IDs")
        void shouldSkipNullParentIds() {
            when(repo.countChildrenByParentId()).thenReturn(
                    List.of(new Object[]{null, 5L}, new Object[]{1L, 2L}));

            Map<Long, Long> result = service.callGetChildCountMap();

            assertThat(result).hasSize(1);
            assertThat(result).containsEntry(1L, 2L);
        }
    }

    @Nested
    @DisplayName("clearRelatedCaches")
    class ClearRelatedCaches {

        @Test
        @DisplayName("should clear caches for entity, ancestors and descendants")
        void shouldClearAllCaches() {
            CodeItem entity = item(1L, "E", null, 0);
            entity.setChildren(Collections.emptyList());

            when(repo.findById(1L)).thenReturn(Optional.of(entity));
            when(repo.findItemWithAllDescendants(1L)).thenReturn(List.of(entity));

            service.callClearRelatedCaches(entity);
            // No exception
        }
    }

    @Nested
    @DisplayName("normalizeParentId")
    class NormalizeParentId {

        @Test
        @DisplayName("should return null for null")
        void shouldReturnNullForNull() {
            assertThat(service.callNormalizeParentId(null)).isNull();
        }

        @Test
        @DisplayName("should return non-null value as-is")
        void shouldReturnNonNullAsIs() {
            assertThat(service.callNormalizeParentId(5L)).isEqualTo(5L);
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
