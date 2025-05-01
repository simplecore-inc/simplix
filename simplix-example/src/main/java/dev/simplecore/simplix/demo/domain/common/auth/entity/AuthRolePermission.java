package dev.simplecore.simplix.demo.domain.common.auth.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import dev.simplecore.simplix.demo.domain.BaseEntity;
import dev.simplecore.simplix.demo.domain.common.auth.enums.PermissionTargetType;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;

import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "auth_role_permission")
@org.hibernate.annotations.Table(
    appliesTo = "auth_role_permission",
    comment = "역할별 권한 설정: 각 역할에 대한 권한 설정 정보"
)
public class AuthRolePermission extends BaseEntity<String> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name="role_permission_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("ID: 시스템에서 사용하는 고유 UUID")
    private String rolePermissionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "auth_permission_id", nullable = false)
    @Comment("권한: 해당 설정의 권한")
    private AuthPermission permission;

    @Column(nullable = false)
    @Comment("권한 범위: 권한이 적용되는 대상의 범위")
    private PermissionTargetType targetType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id")
    @Comment("역할: 해당 권한이 속한 역할")
    private UserRole role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_organization_id")
    @Comment("조직: 해당 권한이 속한 조직")
    private UserOrganization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_account_id")
    @Comment("사용자: 해당 권한이 속한 사용자")
    private UserAccount userAccount;

    @Column(nullable = false)
    @Comment("목록 조회 권한")
    private Boolean listPermission = false;

    @Column(nullable = false)
    @Comment("상세 조회 권한")
    private Boolean viewPermission = false;

    @Column(nullable = false)
    @Comment("생성 권한")
    private Boolean createPermission = false;

    @Column(nullable = false)
    @Comment("수정 권한")
    private Boolean editPermission = false;

    @Column(nullable = false)
    @Comment("삭제 권한")
    private Boolean deletePermission = false;

    @Column(nullable = false)
    @Comment("추가 기능 1 권한")
    private Boolean extra1Permission = false;

    @Column(nullable = false)
    @Comment("추가 기능 2 권한")
    private Boolean extra2Permission = false;

    @Column(nullable = false)
    @Comment("추가 기능 3 권한")
    private Boolean extra3Permission = false;

    @Column(nullable = false)
    @Comment("추가 기능 4 권한")
    private Boolean extra4Permission = false;

    @Column(nullable = false)
    @Comment("추가 기능 5 권한")
    private Boolean extra5Permission = false;

    //----------------------------------

    @Column(length = 500)
    @Comment("설명: 상세 설명")
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

    @PrePersist
    public void generateId() {
        if (this.rolePermissionId == null) {
            this.rolePermissionId = UUID.randomUUID().toString();
        }
    }
}