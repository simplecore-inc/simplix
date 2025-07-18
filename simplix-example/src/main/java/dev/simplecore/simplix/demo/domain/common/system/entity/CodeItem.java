package dev.simplecore.simplix.demo.domain.common.system.entity;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn.ColumnType;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "code_item")
@org.hibernate.annotations.Table(
    appliesTo = "code_item",
    comment = "Code Item: Code item information"
)
@TreeEntityAttributes(
    tableName = "code_item",
    idColumn = "code_id",
    parentIdColumn = "parent_id",
    sortOrderColumn = "sort_order",
    lookupColumns = {
        @LookupColumn(name = "code_key", type = ColumnType.STRING),
        @LookupColumn(name = "is_active", type = ColumnType.BOOLEAN)
    }
)
public class CodeItem extends AuditingBaseEntity<String> implements TreeEntity<CodeItem, String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name = "code_id", length = 36, nullable = false, updatable = false, unique = true)
    @Comment("Code ID: Unique UUID used in the system")
    private String codeId;

    @Column(name = "code_key", length = 50, nullable = false)
    @Comment("Code Key: Code key")
    private String codeKey;

    @Column(name = "code_value", length = 200, nullable = false)
    @Comment("Code Value: Code value")
    private String codeValue;

    @Column(name = "description", length = 2000, nullable = false)
    @Comment("Description: Description")
    private String description;

    @Column(name = "sort_order", nullable = false)
    @Comment("Sort Order: Sort order")
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    @Comment("Is Active: Is active")
    private Boolean isActive;
    
    @Column(name = "is_default", nullable = false)
    @Comment("Is Default: Is default")
    private Boolean isDefault = false;

    @Column(name = "parent_id")
    @Comment("Parent ID: Parent ID")
    private String parentId;
    
    @Transient
    private List<CodeItem> children = new ArrayList<>();

    //----------------------------------

    @Override
    public String getId() {
        return this.codeId;
    }

    @Override
    public void setId(String id) {
        this.codeId = id;
    }

    @Override
    public String getParentId() {
        return this.parentId;
    }

    @Override
    public void setParentId(String parentId) {
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