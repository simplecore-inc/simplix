package dev.simplecore.simplix.demo.domain.common.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.simplecore.simplix.core.entity.converter.JsonMapConverter;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn.ColumnType;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Comment;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;

@Entity
@Getter
@Setter
@Table(name = "code_group")
@Comment("Code Group - Code group information")
@TreeEntityAttributes(
    tableName = "code_group",
    idColumn = "code_group_id",
    parentIdColumn = "parent_id",
    sortOrderColumn = "sort_order",
    lookupColumns = {
        @LookupColumn(name = "group_key", type = ColumnType.STRING),
        @LookupColumn(name = "is_active", type = ColumnType.BOOLEAN)
    }
)
public class CodeGroup extends AuditingBaseEntity<String> implements TreeEntity<CodeGroup, String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name = "code_group_id", length = 36, nullable = false, updatable = false, unique = true)
    @Comment("Code Group ID - Unique UUID Version 7 used in the system")
    private String codeGroupId;

    @Column(name = "group_key", length = 50, nullable = false)
    @Comment("Group Key - Code key")
    private String groupKey;

    @Column(name = "group_name", length = 200, nullable = false)
    @Comment("Group Name - Group Name")
    private String groupName;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "group_name_i18n", length = 2000, nullable = false)
    @Comment("[i18n] Group Name - Group Name")
    private Map<String, String> groupNameI18n;

    @Column(name = "description", length = 2000, nullable = false)
    @Comment("Description - Description of the code group")
    private String description;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "description_i18n", columnDefinition = "TEXT", nullable = true)
    @Comment("[i18n] Description - Description of the code group")
    private Map<String, String> descriptionI18n;

    @Column(name = "sort_order", nullable = false)
    @Comment("Sort Order - Sort order")
    private Integer sortOrder = 999999;

    @Column(name = "is_active", nullable = false)
    @Comment("Is Active - Is active")
    private Boolean isActive = true;

    @Column(name = "parent_id")
    @Comment("Parent ID - Parent ID")
    private String parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = true, insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @Comment("Parent Group - Parent group")
    @JsonIncludeProperties({"codeGroupId", "groupKey", "groupName",
            "groupNameI18n", "description", "descriptionI18n", "sortOrder", "isActive"})
    @NotFound(action = NotFoundAction.IGNORE)
    private CodeGroup parent;

    @Transient
    @JsonManagedReference
    @JsonIgnoreProperties({"parent"})
    private List<CodeGroup> children = new ArrayList<>();

    //----------------------------------

    @Override
    public String getId() {
        return this.codeGroupId;
    }

    @Override
    public void setId(String id) {
        this.codeGroupId = id;
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
    public List<CodeGroup> getChildren() {
        return this.children;
    }

    @Override
    public void setChildren(List<CodeGroup> children) {
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