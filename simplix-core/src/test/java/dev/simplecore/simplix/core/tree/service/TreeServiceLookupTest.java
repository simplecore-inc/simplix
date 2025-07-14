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
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("Tree Service Lookup Test")
class TreeServiceLookupTest {

    @Mock
    private CodeItemRepository treeRepository;

    @InjectMocks
    private TreeBaseService<CodeItem, Long> treeService;

    private CodeGroup codeGroup;
    private CodeItem root;
    private CodeItem child1;
    private CodeItem child2;
    private CodeItem grandChild1;
    private CodeItem grandChild2;

    @BeforeEach
    void setUp() {
        // Create code group
        codeGroup = new CodeGroup();
        codeGroup.setId(1L);
        codeGroup.setGroupKey("TEST_GROUP");
        codeGroup.setGroupName("Test Group");
        codeGroup.setDescription("Test code group for lookup tests");

        // Create test data with various attributes for lookup testing
        root = createCodeItem(1L, "ROOT", "Root Node", null, 1);
        child1 = createCodeItem(2L, "DEPT_IT", "IT Department", root.getId(), 1);
        child2 = createCodeItem(3L, "DEPT_HR", "HR Department", root.getId(), 2);
        grandChild1 = createCodeItem(4L, "TEAM_DEV", "Development Team", child1.getId(), 1);
        grandChild2 = createCodeItem(5L, "TEAM_QA", "QA Team", child1.getId(), 2);

        // Set up hierarchy
        root.setChildren(Arrays.asList(child1, child2));
        child1.setChildren(Arrays.asList(grandChild1, grandChild2));
        child2.setChildren(Collections.emptyList());
        grandChild1.setChildren(Collections.emptyList());
        grandChild2.setChildren(Collections.emptyList());
    }

    @Nested
    @DisplayName("Basic Lookup Tests")
    class BasicLookupTests {

        @Test
        @DisplayName("Find By Lookup - Single Parameter Search")
        void findByLookupSingleParameter() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeValue", "IT Department");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.singletonList(child1));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getCodeKey()).isEqualTo("DEPT_IT");
            assertThat(results.get(0).getCodeValue()).isEqualTo("IT Department");
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Multiple Parameters Search")
        void findByLookupMultipleParameters() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "TEAM_");
            parameters.put("parentId", "2");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(grandChild1, grandChild2));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(2);
            assertThat(results).extracting("codeKey").containsExactly("TEAM_DEV", "TEAM_QA");
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - No Results")
        void findByLookupNoResults() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeValue", "Non-existent");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.emptyList());

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).isEmpty();
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Empty Parameters")
        void findByLookupEmptyParameters() {
            // given
            Map<String, String> parameters = new HashMap<>();
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(root, child1, child2, grandChild1, grandChild2));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(5);
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Null Parameters Should Throw Exception")
        void findByLookupNullParameters() {
            // when & then
            assertThatThrownBy(() -> treeService.findByLookup(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search parameters cannot be null");
        }
    }

    @Nested
    @DisplayName("Paginated Lookup Tests")
    class PaginatedLookupTests {

        @Test
        @DisplayName("Find By Lookup With Pagination - First Page")
        void findByLookupWithPaginationFirstPage() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "TEAM_");
            Pageable pageable = PageRequest.of(0, 1);
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(grandChild1, grandChild2));

            // when
            Page<CodeItem> results = treeService.findByLookup(parameters, pageable);

            // then
            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getCodeKey()).isEqualTo("TEAM_DEV");
            assertThat(results.getTotalElements()).isEqualTo(2);
            assertThat(results.getTotalPages()).isEqualTo(2);
            assertThat(results.getNumber()).isEqualTo(0);
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup With Pagination - Second Page")
        void findByLookupWithPaginationSecondPage() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "TEAM_");
            Pageable pageable = PageRequest.of(1, 1);
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(grandChild1, grandChild2));

            // when
            Page<CodeItem> results = treeService.findByLookup(parameters, pageable);

            // then
            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getCodeKey()).isEqualTo("TEAM_QA");
            assertThat(results.getTotalElements()).isEqualTo(2);
            assertThat(results.getTotalPages()).isEqualTo(2);
            assertThat(results.getNumber()).isEqualTo(1);
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup With Pagination - Empty Results")
        void findByLookupWithPaginationEmptyResults() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeValue", "Non-existent");
            Pageable pageable = PageRequest.of(0, 10);
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.emptyList());

            // when
            Page<CodeItem> results = treeService.findByLookup(parameters, pageable);

            // then
            assertThat(results.getContent()).isEmpty();
            assertThat(results.getTotalElements()).isEqualTo(0);
            assertThat(results.getTotalPages()).isEqualTo(0);
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup With Pagination - Null Parameters Should Throw Exception")
        void findByLookupWithPaginationNullParameters() {
            // given
            Pageable pageable = PageRequest.of(0, 10);

            // when & then
            assertThatThrownBy(() -> treeService.findByLookup(null, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search parameters cannot be null");
        }

        @Test
        @DisplayName("Find By Lookup With Pagination - Null Pageable Should Throw Exception")
        void findByLookupWithPaginationNullPageable() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "TEAM_");

            // when & then
            assertThatThrownBy(() -> treeService.findByLookup(parameters, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pageable cannot be null");
        }
    }

    @Nested
    @DisplayName("Hierarchical Lookup Tests")
    class HierarchicalLookupTests {

        @Test
        @DisplayName("Find Hierarchy By Lookup - Include Ancestors")
        void findHierarchyByLookupIncludeAncestors() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "TEAM_DEV");
            
            // Mock the lookup to return grandChild1
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.singletonList(grandChild1));
            
            // Mock ancestor finding
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            
            // Mock bulk loading of relevant items
            Set<Long> relevantIds = new HashSet<>(Arrays.asList(1L, 2L, 4L)); // root, child1, grandChild1
            when(treeRepository.findAllById(relevantIds)).thenReturn(Arrays.asList(root, child1, grandChild1));
            
            // Mock hierarchy building
            when(treeRepository.buildHierarchy(anyList())).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> results = treeService.findHierarchyByLookup(parameters);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getCodeKey()).isEqualTo("ROOT");
            verify(treeRepository).findByLookup(parameters);
            verify(treeRepository).findAllById(any());
            verify(treeRepository).buildHierarchy(anyList());
        }

        @Test
        @DisplayName("Find Hierarchy By Lookup - Multiple Matches")
        void findHierarchyByLookupMultipleMatches() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "TEAM_");
            
            // Mock the lookup to return both team items
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(grandChild1, grandChild2));
            
            // Mock ancestor finding for grandChild1
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(grandChild2.getId())).thenReturn(Optional.of(grandChild2));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            
            // Mock bulk loading of relevant items
            Set<Long> relevantIds = new HashSet<>(Arrays.asList(1L, 2L, 4L, 5L)); // root, child1, grandChild1, grandChild2
            when(treeRepository.findAllById(relevantIds)).thenReturn(Arrays.asList(root, child1, grandChild1, grandChild2));
            
            // Mock hierarchy building
            when(treeRepository.buildHierarchy(anyList())).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> results = treeService.findHierarchyByLookup(parameters);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getCodeKey()).isEqualTo("ROOT");
            verify(treeRepository).findByLookup(parameters);
            verify(treeRepository).findAllById(any());
            verify(treeRepository).buildHierarchy(anyList());
        }

        @Test
        @DisplayName("Find Hierarchy By Lookup - No Results")
        void findHierarchyByLookupNoResults() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeValue", "Non-existent");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.emptyList());
            when(treeRepository.findAllById(Collections.emptySet())).thenReturn(Collections.emptyList());
            when(treeRepository.buildHierarchy(Collections.emptyList())).thenReturn(Collections.emptyList());

            // when
            List<CodeItem> results = treeService.findHierarchyByLookup(parameters);

            // then
            assertThat(results).isEmpty();
            verify(treeRepository).findByLookup(parameters);
            verify(treeRepository).findAllById(Collections.emptySet());
            verify(treeRepository).buildHierarchy(Collections.emptyList());
        }

        @Test
        @DisplayName("Find Hierarchy By Lookup - Root Level Match")
        void findHierarchyByLookupRootLevelMatch() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "ROOT");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.singletonList(root));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            
            Set<Long> relevantIds = new HashSet<>(Collections.singletonList(1L)); // only root
            when(treeRepository.findAllById(relevantIds)).thenReturn(Collections.singletonList(root));
            when(treeRepository.buildHierarchy(anyList())).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> results = treeService.findHierarchyByLookup(parameters);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getCodeKey()).isEqualTo("ROOT");
            verify(treeRepository).findByLookup(parameters);
            verify(treeRepository).findAllById(any());
            verify(treeRepository).buildHierarchy(anyList());
        }

        @Test
        @DisplayName("Find Hierarchy By Lookup - Null Parameters Should Throw Exception")
        void findHierarchyByLookupNullParameters() {
            // when & then
            assertThatThrownBy(() -> treeService.findHierarchyByLookup(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search parameters cannot be null");
        }
    }

    @Nested
    @DisplayName("Lookup Performance Tests")
    class LookupPerformanceTests {

        @Test
        @DisplayName("Find By Lookup - Large Result Set")
        void findByLookupLargeResultSet() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("isActive", "true");
            
            // Create a large list of items
            List<CodeItem> largeResultSet = Arrays.asList(root, child1, child2, grandChild1, grandChild2);
            when(treeRepository.findByLookup(parameters)).thenReturn(largeResultSet);

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(5);
            verify(treeRepository, times(1)).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup With Pagination - Large Dataset")
        void findByLookupWithPaginationLargeDataset() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("isActive", "true");
            Pageable pageable = PageRequest.of(2, 2); // Third page, 2 items per page
            
            List<CodeItem> largeResultSet = Arrays.asList(root, child1, child2, grandChild1, grandChild2);
            when(treeRepository.findByLookup(parameters)).thenReturn(largeResultSet);

            // when
            Page<CodeItem> results = treeService.findByLookup(parameters, pageable);

            // then
            assertThat(results.getContent()).hasSize(1); // Only 1 item on the third page
            assertThat(results.getContent().get(0).getCodeKey()).isEqualTo("TEAM_QA");
            assertThat(results.getTotalElements()).isEqualTo(5);
            assertThat(results.getTotalPages()).isEqualTo(3);
            verify(treeRepository, times(1)).findByLookup(parameters);
        }
    }

    @Nested
    @DisplayName("Nested Object Lookup Tests")
    class NestedObjectLookupTests {

        @Test
        @DisplayName("Find By Lookup - CodeGroup GroupKey")
        void findByLookupCodeGroupKey() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeGroup.groupKey", "TEST_GROUP");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(root, child1, child2, grandChild1, grandChild2));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(5);
            assertThat(results).allMatch(item -> item.getCodeGroup().getGroupKey().equals("TEST_GROUP"));
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - CodeGroup GroupName")
        void findByLookupCodeGroupName() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeGroup.groupName", "Test Group");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(root, child1, child2, grandChild1, grandChild2));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(5);
            assertThat(results).allMatch(item -> item.getCodeGroup().getGroupName().equals("Test Group"));
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - CodeGroup ID")
        void findByLookupCodeGroupId() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeGroup.id", "1");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(root, child1, child2, grandChild1, grandChild2));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(5);
            assertThat(results).allMatch(item -> item.getCodeGroup().getId().equals(1L));
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Combined Entity and Nested Object Fields")
        void findByLookupCombinedFields() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeGroup.groupKey", "TEST_GROUP");
            parameters.put("codeKey", "DEPT_");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(child1, child2));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(2);
            assertThat(results).extracting("codeKey").containsExactly("DEPT_IT", "DEPT_HR");
            assertThat(results).allMatch(item -> item.getCodeGroup().getGroupKey().equals("TEST_GROUP"));
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Non-existent Nested Field")
        void findByLookupNonExistentNestedField() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeGroup.groupKey", "NON_EXISTENT_GROUP");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.emptyList());

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).isEmpty();
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find Hierarchy By Lookup - Nested Object Field")
        void findHierarchyByLookupNestedField() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeGroup.groupKey", "TEST_GROUP");
            parameters.put("codeKey", "TEAM_DEV");
            
            // Mock the lookup to return grandChild1
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.singletonList(grandChild1));
            
            // Mock ancestor finding
            when(treeRepository.findById(grandChild1.getId())).thenReturn(Optional.of(grandChild1));
            when(treeRepository.findById(child1.getId())).thenReturn(Optional.of(child1));
            when(treeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            
            // Mock bulk loading of relevant items
            Set<Long> relevantIds = new HashSet<>(Arrays.asList(1L, 2L, 4L)); // root, child1, grandChild1
            when(treeRepository.findAllById(relevantIds)).thenReturn(Arrays.asList(root, child1, grandChild1));
            
            // Mock hierarchy building
            when(treeRepository.buildHierarchy(anyList())).thenReturn(Collections.singletonList(root));

            // when
            List<CodeItem> results = treeService.findHierarchyByLookup(parameters);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getCodeKey()).isEqualTo("ROOT");
            verify(treeRepository).findByLookup(parameters);
            verify(treeRepository).findAllById(any());
            verify(treeRepository).buildHierarchy(anyList());
        }
    }

    @Nested
    @DisplayName("Lookup Edge Cases Tests")
    class LookupEdgeCasesTests {

        @Test
        @DisplayName("Find By Lookup - Special Characters in Parameters")
        void findByLookupSpecialCharacters() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeValue", "IT & Development");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.singletonList(child1));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(1);
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Case Sensitivity")
        void findByLookupCaseSensitivity() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("codeKey", "dept_it"); // lowercase
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.emptyList());

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).isEmpty();
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Numeric String Parameters")
        void findByLookupNumericStringParameters() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("sortOrder", "1");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Arrays.asList(root, child1, grandChild1));

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).hasSize(3);
            verify(treeRepository).findByLookup(parameters);
        }

        @Test
        @DisplayName("Find By Lookup - Boolean String Parameters")
        void findByLookupBooleanStringParameters() {
            // given
            Map<String, String> parameters = new HashMap<>();
            parameters.put("isActive", "false");
            
            when(treeRepository.findByLookup(parameters)).thenReturn(Collections.emptyList());

            // when
            List<CodeItem> results = treeService.findByLookup(parameters);

            // then
            assertThat(results).isEmpty();
            verify(treeRepository).findByLookup(parameters);
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