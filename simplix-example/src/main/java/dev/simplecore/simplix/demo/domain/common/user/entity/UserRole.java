package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import dev.simplecore.simplix.core.hibernate.UuidV7Generator;

import jakarta.persistence.*;


@Entity
@Getter
@Setter
@Table(name = "user_role")
@Comment("User Role - Group of permissions or job title information")
public class UserRole extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name="role_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Role ID - Unique UUID Version 7 used in the system")
    private String roleId;

    @Column(nullable = false, unique = true)
    @Comment("Role Name - Unique role name used in the system")
    @DisplayName(description = "Role name for display")
    private String name;  // e.g., Team Leader, Project Manager

    @Column(nullable = false, unique = true)
    @Comment("Role Code - Unique role code name used in the system")
    private String role;  // e.g., ROLE_ADMIN, ROLE_MANAGER, ROLE_USER, ROLE_GUEST, ROLE_TEAM_LEAD, ROLE_PM, ...

    @Column(length = 500)
    @Comment("Role Description - Detailed description and purpose of the role")
    private String description;

    @Column(name = "item_order", nullable = false)
    @Comment("Order")
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