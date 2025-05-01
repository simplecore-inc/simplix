package dev.simplecore.simplix.demo.domain.common.auth.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import dev.simplecore.simplix.demo.domain.BaseEntity;

import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "auth_permission")
@org.hibernate.annotations.Table(
    appliesTo = "auth_permission",
    comment = "권한 설정: 시스템의 각 기능에 대한 접근 권한 설정 정보"
)
public class AuthPermission extends BaseEntity<String> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name="permission_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("권한 ID: 시스템에서 사용하는 고유 UUID")
    private String permissionId;

    @Column(nullable = false, unique = true)
    @Comment("권한코드: 시스템에서 사용하는 권한 코드명")
    private String name;  // 예: USER, BOARD, ...

    @Column(length = 500)
    @Comment("권한설명: 권한에 대한 상세 설명 및 용도")
    private String description;

    @Column(nullable = false)
    @Comment("목록 조회 기능 사용 여부")
    private Boolean useList = true;

    @Column(nullable = false)
    @Comment("상세 조회 기능 사용 여부")
    private Boolean useView = true;

    @Column(nullable = false)
    @Comment("생성 기능 사용 여부")
    private Boolean useCreate = true;

    @Column(nullable = false)
    @Comment("수정 기능 사용 여부")
    private Boolean useEdit = true;

    @Column(nullable = false)
    @Comment("삭제 기능 사용 여부")
    private Boolean useDelete = true;

    @Column(nullable = false)
    @Comment("추가 기능 1 사용 여부")
    private Boolean useExtra1 = false;

    @Column(nullable = false)
    @Comment("추가 기능 2 사용 여부")
    private Boolean useExtra2 = false;

    @Column(nullable = false)
    @Comment("추가 기능 3 사용 여부")
    private Boolean useExtra3 = false;

    @Column(nullable = false)
    @Comment("추가 기능 4 사용 여부")
    private Boolean useExtra4 = false;

    @Column(nullable = false)
    @Comment("추가 기능 5 사용 여부")
    private Boolean useExtra5 = false;

    @Column(length = 100)
    @Comment("추가 기능 1 이름")
    private String extra1Name;

    @Column(length = 100)
    @Comment("추가 기능 2 이름")
    private String extra2Name;

    @Column(length = 100)
    @Comment("추가 기능 3 이름")
    private String extra3Name;

    @Column(length = 100)
    @Comment("추가 기능 4 이름")
    private String extra4Name;

    @Column(length = 100)
    @Comment("추가 기능 5 이름")
    private String extra5Name;

    //----------------------------------

    @Override
    public String getId() {
        return this.permissionId;
    }

    @Override
    public void setId(String id) {
        this.permissionId = id;
    }

    @PrePersist
    public void generateId() {
        if (this.permissionId == null) {
            this.permissionId = UUID.randomUUID().toString();
        }
    }
}