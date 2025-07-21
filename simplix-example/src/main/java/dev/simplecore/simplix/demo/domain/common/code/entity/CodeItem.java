package dev.simplecore.simplix.demo.domain.common.code.entity;

import dev.simplecore.simplix.core.entity.converter.JsonMapConverter;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

import java.util.Map;


/**
 * Code Item Entity
 * Implements a hierarchical code system structure
 */
@Entity
@Table(name = "code_item")
@Getter
@Setter
@org.hibernate.annotations.Table(
        appliesTo = "code_item",
        comment = "Code Item: Individual code item information belonging to a code group"
)
//@EntityListeners(CodeItemEntityListener.class)
public class CodeItem extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name = "code_item_id", length = 36)
    @Comment("Code Item ID: Unique ID used in the system")
    private String codeItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_group_id", nullable = false)
    @Comment("Code Group: Code group to which this item belongs")
    private CodeGroup codeGroup;

    @Column(name = "code_key", length = 50, nullable = false)
    @Comment("Code Key: Unique key identifying the code item")
    private String codeKey;

    @Column(name = "code_value", length = 200, nullable = false)
    @Comment("Code Value: Actual value of the code item")
    private String codeValue;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "code_value_i18n", length = 2000, nullable = false)
    @Comment("[i18n] Code Value: Actual value of the code item")
    private Map<String, String> codeValueI18n;

    @Column(name = "description", length = 2000, nullable = true)
    @Comment("Description: Description of the code item")
    private String description;

    @Column(name = "sort_order", nullable = false)
    @Comment("Sort Order: Display order of the code item")
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    @Comment("Is Active: Whether the code item is available for use")
    private Boolean isActive;

    @Column(name = "is_default", nullable = false)
    @Comment("Is Default: Is default")
    private Boolean isDefault = false;

    //----------------------------------

    @Override
    public String getId() {
        return this.codeItemId;
    }

    @Override
    public void setId(String id) {
        this.codeItemId = id;
    }
}
