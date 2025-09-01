package dev.simplecore.simplix.demo.domain.common.auth.entity;

import dev.simplecore.simplix.demo.domain.BaseEntity;
import dev.simplecore.simplix.demo.domain.common.auth.enums.PermissionTargetType;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "auth_role_permission")
@Comment("Role Permission: Role-specific permission settings")
public class AuthRolePermission extends BaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name="role_permission_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Role Permission ID: Unique UUID Version 7 used in the system")
    private String rolePermissionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "auth_permission_id", nullable = false)
    @Comment("Permission: Permission for this setting")
    private AuthPermission permission;

    @Column(nullable = false)
    @Comment("Permission Scope: Scope of the target to which the permission applies")
    private PermissionTargetType targetType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id")
    @Comment("Role: The role to which this permission belongs")
    private UserRole role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_organization_id")
    @Comment("Organization: The organization to which this permission belongs")
    private UserOrganization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_account_id")
    @Comment("User: The user to whom this permission belongs")
    private UserAccount userAccount;

    @Column(nullable = false)
    @Comment("List View Permission")
    private Boolean listPermission = false;

    @Column(nullable = false)
    @Comment("Detail View Permission")
    private Boolean viewPermission = false;

    @Column(nullable = false)
    @Comment("Create Permission")
    private Boolean createPermission = false;

    @Column(nullable = false)
    @Comment("Edit Permission")
    private Boolean editPermission = false;

    @Column(nullable = false)
    @Comment("Delete Permission")
    private Boolean deletePermission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 1 Permission")
    private Boolean extra1Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 2 Permission")
    private Boolean extra2Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 3 Permission")
    private Boolean extra3Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 4 Permission")
    private Boolean extra4Permission = false;

    @Column(nullable = false)
    @Comment("Extra Feature 5 Permission")
    private Boolean extra5Permission = false;

    //----------------------------------

    @Column(length = 500)
    @Comment("Description: Detailed description")
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