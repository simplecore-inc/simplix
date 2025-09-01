package dev.simplecore.simplix.demo.domain;


import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditingBaseEntity<K> extends SimpliXBaseEntity<K> {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    @Comment("Creator - User who initially created the record")
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    @Comment("Creation Date - Initial creation timestamp with timezone")
    private OffsetDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    @Comment("Modifier - User who last modified the record")
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    @Comment("Modification Date - Last modification timestamp with timezone")
    private OffsetDateTime updatedAt;
}