package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.core.annotation.I18nTitle;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import dev.simplecore.simplix.demo.domain.common.user.enums.OrganizationType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "user_organization")
@org.hibernate.annotations.Table(
    appliesTo = "user_organization",
    comment = "User Organization: Information about organizational units such as departments or project groups"
)
@I18nTitle({"ko=사용자 조직", "en=User Organization", "ja=ユーザー組織"})
public class UserOrganization extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name="organization_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Organization ID: Unique UUID used in the system")
    @I18nTitle({"ko=조직 ID", "en=Organization ID", "ja=組織ID"})
    private String organizationId;

    @Column(nullable = false, unique = true)
    @Comment("Organization Name: Name of the department or group")
    @I18nTitle({"ko=조직명", "en=Organization Name", "ja=組織名"})
    @DisplayName(description = "Organization name for display")
    private String name;  // 예: 개발팀, 인사부, 프로젝트A팀

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false)
    @Comment("Organization Type: Type of organization (DEPARTMENT: Department, GROUP: Group)")
    @I18nTitle({"ko=조직 유형", "en=Organization Type", "ja=組織タイプ"})
    private OrganizationType orgType;  // DEPARTMENT or GROUP

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @BatchSize(size = 50)
    @I18nTitle({"ko=상위 조직", "en=Parent Organization", "ja=上位組織"})
    private UserOrganization parent;

    @Column(length = 500)
    @Comment("Organization Description: Detailed description and purpose of the organization")
    @I18nTitle({"ko=조직 설명", "en=Organization Description", "ja=組織説明"})
    private String description;

    @Column(name = "item_order", nullable = false)
    @Comment("Order")
    @I18nTitle({"ko=순서", "en=Order", "ja=順서"})
    private BigDecimal itemOrder;

        
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