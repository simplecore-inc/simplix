package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import dev.simplecore.simplix.demo.domain.common.user.enums.OrganizationType;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "user_organization")
@org.hibernate.annotations.Table(
    appliesTo = "user_organization",
    comment = "사용자 조직: 부서 또는 프로젝트 그룹과 같은 조직 단위 정보"
)
public class UserOrganization extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name="organization_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("조직 ID: 시스템에서 사용하는 고유 UUID")
    private String organizationId;

    @Column(nullable = false, unique = true)
    @Comment("조직명: 부서 또는 그룹의 이름")
    private String name;  // 예: 개발팀, 인사부, 프로젝트A팀

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false)
    @Comment("조직유형: 조직의 종류 (DEPARTMENT: 부서, GROUP: 그룹)")
    private OrganizationType orgType;  // DEPARTMENT or GROUP

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private UserOrganization parent;

    @Column(length = 500)
    @Comment("조직설명: 조직의 상세 설명 및 용도")
    private String description;

    @Column(name = "item_order", nullable = false, unique = true)
    @Comment("순서")
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

    @PrePersist
    public void generateId() {
        if (this.organizationId == null) {
            this.organizationId = UUID.randomUUID().toString();
        }
    }
}