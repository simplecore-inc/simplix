package dev.simplecore.simplix.demo.domain.common.auth.entity;

import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;
import dev.simplecore.simplix.demo.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "auth_permission")
@Comment("Permission Settings - Access permission settings for each function in the system")
public class AuthPermission extends BaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name="permission_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Permission ID - Unique UUID Version 7 used in the system")
    private String permissionId;

    @Column(nullable = false, unique = true)
    @Comment("Permission Code - Code name for the permission used in the system")
    @DisplayName(description = "Permission code for display")
    private String name;  // 예: USER, BOARD, ...

    @Column(length = 500)
    @Comment("Permission Description - Detailed description and purpose of the permission")
    private String description;

    @Column(nullable = false)
    @Comment("Whether to use the list view function")
    private Boolean useList = true;

    @Column(nullable = false)
    @Comment("Whether to use the detail view function")
    private Boolean useView = true;

    @Column(nullable = false)
    @Comment("Whether to use the create function")
    private Boolean useCreate = true;

    @Column(nullable = false)
    @Comment("Whether to use the edit function")
    private Boolean useEdit = true;

    @Column(nullable = false)
    @Comment("Whether to use the delete function")
    private Boolean useDelete = true;

    @Column(nullable = false)
    @Comment("Whether to use extra function 1")
    private Boolean useExtra1 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 2")
    private Boolean useExtra2 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 3")
    private Boolean useExtra3 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 4")
    private Boolean useExtra4 = false;

    @Column(nullable = false)
    @Comment("Whether to use extra function 5")
    private Boolean useExtra5 = false;

    @Column(length = 100)
    @Comment("Extra Function 1 Name")
    private String extra1Name;

    @Column(length = 100)
    @Comment("Extra Function 2 Name")
    private String extra2Name;

    @Column(length = 100)
    @Comment("Extra Function 3 Name")
    private String extra3Name;

    @Column(length = 100)
    @Comment("Extra Function 4 Name")
    private String extra4Name;

    @Column(length = 100)
    @Comment("Extra Function 5 Name")
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