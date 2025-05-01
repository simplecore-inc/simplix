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
@Table(name = "user_position")
@org.hibernate.annotations.Table(
    appliesTo = "user_position",
    comment = "사용자 직급: 조직 내 공식 직급 체계 정보"
)
public class UserPosition extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name="position_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("직급 ID: 시스템에서 사용하는 고유 UUID")
    private String positionId;

    @Column(nullable = false, unique = true)
    @Comment("직급명: 조직 내 공식 직급명")
    private String name;  // 예: 사원, 대리, 과장

    @Column(length = 500)
    @Comment("직급설명: 직급에 대한 상세 설명 및 용도")
    private String description;

    @Column(name = "item_order", nullable = false, unique = true)
    @Comment("순서")
    private String itemOrder;

        
    //----------------------------------

    @Override
    public String getId() {
        return this.positionId;
    }

    @Override
    public void setId(String id) {
        this.positionId = id;
    }

    @PrePersist
    public void generateId() {
        if (this.positionId == null) {
            this.positionId = UUID.randomUUID().toString();
        }
    }
}