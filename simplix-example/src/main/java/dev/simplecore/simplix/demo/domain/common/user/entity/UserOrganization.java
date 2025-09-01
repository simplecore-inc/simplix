package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import dev.simplecore.simplix.demo.domain.common.user.enums.OrganizationType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;

import jakarta.persistence.*;


@Entity
@Getter
@Setter
@Table(name = "user_organization")
@Comment("User Organization: Information about organizational units such as departments or project groups")
public class UserOrganization extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name="organization_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Organization ID: Unique UUID Version 7 used in the system")
    private String organizationId;

    @Column(nullable = false, unique = true)
    @Comment("Organization Name: Name of the department or group")
    @DisplayName(description = "Organization name for display")
    private String name;  // e.g., Development Team, HR Department, Project A Team

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false)
    @Comment("Organization Type: Type of organization (DEPARTMENT: Department, GROUP: Group)")
    private OrganizationType orgType;  // DEPARTMENT or GROUP

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private UserOrganization parent;

    @Column(length = 500)
    @Comment("Organization Description: Detailed description and purpose of the organization")
    private String description;

    @Column(name = "item_order", nullable = false)
    @Comment("Order")
    private Integer itemOrder;

        
    //----------------------------------

    @Override
    public String getId() {
        return this.organizationId;
    }

    @Override
    public void setId(String id) {
        this.organizationId = id;
    }
}