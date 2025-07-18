package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.core.annotation.I18nTitle;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "user_position")
@org.hibernate.annotations.Table(
    appliesTo = "user_position",
    comment = "User Position: Official position hierarchy information within the organization"
)
@I18nTitle({"ko=사용자 직급", "en=User Position", "ja=ユーザー職級"})
public class UserPosition extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name="position_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Position ID: Unique UUID used in the system")
    @I18nTitle({"ko=직급 ID", "en=Position ID", "ja=職級ID"})
    private String positionId;

    @Column(nullable = false, unique = true)
    @Comment("Position Name: Official position name within the organization")
    @I18nTitle({"ko=직급명", "en=Position Name", "ja=職級名"})
    @DisplayName(description = "Position name for display")
    private String name;  // 예: 사원, 대리, 과장

    @Column(length = 500)
    @Comment("Position Description: Detailed description and purpose of the position")
    @I18nTitle({"ko=직급 설명", "en=Position Description", "ja=職級説明"})
    private String description;

    @Column(name = "item_order", nullable = false)
    @Comment("Order")
    @I18nTitle({"ko=순서", "en=Order", "ja=順서"})
    private Integer itemOrder;

        
    //----------------------------------

    @Override
    public String getId() {
        return this.positionId;
    }

    @Override
    public void setId(String id) {
        this.positionId = id;
    }
}