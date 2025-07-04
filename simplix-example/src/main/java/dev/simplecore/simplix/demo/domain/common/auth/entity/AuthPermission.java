package dev.simplecore.simplix.demo.domain.common.auth.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import dev.simplecore.simplix.demo.domain.BaseEntity;
import dev.simplecore.simplix.core.annotation.I18nTitle;

import org.hibernate.annotations.Comment;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "auth_permission")
@org.hibernate.annotations.Table(
    appliesTo = "auth_permission",
    comment = "Permission Settings: Access permission settings for each function in the system"
)
@I18nTitle({"ko=권한 설정", "en=Auth Permission", "ja=権限設定"})
public class AuthPermission extends BaseEntity<String> {

    @Id
    @GeneratedValue(generator = "tsid")
    @GenericGenerator(name = "tsid", strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator")
    @Column(name="permission_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Permission ID: Unique UUID used in the system")
    @I18nTitle({"ko=권한 ID", "en=Permission ID", "ja=権限ID"})
    private String permissionId;

    @Column(nullable = false, unique = true)
    @Comment("Permission Code: Code name for the permission used in the system")
    @I18nTitle({"ko=권한 코드", "en=Permission Code", "ja=権限コード"})
    private String name;  // 예: USER, BOARD, ...

    @Column(length = 500)
    @Comment("Permission Description: Detailed description and purpose of the permission")
    @I18nTitle({"ko=권한 설명", "en=Permission Description", "ja=権限説明"})
    private String description;

    @Column(nullable = false)
    @Comment("Whether to use the list view function")
    @I18nTitle({"ko=목록 조회 기능", "en=List Function", "ja=リスト機能"})
    private Boolean useList = true;

    @Column(nullable = false)
    @Comment("Whether to use the detail view function")
    @I18nTitle({"ko=상세 조회 기능", "en=View Function", "ja=詳細表示機能"})
    private Boolean useView = true;

    @Column(nullable = false)
    @Comment("Whether to use the create function")
    @I18nTitle({"ko=생성 기능", "en=Create Function", "ja=作成機能"})
    private Boolean useCreate = true;

    @Column(nullable = false)
    @Comment("Whether to use the edit function")
    @I18nTitle({"ko=수정 기능", "en=Edit Function", "ja=編集機能"})
    private Boolean useEdit = true;

    @Column(nullable = false)
    @Comment("Whether to use the delete function")
    @I18nTitle({"ko=삭제 기능", "en=Delete Function", "ja=削除機能"})
    private Boolean useDelete = true;

    @Column(nullable = false)
    @Comment("Whether to use extra function 1")
    @I18nTitle({"ko=추가 기능 1", "en=Extra Function 1", "ja=追加機能1"})
    private Boolean useExtra1 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 2")
    @I18nTitle({"ko=추가 기능 2", "en=Extra Function 2", "ja=追加機能2"})
    private Boolean useExtra2 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 3")
    @I18nTitle({"ko=추가 기능 3", "en=Extra Function 3", "ja=追加機能3"})
    private Boolean useExtra3 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 4")
    @I18nTitle({"ko=추가 기능 4", "en=Extra Function 4", "ja=追加機能4"})
    private Boolean useExtra4 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 5")
    @I18nTitle({"ko=추가 기능 5", "en=Extra Function 5", "ja=追加機能5"})
    private Boolean useExtra5 = false;

    @Column(length = 100)
    @Comment("Extra Function 1 Name")
    @I18nTitle({"ko=추가 기능 1 이름", "en=Extra Function 1 Name", "ja=追加機能1名"})
    private String extra1Name;

    @Column(length = 100)
    @Comment("Extra Function 2 Name")
    @I18nTitle({"ko=추가 기능 2 이름", "en=Extra Function 2 Name", "ja=追加機能2名"})
    private String extra2Name;

    @Column(length = 100)
    @Comment("Extra Function 3 Name")
    @I18nTitle({"ko=추가 기능 3 이름", "en=Extra Function 3 Name", "ja=追加機能3名"})
    private String extra3Name;

    @Column(length = 100)
    @Comment("Extra Function 4 Name")
    @I18nTitle({"ko=추가 기능 4 이름", "en=Extra Function 4 Name", "ja=追加機能4名"})
    private String extra4Name;

    @Column(length = 100)
    @Comment("Extra Function 5 Name")
    @I18nTitle({"ko=추가 기능 5 이름", "en=Extra Function 5 Name", "ja=追加機能5名"})
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
}