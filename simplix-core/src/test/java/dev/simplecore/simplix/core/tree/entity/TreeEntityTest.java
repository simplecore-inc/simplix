package dev.simplecore.simplix.core.tree.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TreeEntity interface default methods")
class TreeEntityTest {

    static class SimpleTreeNode implements TreeEntity<SimpleTreeNode, Long> {
        private Long id;
        private Long parentId;
        private List<SimpleTreeNode> children = new ArrayList<>();

        SimpleTreeNode(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
        @Override public Long getParentId() { return parentId; }
        @Override public void setParentId(Long parentId) { this.parentId = parentId; }
        @Override public List<SimpleTreeNode> getChildren() { return children; }
        @Override public void setChildren(List<SimpleTreeNode> children) { this.children = children; }
        @Override public Comparable<?> getSortKey() { return id; }
    }

    @Test
    @DisplayName("isRoot should return true when parentId is null")
    void isRootShouldReturnTrue() {
        SimpleTreeNode root = new SimpleTreeNode(1L, null);
        assertThat(root.isRoot()).isTrue();
    }

    @Test
    @DisplayName("isRoot should return false when parentId is set")
    void isRootShouldReturnFalse() {
        SimpleTreeNode child = new SimpleTreeNode(2L, 1L);
        assertThat(child.isRoot()).isFalse();
    }

    @Test
    @DisplayName("isLeaf should return true when children is empty")
    void isLeafShouldReturnTrueForEmpty() {
        SimpleTreeNode leaf = new SimpleTreeNode(1L, null);
        leaf.setChildren(Collections.emptyList());
        assertThat(leaf.isLeaf()).isTrue();
    }

    @Test
    @DisplayName("isLeaf should return true when children is null")
    void isLeafShouldReturnTrueForNull() {
        SimpleTreeNode leaf = new SimpleTreeNode(1L, null);
        leaf.setChildren(null);
        assertThat(leaf.isLeaf()).isTrue();
    }

    @Test
    @DisplayName("isLeaf should return false when has children")
    void isLeafShouldReturnFalseWhenHasChildren() {
        SimpleTreeNode parent = new SimpleTreeNode(1L, null);
        SimpleTreeNode child = new SimpleTreeNode(2L, 1L);
        parent.setChildren(List.of(child));
        assertThat(parent.isLeaf()).isFalse();
    }
}
