package dev.simplecore.simplix.demo.domain.common.auth.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import dev.simplecore.simplix.demo.domain.BaseEntity;
import dev.simplecore.simplix.demo.domain.common.auth.enums.PermissionTargetType;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import dev.simplecore.simplix.core.annotation.I18nTitle;

import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "auth_role_permission")
@org.hibernate.annotations.Table(
    appliesTo = "auth_role_permission",
    comment = "Role-based Permission Settings: Permission settings for each role"
)
@I18nTitle({"ko=역할별 권한 설정", "en=Role Permission", "ja=役割別権限設定"})
public class AuthRolePermission extends BaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name="role_permission_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("ID: Unique UUID used in the system")
    @I18nTitle({"ko=역할 권한 ID", "en=Role Permission ID", "ja=役割権限ID"})
    private String rolePermissionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "auth_permission_id", nullable = false)
    @Comment("Permission: Permission for this setting")
    @I18nTitle({"ko=권한", "en=Permission", "ja=権限"})
    private AuthPermission permission;

    @Column(nullable = false)
    @Comment("Permission Scope: Scope of the target to which the permission applies")
    @I18nTitle({"ko=권한 범위", "en=Target Type", "ja=権限範囲"})
    private PermissionTargetType targetType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id")
    @Comment("Role: The role to which this permission belongs")
    @I18nTitle({"ko=역할", "en=Role", "ja=役割"})
    private UserRole role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_organization_id")
    @Comment("Organization: The organization to which this permission belongs")
    @I18nTitle({"ko=조직", "en=Organization", "ja=組織"})
    private UserOrganization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_account_id")
    @Comment("User: The user to whom this permission belongs")
    @I18nTitle({"ko=사용자", "en=User Account", "ja=ユーザー"})
    private UserAccount userAccount;

    @Column(nullable = false)
    @Comment("List View Permission")
    @I18nTitle({"ko=목록 조회 권한", "en=List Permission", "ja=リスト表示権限"})
    private Boolean listPermission = false;

    @Column(nullable = false)
    @Comment("Detail View Permission")
    @I18nTitle({"ko=상세 조회 권한", "en=View Permission", "ja=詳細表示権限"})
    private Boolean viewPermission = false;

    @Column(nullable = false)
    @Comment("Create Permission")
    @I18nTitle({"ko=생성 권한", "en=Create Permission", "ja=作成権限"})
    private Boolean createPermission = false;

    @Column(nullable = false)
    @Comment("Edit Permission")
    @I18nTitle({"ko=수정 권한", "en=Edit Permission", "ja=編集権限"})
    private Boolean editPermission = false;

    @Column(nullable = false)
    @Comment("Delete Permission")
    @I18nTitle({"ko=삭제 권한", "en=Delete Permission", "ja=削除権限"})
    private Boolean deletePermission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 1 Permission")
    @I18nTitle({"ko=추가 기능 1 권한", "en=Extra Permission 1", "ja=追加権限1"})
    private Boolean extra1Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 2 Permission")
    @I18nTitle({"ko=추가 기능 2 권한", "en=Extra Permission 2", "ja=追加権限2"})
    private Boolean extra2Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 3 Permission")
    @I18nTitle({"ko=추가 기능 3 권한", "en=Extra Permission 3", "ja=追加権限3"})
    private Boolean extra3Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 4 Permission")
    @I18nTitle({"ko=추가 기능 4 권한", "en=Extra Permission 4", "ja=追加権限4"})
    private Boolean extra4Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 5 Permission")
    @I18nTitle({"ko=추가 기능 5 권한", "en=Extra Permission 5", "ja=追加権限5"})
    private Boolean extra5Permission = false;

    //----------------------------------

    @Column(length = 500)
    @Comment("Description: Detailed description")
    @I18nTitle({"ko=설명", "en=Description", "ja=説明"})
    private String description;

    //----------------------------------

    @Override
    public String getId() {
        return this.rolePermissionId;
    }

    @Override
    public void setId(String id) {
        this.rolePermissionId = id;
    }
}