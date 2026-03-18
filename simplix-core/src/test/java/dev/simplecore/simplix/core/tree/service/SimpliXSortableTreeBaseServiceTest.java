package dev.simplecore.simplix.core.tree.service;

import dev.simplecore.simplix.core.tree.entity.SortableTreeEntity;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXSortableTreeBaseService")
class SimpliXSortableTreeBaseServiceTest {

    @Getter
    @Setter
    static class SortableItem implements SortableTreeEntity<SortableItem, Long> {
        private Long id;
        private Long parentId;
        private Integer sortOrder;
        private List<SortableItem> children = new ArrayList<>();
        private String name;

        @Override
        public Comparable<?> getSortKey() {
            return sortOrder;
        }
    }

    @Mock
    private SimpliXTreeRepository<SortableItem, Long> repository;

    private SimpliXSortableTreeBaseService<SortableItem, Long> service;

    private SortableItem parent;
    private SortableItem child1;
    private SortableItem child2;
    private SortableItem child3;

    @BeforeEach
    void setUp() {
        service = new SimpliXSortableTreeBaseService<>(repository);

        parent = new SortableItem();
        parent.setId(1L);
        parent.setParentId(null);
        parent.setSortOrder(0);
        parent.setName("Parent");

        child1 = new SortableItem();
        child1.setId(2L);
        child1.setParentId(1L);
        child1.setSortOrder(0);
        child1.setName("Child 1");
        child1.setChildren(Collections.emptyList());

        child2 = new SortableItem();
        child2.setId(3L);
        child2.setParentId(1L);
        child2.setSortOrder(1);
        child2.setName("Child 2");
        child2.setChildren(Collections.emptyList());

        child3 = new SortableItem();
        child3.setId(4L);
        child3.setParentId(1L);
        child3.setSortOrder(2);
        child3.setName("Child 3");
        child3.setChildren(Collections.emptyList());

        parent.setChildren(Arrays.asList(child1, child2, child3));
    }

    @Nested
    @DisplayName("reorderChildren")
    class ReorderChildren {

        @Test
        @DisplayName("should reorder children based on provided ID list")
        void shouldReorderChildren() {
            when(repository.findDirectChildren(1L)).thenReturn(Arrays.asList(child1, child2, child3));
            when(repository.findById(any())).thenReturn(Optional.of(parent));
            when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            service.reorderChildren(1L, Arrays.asList(3L, 2L, 4L));

            assertThat(child2.getSortOrder()).isEqualTo(0);
            assertThat(child1.getSortOrder()).isEqualTo(1);
            assertThat(child3.getSortOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("should skip IDs not found in children")
        void shouldSkipMissingIds() {
            when(repository.findDirectChildren(1L)).thenReturn(Arrays.asList(child1, child2));
            when(repository.findById(any())).thenReturn(Optional.of(parent));
            when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            service.reorderChildren(1L, Arrays.asList(2L, 999L, 3L));

            assertThat(child1.getSortOrder()).isEqualTo(0);
            assertThat(child2.getSortOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle root level reordering with null parent")
        void shouldHandleRootReordering() {
            SortableItem root1 = new SortableItem();
            root1.setId(10L);
            root1.setParentId(null);
            root1.setSortOrder(0);
            root1.setChildren(Collections.emptyList());

            SortableItem root2 = new SortableItem();
            root2.setId(11L);
            root2.setParentId(null);
            root2.setSortOrder(1);
            root2.setChildren(Collections.emptyList());

            when(repository.findRootItems()).thenReturn(Arrays.asList(root1, root2));
            when(repository.findById(any())).thenReturn(Optional.of(root1));
            when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            service.reorderChildren(null, Arrays.asList(11L, 10L));

            assertThat(root2.getSortOrder()).isEqualTo(0);
            assertThat(root1.getSortOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw for null ordered IDs list")
        void shouldThrowForNullList() {
            assertThatThrownBy(() -> service.reorderChildren(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
