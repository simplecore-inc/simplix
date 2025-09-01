package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.core.convert.datetime.DateTimeConverter;
import dev.simplecore.simplix.core.entity.SimpliXCompositeKey;
import dev.simplecore.simplix.demo.domain.BaseEntity;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@Table(name = "user_account_log")
@Comment("User Account Log - Change history and audit trail for user account modifications")
public class UserAccountLog extends BaseEntity<UserAccountLog.UserAccountLogId> {

    @EmbeddedId
    private UserAccountLogId logId;

    @Column(name = "log_message")
    @Comment("Log Message - Change details")
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
        @Comment("User ID - Unique UUID used in the system")
        private String userId;
        
        @Column(name = "log_time")
        @Comment("Log Time - Log timestamp")
        private OffsetDateTime logTime;
        
        @Override
        public void validate() {
            if (this.userId == null) {
                throw new RuntimeException("UserAccountLog ID's userId cannot be null");
            }

            // If userId exists, set logTime to current time if null
            if (this.logTime == null) {
                this.logTime = OffsetDateTime.now();
            }
        }
        
        @Override
        public UserAccountLogId fromPathVariables(String... pathVariables) {
            if (pathVariables.length < 2) {
                throw new IllegalArgumentException("UserAccountLogId requires userId and logTime");
            }
            
            this.userId = pathVariables[0];
            
            DateTimeConverter converter = DateTimeConverter.getDefault();
            OffsetDateTime dateTime = converter.fromString(pathVariables[1], OffsetDateTime.class);
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