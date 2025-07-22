package dev.simplecore.simplix.demo.domain.common.user.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.core.annotation.I18nTitle;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "user_account")
@org.hibernate.annotations.Table(
    appliesTo = "user_account",
    comment = "User Account: Basic account information for users accessing the system"
)
@I18nTitle({"ko=사용자 계정", "en=User Account", "ja=ユーザーアカウント"})
public class UserAccount extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name="user_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("User ID: Unique UUID used in the system")
    @I18nTitle({"ko=사용자 ID", "en=User ID", "ja=ユーザーID"})
    private String userId;

    @Column(unique = true, nullable = false)
    @Comment("Login Account: Username used for user login")
    @I18nTitle({"ko=로그인 계정", "en=Username", "ja=ユーザー名"})
    private String username;

    @Column(nullable = false)
    @Comment("Password: Encrypted password for user authentication")
    @I18nTitle({"ko=비밀번호", "en=Password", "ja=パスワード"})
    private String password;

    @Column(nullable = false)
    @Comment("Account Status: Whether the user account is active")
    @I18nTitle({"ko=계정 상태", "en=Account Status", "ja=アカウント状態"})
    private Boolean enabled;

    @Column(name = "real_name")
    @Comment("Name: User's real name")
    @I18nTitle({"ko=실명", "en=Real Name", "ja=実名"})
    @DisplayName(description = "User's real name for display")
    private String realName;

    @Column(columnDefinition = "TEXT")
    @Comment("Self-introduction: User's self-introduction and description")
    @I18nTitle({"ko=자기소개", "en=Description", "ja=自己紹介"})
    private String description;

    @Column(name = "email", unique = true)
    @Comment("Email: User's email address")
    @I18nTitle({"ko=이메일", "en=Email", "ja=メールアドレス"})
    private String email;

    @Column(name = "mobile_phone")
    @Comment("Mobile Phone: User's mobile phone number")
    @I18nTitle({"ko=휴대전화", "en=Mobile Phone", "ja=携帯電話"})
    private String mobilePhone;

    @Column(name = "office_phone")
    @Comment("Office Phone: User's office phone number")
    @I18nTitle({"ko=사무실 전화", "en=Office Phone", "ja=オフィス電話"})
    private String officePhone;

    @Column(name = "postal_code", length = 10)
    @Comment("Postal Code: Postal code of the user's address")
    @I18nTitle({"ko=우편번호", "en=Postal Code", "ja=郵便番号"})
    private String postalCode;

    @Column(name = "address")
    @Comment("Address: User's primary address")
    @I18nTitle({"ko=주소", "en=Address", "ja=住所"})
    private String address;

    @Column(name = "address_detail")
    @Comment("Detailed Address: User's detailed address")
    @I18nTitle({"ko=상세주소", "en=Address Detail", "ja=詳細住所"})
    private String addressDetail;

    // A user can only have one position (1:N relationship)
    @ManyToOne(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @JoinColumn(name = "position_id")
    @Comment("Position Information: User's position within the organization")
    @I18nTitle({"ko=직급", "en=Position", "ja=職級"})
    private UserPosition position;

    // A user can have multiple roles (including job titles) (N:M relationship)
    @JsonManagedReference
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @JoinTable(
        name = "user_account_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Comment("Permission Information: System roles and job titles assigned to the user")
    @I18nTitle({"ko=역할", "en=Roles", "ja=役割"})
    private Set<UserRole> roles;

    // A user can belong to multiple organizations (departments/groups) (N:M relationship)
    @JsonManagedReference
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @JoinTable(
        name = "user_account_organizations",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id")
    )
    @Comment("Affiliated Organization: Department or organizational group the user belongs to")
    @I18nTitle({"ko=소속조직", "en=Organizations", "ja=所属組織"})
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