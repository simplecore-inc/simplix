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
@Table(name = "user_role")
@org.hibernate.annotations.Table(
    appliesTo = "user_role",
    comment = "User Role: Group of permissions or job title information"
)
@I18nTitle({"ko=사용자 역할", "en=User Role", "ja=ユーザー役割"})
public class UserRole extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name="role_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Role ID: Unique UUID used in the system")
    @I18nTitle({"ko=역할 ID", "en=Role ID", "ja=役割ID"})
    private String roleId;

    @Column(nullable = false, unique = true)
    @Comment("Role Name: Unique role name used in the system")
    @I18nTitle({"ko=역할명", "en=Role Name", "ja=役할명"})
    @DisplayName(description = "Role name for display")
    private String name;  // 예: 팀장, 프로젝트 매니저

    @Column(nullable = false, unique = true)
    @Comment("Role Code: Unique role code name used in the system")
    @I18nTitle({"ko=역할 코드", "en=Role Code", "ja=役割コード"})
    private String role;  // 예: ROLE_ADMIN, ROLE_MANAGER, ROLE_USER, ROLE_GUEST, ROLE_TEAM_LEAD, ROLE_PM, ...

    @Column(length = 500)
    @Comment("Role Description: Detailed description and purpose of the role")
    @I18nTitle({"ko=역할 설명", "en=Role Description", "ja=役割説明"})
    private String description;

    @Column(name = "item_order", nullable = false)
    @Comment("Order")
    @I18nTitle({"ko=순서", "en=Order", "ja=順序"})
    private Integer itemOrder;

        
    //----------------------------------

    @Override
    public String getId() {
        return this.roleId;
    }

    @Override
    public void setId(String id) {
        this.roleId = id;
    }
}