package dev.simplecore.simplix.demo.domain;


import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import dev.simplecore.simplix.core.annotation.I18nTitle;
import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditingBaseEntity<K> extends SimpliXBaseEntity<K> {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    @Comment("Creator: User who initially created the record")
    @I18nTitle({"ko=생성자", "en=Created By", "ja=作成者"})
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    @Comment("Creation Date: Initial creation timestamp with timezone")
    @I18nTitle({"ko=생성일시", "en=Created At", "ja=作成日時"})
    private OffsetDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    @Comment("Modifier: User who last modified the record")
    @I18nTitle({"ko=수정자", "en=Updated By", "ja=更新者"})
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    @Comment("Modification Date: Last modification timestamp with timezone")
    @I18nTitle({"ko=수정일시", "en=Updated At", "ja=更新日時"})
    private OffsetDateTime updatedAt;
}