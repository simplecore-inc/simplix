package dev.simplecore.simplix.core.tree.entity;

import dev.simplecore.simplix.core.entity.SoftDeletable;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Test entity for verifying soft-delete filtering in tree queries.
 */
@Entity
@Table(name = "soft_delete_tree_items")
@FilterDef(
    name = "softDeleteFilter",
    parameters = @ParamDef(name = "isDeleted", type = Boolean.class)
)
@Filter(name = "softDeleteFilter", condition = "deleted = :isDeleted")
@TreeEntityAttributes(
    tableName = "soft_delete_tree_items",
    idColumn = "id",
    parentIdColumn = "parent_id",
    sortOrderColumn = "sort_order"
)
@Getter
@Setter
@NoArgsConstructor
public class SoftDeleteTreeItem implements TreeEntity<SoftDeleteTreeItem, Long>, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Transient
    private List<SoftDeleteTreeItem> children = new ArrayList<>();

    public SoftDeleteTreeItem(String name, Long parentId, int sortOrder) {
        this.name = name;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.deleted = false;
    }

    @Override
    public Comparable<?> getSortKey() {
        return sortOrder;
    }

    @Override
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}
