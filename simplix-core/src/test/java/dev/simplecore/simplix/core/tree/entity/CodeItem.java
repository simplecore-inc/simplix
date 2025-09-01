package dev.simplecore.simplix.core.tree.entity;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn.ColumnType;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "code_items")
@Getter
@Setter
@TreeEntityAttributes(
    tableName = "code_items",
    idColumn = "code_id",
    parentIdColumn = "parent_id",
    sortOrderColumn = "sort_order",
    lookupColumns = {
        @LookupColumn(name = "code_key", type = ColumnType.STRING),
        @LookupColumn(name = "code_value", type = ColumnType.STRING),
        @LookupColumn(name = "is_active", type = ColumnType.BOOLEAN)
    }
)
public class CodeItem implements TreeEntity<CodeItem, Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code_id")
    private Long codeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_group_id", nullable = false)
    private CodeGroup codeGroup;

    @Column(name = "code_key", length = 50, nullable = false)
    private String codeKey;

    @Column(name = "code_value", length = 200, nullable = false)
    private String codeValue;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "parent_id")
    private Long parentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "code_id", insertable = false, updatable = false)
    private CodeItem parent;

    @OneToMany(mappedBy = "parent")
    private List<CodeItem> children = new ArrayList<>();

    //----------------------------------

    @Override
    public Long getId() {
        return this.codeId;
    }

    @Override
    public void setId(Long id) {
        this.codeId = id;
    }

    @Override
    public Long getParentId() {
        return this.parentId;
    }

    @Override
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @Override
    public List<CodeItem> getChildren() {
        return this.children;
    }

    @Override
    public void setChildren(List<CodeItem> children) {
        this.children = children;
    }

    @Override
    public Integer getSortOrder() {
        return this.sortOrder;
    }

    @Override
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
} 