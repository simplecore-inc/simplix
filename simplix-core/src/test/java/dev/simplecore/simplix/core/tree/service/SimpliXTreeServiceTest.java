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
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("Tree Service Test")
class SimpliXTreeServiceTest {

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
        // Create code group
        codeGroup = new CodeGroup();
        codeGroup.setId(1L);
        codeGroup.setGroupKey("TEST_GROUP");
        codeGroup.setGroupName("Test Group");
        codeGroup.setDescription("Test code group for tree structure");

        // Create test data
        root = createCodeItem(1L, "ROOT", "Root Node", null, 1);
        child1 = createCodeItem(2L, "CHILD1", "First Child", root.getId(), 1);
        child2 = createCodeItem(3L, "CHILD2", "Second Child", root.getId(), 2);
        grandChild1 = createCodeItem(4L, "GRANDCHILD1", "First Grandchild", child1.getId(), 1);

        // Set up hierarchy
        root.setChildren(Arrays.asList(child1, child2));
        child1.setChildren(Collections.singletonList(grandChild1));
        child2.setChildren(Collections.emptyList());
        grandChild1.setChildren(Collections.emptyList());
    }

    @Nested
    @DisplayName("Tree Navigation Tests")
    class TreeNavigationTests {

        @Test
        @DisplayName("Get Direct Children - Get Immediate Child Nodes")
        void getDirectChildren() {
            // given
            when(treeRepository.findDirectChildren(root.getId())).thenReturn(Arrays.asList(child1, child2));

            // when
            List<CodeItem> children = treeService.getDirectChildren(root.getId());

            // then
            assertThat(children).hasSize(2)
                .extracting("codeKey")
                .containsExactly("CHILD1", "CHILD2");
            verify(treeRepository).findDirectChildren(root.getId());
        }

        @Test
        @DisplayName("Get All Descendants - Get All Child Nodes")
        void getAllDescendants() {
            // given
            when(treeRepository.findItemWithAllDescendants(root.getId()))
                .thenReturn(Arrays.asList(root, child1, child2, grandChild1));

            // when
            List<CodeItem> descendants = treeService.getAllDescendants(root.getId());

            // then
            assertThat(descendants).hasSize(4)
                .extracting("codeKey")
                .containsExactly("ROOT", "CHILD1", "CHILD2", "GRANDCHILD1");
            verify(treeRepository).findItemWithAllDescendants(root.getId());
        }

        @Test
        @DisplayName("Find Complete Hierarchy - Retrieve Full Tree Structure")
        void findCompleteHierarchy() {
            // given
            when(treeRepository.findCompleteHierarchy()).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> hierarchy = treeService.findCompleteHierarchy();

            // then
            assertThat(hierarchy).hasSize(1);
            assertThat(hierarchy.get(0).getChildren()).hasSize(2);
            assertThat(hierarchy.get(0).getChildren().get(0).getChildren()).hasSize(1);
            verify(treeRepository).findCompleteHierarchy();
        }

        @Test
        @DisplayName("Get Root Items - Get Top Level Nodes")
        void getRootItems() {
            // given
            when(treeRepository.findRootItems()).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> rootItems = treeService.getRootItems();

            // then
            assertThat(rootItems).hasSize(1)
                .extracting("codeKey")
                .containsExactly("ROOT");
            verify(treeRepository).findRootItems();
        }

        @Test
        @DisplayName("Find With Descendants - Retrieve Node with All Children")
        void findWithDescendants() {
            // given
            when(treeRepository.findItemWithAllDescendants(root.getId()))
                .thenReturn(Arrays.asList(root, child1, child2, grandChild1));

            // when
            List<CodeItem> descendants = treeService.findWithDescendants(root.getId());

            // then
            assertThat(descendants).hasSize(4)
                .extracting("codeKey")
                .containsExactly("ROOT", "CHILD1", "CHILD2", "GRANDCHILD1");
            verify(treeRepository).findItemWithAllDescendants(root.getId());
        }

        @Test
        @DisplayName("Find Ancestors - Retrieve All Parent Nodes")
        void findAncestors() {
            // given
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            List<CodeItem> ancestors = treeService.findAncestors(grandChild1.getId());

            // then
            assertThat(ancestors).hasSize(2)
                .extracting("codeKey")
                .containsExactly("CHILD1", "ROOT");
        }

        @Test
        @DisplayName("Find Siblings - Retrieve Nodes with Same Parent")
        void findSiblings() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findDirectChildren(root.getId())).thenReturn(Arrays.asList(child1, child2));

            // when
            List<CodeItem> siblings = treeService.findSiblings(child1.getId());

            // then
            assertThat(siblings).hasSize(1)
                .extracting("codeKey")
                .containsExactly("CHILD2");
        }

        @Test
        @DisplayName("Get Path - Get Full Path from Root to Node")
        void getPath() {
            // given
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            List<CodeItem> path = treeService.getPath(grandChild1.getId());

            // then
            assertThat(path).hasSize(3)
                .extracting("codeKey")
                .containsExactly("ROOT", "CHILD1", "GRANDCHILD1");
        }

        @Test
        @DisplayName("Get Path Of Root - Handle Root Node Path")
        void getPathOfRoot() {
            // given
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            List<CodeItem> path = treeService.getPath(root.getId());

            // then
            assertThat(path).hasSize(1)
                .extracting("codeKey")
                .containsExactly("ROOT");
        }

        @Test
        @DisplayName("Find Empty Children - Handle Node Without Children")
        void findEmptyChildren() {
            // given
            when(treeRepository.findDirectChildren(grandChild1.getId())).thenReturn(Collections.emptyList());

            // when
            List<CodeItem> children = treeService.getDirectChildren(grandChild1.getId());

            // then
            assertThat(children).isEmpty();
            verify(treeRepository).findDirectChildren(grandChild1.getId());
        }
    }

    @Nested
    @DisplayName("CRUD Operations Tests")
    class CrudOperationsTests {

        @Test
        @DisplayName("Create - Create New Entity")
        void create() {
            // given
            CodeItem newItem = createCodeItem(null, "NEW_ITEM", "New Test Item", root.getId(), 3);
            CodeItem savedItem = createCodeItem(5L, "NEW_ITEM", "New Test Item", root.getId(), 3);
            
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(savedItem);

            // when
            CodeItem created = treeService.create(newItem);

            // then
            assertThat(created).isNotNull();
            assertThat(created.getCodeKey()).isEqualTo("NEW_ITEM");
            assertThat(created.getParentId()).isEqualTo(root.getId());
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Create Root Node - Create Top Level Entity")
        void createRootNode() {
            // given
            CodeItem newRoot = createCodeItem(null, "NEW_ROOT", "New Root Node", null, 1);
            CodeItem savedRoot = createCodeItem(8L, "NEW_ROOT", "New Root Node", null, 1);
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(savedRoot);

            // when
            CodeItem created = treeService.create(newRoot);

            // then
            assertThat(created).isNotNull();
            assertThat(created.getCodeKey()).isEqualTo("NEW_ROOT");
            assertThat(created.getParentId()).isNull();
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Update - Modify Existing Entity")
        void update() {
            // given
            child1.setCodeValue("Updated First Child");
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(child1);

            // when
            CodeItem updated = treeService.update(child1);

            // then
            assertThat(updated).isNotNull();
            assertThat(updated.getCodeValue()).isEqualTo("Updated First Child");
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Update Sort Order - Change Node Position")
        void updateSortOrder() {
            // given
            child1.setSortOrder(3);
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(child1);

            // when
            CodeItem updated = treeService.update(child1);

            // then
            assertThat(updated).isNotNull();
            assertThat(updated.getSortOrder()).isEqualTo(3);
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Find By Id - Retrieve Entity by ID")
        void findById() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));

            // when
            Optional<CodeItem> found = treeService.findById(child1.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getCodeKey()).isEqualTo("CHILD1");
            verify(treeRepository).findById(child1.getId());
        }

        @Test
        @DisplayName("Find Non-existent Node - Handle Invalid ID")
        void findNonExistentNode() {
            // given
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            Optional<CodeItem> result = treeService.findById(999L);

            // then
            assertThat(result).isEmpty();
            verify(treeRepository).findById(999L);
        }

        @Test
        @DisplayName("Delete By Id - Remove Entity by ID")
        void deleteById() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findItemWithAllDescendants(child1.getId()))
                .thenReturn(Arrays.asList(child1, grandChild1));
            doNothing().when(treeRepository).deleteById(child1.getId());

            // when
            treeService.deleteById(child1.getId());

            // then
            verify(treeRepository).deleteById(child1.getId());
        }
    }

    @Nested
    @DisplayName("Tree Manipulation Tests")
    class TreeManipulationTests {

        @Test
        @DisplayName("Move - Change Node's Parent")
        void move() {
            // given
            CodeItem newParent = createCodeItem(6L, "NEW_PARENT", "New Parent Node", null, 2);
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(newParent.getId())).thenReturn(Optional.of(newParent));
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(child1);

            // when
            CodeItem moved = treeService.move(child1.getId(), newParent.getId());

            // then
            assertThat(moved).isNotNull();
            assertThat(moved.getParentId()).isEqualTo(newParent.getId());
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Copy Subtree - Duplicate Node and Its Children")
        void copySubtree() {
            // given
            CodeItem copiedRoot = createCodeItem(7L, "ROOT", "Root Node", null, 1);
            CodeItem copiedChild1 = createCodeItem(8L, "CHILD1", "First Child", 7L, 1);
            CodeItem copiedChild2 = createCodeItem(9L, "CHILD2", "Second Child", 7L, 2);
            CodeItem copiedGrandChild1 = createCodeItem(10L, "GRANDCHILD1", "First Grandchild", 8L, 1);
            
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findItemWithAllDescendants(root.getId()))
                .thenReturn(Arrays.asList(root, child1, child2, grandChild1));
            when(treeRepository.findDirectChildren(root.getId())).thenReturn(Arrays.asList(child1, child2));
            when(treeRepository.findDirectChildren(child1.getId())).thenReturn(Collections.singletonList(grandChild1));
            when(treeRepository.findDirectChildren(child2.getId())).thenReturn(Collections.emptyList());
            when(treeRepository.findDirectChildren(grandChild1.getId())).thenReturn(Collections.emptyList());
            
            // Mock saveAndFlush calls for each node copy
            when(treeRepository.saveAndFlush(any(CodeItem.class)))
                .thenReturn(copiedRoot)
                .thenReturn(copiedChild1)
                .thenReturn(copiedChild2)
                .thenReturn(copiedGrandChild1);

            // when
            CodeItem copied = treeService.copySubtree(root.getId(), null);

            // then
            assertThat(copied).isNotNull();
            assertThat(copied.getCodeKey()).isEqualTo("ROOT");
            verify(treeRepository, atLeastOnce()).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Reorder Children - Should Throw UnsupportedOperationException in Base Implementation")
        void reorderChildren() {
            // given
            List<Long> newOrder = Arrays.asList(child2.getId(), child1.getId());

            // when & then
            assertThatThrownBy(() -> treeService.reorderChildren(root.getId(), newOrder))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("reorderChildren is not supported in the generic implementation");
        }
    }

    @Nested
    @DisplayName("Tree Analysis Tests")
    class TreeAnalysisTests {

        @Test
        @DisplayName("Is Descendant Of - Check if Node is Child of Another")
        void isDescendantOf() {
            // given
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            boolean isDescendant = treeService.isDescendantOf(grandChild1.getId(), root.getId());

            // then
            assertThat(isDescendant).isTrue();
        }

        @Test
        @DisplayName("Is Not Descendant Of - Check Sibling Relationship")
        void isNotDescendantOf() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            boolean isDescendant = treeService.isDescendantOf(child1.getId(), child2.getId());

            // then
            assertThat(isDescendant).isFalse();
        }

        @Test
        @DisplayName("Get Depth - Get Node's Level in Tree")
        void getDepth() {
            // given
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            int depth = treeService.getDepth(grandChild1.getId());

            // then
            assertThat(depth).isEqualTo(2);
        }

        @Test
        @DisplayName("Get Root Depth - Root Node Should Have Depth 0")
        void getRootDepth() {
            // given
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            int depth = treeService.getDepth(root.getId());

            // then
            assertThat(depth).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Move To Invalid Parent - Handle Non-existent Parent")
        void moveToInvalidParent() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> treeService.move(child1.getId(), 999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Parent entity not found with ID: 999");
        }

        @Test
        @DisplayName("Copy To Invalid Parent - Handle Non-existent Parent")
        void copyToInvalidParent() {
            // given
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> treeService.copySubtree(root.getId(), 999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Parent entity not found with ID: 999");
        }

        @Test
        @DisplayName("Create With Invalid Parent - Handle Non-existent Parent")
        void createWithInvalidParent() {
            // given
            CodeItem invalidItem = createCodeItem(null, "INVALID", "Invalid Node", 999L, 1);
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> treeService.create(invalidItem))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Parent entity not found with ID: 999");
        }

        @Test
        @DisplayName("Reorder Invalid Children - Should Throw UnsupportedOperationException in Base Implementation")
        void reorderInvalidChildren() {
            // given
            List<Long> invalidOrder = Arrays.asList(999L, 888L);

            // when & then
            // Note: Base implementation throws UnsupportedOperationException before validation
            assertThatThrownBy(() -> treeService.reorderChildren(root.getId(), invalidOrder))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("reorderChildren is not supported in the generic implementation");
        }

        @Test
        @DisplayName("Move Non-existent Node - Handle Invalid Source ID")
        void moveNonExistentNode() {
            // given
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> treeService.move(999L, root.getId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Entity not found with ID: 999");
        }

        @Test
        @DisplayName("Copy Non-existent Node - Handle Invalid Source ID")
        void copyNonExistentNode() {
            // given
            when(treeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> treeService.copySubtree(999L, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Source entity not found: 999");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Find Siblings Of Root - Root Has No Siblings")
        void findSiblingsOfRoot() {
            // given
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.findRootItems()).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> siblings = treeService.findSiblings(root.getId());

            // then
            assertThat(siblings).isEmpty();
        }

        @Test
        @DisplayName("Find Ancestors Of Root - Root Has No Ancestors")
        void findAncestorsOfRoot() {
            // given
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));

            // when
            List<CodeItem> ancestors = treeService.findAncestors(root.getId());

            // then
            assertThat(ancestors).isEmpty();
        }

        @Test
        @DisplayName("Move To Same Parent - Should Work Without Issues")
        void moveToSameParent() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(child1);

            // when
            CodeItem moved = treeService.move(child1.getId(), root.getId());

            // then
            assertThat(moved).isNotNull();
            assertThat(moved.getParentId()).isEqualTo(root.getId());
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }

        @Test
        @DisplayName("Move To Root Level - Set Parent to Null")
        void moveToRootLevel() {
            // given
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.saveAndFlush(any(CodeItem.class))).thenReturn(child1);

            // when
            CodeItem moved = treeService.move(child1.getId(), null);

            // then
            assertThat(moved).isNotNull();
            assertThat(moved.getParentId()).isNull();
            verify(treeRepository).saveAndFlush(any(CodeItem.class));
        }
    }

    private CodeItem createCodeItem(Long id, String key, String value, Long parentId, int sortOrder) {
        CodeItem item = new CodeItem();
        item.setId(id);
        item.setCodeGroup(codeGroup);
        item.setCodeKey(key);
        item.setCodeValue(value);
        item.setDescription(value + " description");
        item.setSortOrder(sortOrder);
        item.setIsActive(true);
        item.setParentId(parentId);
        return item;
    }
} 