package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.demo.domain.BaseEntity;
import dev.simplecore.simplix.core.convert.datetime.DateTimeConverter;
import dev.simplecore.simplix.core.entity.SimpliXCompositeKey;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

import java.time.LocalDateTime;
import lombok.Data;

@Entity
@Getter
@Setter
@Table(name = "user_account_log")
@org.hibernate.annotations.Table(
    appliesTo = "user_account_log",
    comment = "사용자 계정 변경로그: (복합PK 테스트 용도)"
)
public class UserAccountLog extends BaseEntity<UserAccountLog.UserAccountLogId> {

    @EmbeddedId
    private UserAccountLogId logId;

    @Column(name = "log_message")
    @Comment("로그 메시지: 변경 내용")
    private String logMessage;

    //----------------------------------

    @Override
    public UserAccountLogId getId() {
        return this.logId;
    }

    @Override
    public void setId(UserAccountLogId id) {
        this.logId = id;
    }

    @PrePersist
    public void validateBeforePersist() {
        if (this.logId == null) {
            throw new RuntimeException("UserAccountLog ID cannot be null");
        }
        this.logId.validate();
    }

    @Embeddable
    @Data
    public static class UserAccountLogId implements SimpliXCompositeKey {
        
        @Column(name = "user_id", nullable = false, length = 36)
        @Comment("사용자 ID: 시스템에서 사용하는 고유 UUID")
        private String userId;
        
        @Column(name = "log_time")
        @Comment("Log Time: Log timestamp")
        private LocalDateTime logTime;
        
        @Override
        public void validate() {
            if (this.userId == null) {
                throw new RuntimeException("UserAccountLog ID's userId cannot be null");
            }

            // If userId exists, set logTime to current time if null
            if (this.logTime == null) {
                this.logTime = LocalDateTime.now();
            }
        }
        
        @Override
        public UserAccountLogId fromPathVariables(String... pathVariables) {
            if (pathVariables.length < 2) {
                throw new IllegalArgumentException("UserAccountLogId requires userId and logTime");
            }
            
            this.userId = pathVariables[0];
            
            DateTimeConverter converter = DateTimeConverter.getDefault();
            LocalDateTime dateTime = converter.fromString(pathVariables[1], LocalDateTime.class);
            this.logTime = dateTime;
            
            validate();
            return this;
        }
        
        @Override
        public UserAccountLogId fromCompositeId(String compositeId) {
            String[] parts = compositeId.split("__");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format. Expected 'userId__logTime' but got: " + compositeId);
            }
            
            return fromPathVariables(parts);
        }
    }
}