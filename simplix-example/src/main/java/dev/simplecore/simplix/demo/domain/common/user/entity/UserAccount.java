package dev.simplecore.simplix.demo.domain.common.user.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import jakarta.persistence.*;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "user_account")
@Comment("User Account - Basic account information for users accessing the system")
public class UserAccount extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name="user_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("User ID - Unique UUID Version 7 used in the system")
    private String userId;

    @Column(unique = true, nullable = false)
    @Comment("Login Account - Username used for user login")
    private String username;

    @Column(nullable = false)
    @Comment("Password - Encrypted password for user authentication")
    private String password;

    @Column(nullable = false)
    @Comment("Account Status - Whether the user account is active")
    private Boolean enabled;

    @Column(name = "real_name")
    @Comment("Name - User real name")
    @DisplayName(description = "User's real name for display")
    private String realName;

    @Column(columnDefinition = "TEXT")
    @Comment("Self-introduction - User self-introduction and description")
    private String description;

    @Column(name = "email", unique = true)
    @Comment("Email - User email address")
    private String email;

    @Column(name = "mobile_phone")
    @Comment("Mobile Phone - User mobile phone number")
    private String mobilePhone;

    @Column(name = "office_phone")
    @Comment("Office Phone - User office phone number")
    private String officePhone;

    @Column(name = "postal_code", length = 10)
    @Comment("Postal Code - Postal code of the user address")
    private String postalCode;

    @Column(name = "address")
    @Comment("Address - User primary address")
    private String address;

    @Column(name = "address_detail")
    @Comment("Detailed Address - User detailed address")
    private String addressDetail;

    // A user can only have one position (1:N relationship)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    @Comment("Position Information - User position within the organization")
    private UserPosition position;

    // A user can have multiple roles (including job titles) (N:M relationship)
    @JsonManagedReference
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_account_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Comment("Permission Information - System roles and job titles assigned to the user")
    private Set<UserRole> roles;

    // A user can belong to multiple organizations (departments/groups) (N:M relationship)
    @JsonManagedReference
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_account_organizations",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id")
    )
    @Comment("Affiliated Organization - Department or organizational group the user belongs to")
    private Set<UserOrganization> organizations;

        
    //----------------------------------

    @Override
    public String getId() {
        return this.userId;
    }

    @Override
    public void setId(String id) {
        this.userId = id;
    }
}