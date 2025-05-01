package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Comment;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "user_account")
@org.hibernate.annotations.Table(
    appliesTo = "user_account",
    comment = "사용자 계정: 시스템에 접근하는 사용자의 기본 계정 정보"
)
public class UserAccount extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name="user_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("사용자 ID: 시스템에서 사용하는 고유 UUID")
    private String userId;

    @Column(unique = true, nullable = false)
    @Comment("로그인 계정: 사용자 로그인시 사용하는 계정명")
    private String username;

    @Column(nullable = false)
    @Comment("비밀번호: 사용자 인증을 위한 암호화된 비밀번호")
    private String password;

    @Column(nullable = false)
    @Comment("계정상태: 사용자 계정의 활성화 상태 여부")
    private Boolean enabled;

    @Column(name = "real_name")
    @Comment("이름: 사용자의 실명")
    private String realName;

    @Lob
    @Column(name = "profile_image")
    @Comment("프로필 이미지: 사용자 프로필 이미지 바이너리 데이터")
    private byte[] profileImage;

    @Column(name = "profile_image_type")
    @Comment("프로필 이미지 타입: 이미지의 MIME 타입 (예: image/jpeg, image/png)")
    private String profileImageType;

    @Column(columnDefinition = "TEXT")
    @Comment("자기소개: 사용자 자기소개 및 설명")
    private String description;

    @Column(name = "email", unique = true)
    @Comment("이메일: 사용자 이메일 주소")
    private String email;

    @Column(name = "mobile_phone")
    @Comment("휴대전화: 사용자 휴대전화 번호")
    private String mobilePhone;

    @Column(name = "office_phone")
    @Comment("사무실 전화: 사용자 사무실 전화번호")
    private String officePhone;

    @Column(name = "postal_code", length = 10)
    @Comment("우편번호: 사용자 주소의 우편번호")
    private String postalCode;

    @Column(name = "address")
    @Comment("주소: 사용자의 기본 주소")
    private String address;

    @Column(name = "address_detail")
    @Comment("상세주소: 사용자의 상세 주소")
    private String addressDetail;

    // 사용자는 하나의 직급만 가질 수 있음 (1:N 관계)
    @ManyToOne
    @JoinColumn(name = "position_id")
    @Comment("직급정보: 사용자의 조직 내 직급")
    private UserPosition position;

    // 사용자는 여러 개의 역할(직책 포함)을 가질 수 있음 (N:M 관계)
    @JsonManagedReference
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_account_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Comment("권한정보: 사용자에게 부여된 시스템 역할 및 직책")
    private Set<UserRole> roles;

    // 사용자는 여러 조직(부서/그룹)에 속할 수 있음 (N:M 관계)
    @JsonManagedReference
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_account_organizations",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id")
    )
    @Comment("소속조직: 사용자가 속한 부서 또는 조직 그룹")
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
    @PrePersist
    public void generateId() {
        if (this.userId == null) {
            this.userId = UUID.randomUUID().toString();
        }
    }
}