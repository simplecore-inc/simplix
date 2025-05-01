package dev.simplecore.simplix.demo.domain.common.user.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;

import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "user_role")
@org.hibernate.annotations.Table(
    appliesTo = "user_role",
    comment = "사용자 역할: 권한 그룹 또는 직책 정보"
)
public class UserRole extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name="role_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("역할 ID: 시스템에서 사용하는 고유 UUID")
    private String roleId;

    @Column(nullable = false, unique = true)
    @Comment("역할명: 시스템에서 사용하는 고유 역할명")
    private String name;  // 예: 팀장, 프로젝트 매니저

    @Column(nullable = false, unique = true)
    @Comment("역할코드: 시스템에서 사용하는 고유 역할 코드명")
    private String role;  // 예: ROLE_ADMIN, ROLE_MANAGER, ROLE_USER, ROLE_GUEST, ROLE_TEAM_LEAD, ROLE_PM, ...

    @Column(length = 500)
    @Comment("역할설명: 역할에 대한 상세 설명 및 용도")
    private String description;

    @Column(name = "item_order", nullable = false, unique = true)
    @Comment("순서")
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

    @PrePersist
    public void generateId() {
        if (this.roleId == null) {
            this.roleId = UUID.randomUUID().toString();
        }
    }
}