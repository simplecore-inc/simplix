package dev.simplecore.simplix.stream.admin.command;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity representing an admin command queued for distributed execution.
 * <p>
 * Commands are inserted by the Admin API and processed by each instance
 * through polling. Each instance checks if it owns the target resource
 * and executes the command if applicable.
 */
@Entity
@Table(name = "stream_admin_commands",
        indexes = {
                @Index(name = "idx_stream_admin_cmd_status", columnList = "status, createdAt"),
                @Index(name = "idx_stream_admin_cmd_target", columnList = "targetId")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of command to execute
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminCommandType commandType;

    /**
     * Target identifier (session ID or subscription key)
     */
    @Column(nullable = false, length = 255)
    private String targetId;

    /**
     * Optional: specific instance ID that should execute this command.
     * If null, any instance that owns the target will execute.
     */
    @Column(length = 64)
    private String targetInstanceId;

    /**
     * Current status of the command
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AdminCommandStatus status = AdminCommandStatus.PENDING;

    /**
     * When the command was created
     */
    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * When the command was executed (if executed)
     */
    private Instant executedAt;

    /**
     * Instance ID that executed the command
     */
    @Column(length = 64)
    private String executedBy;

    /**
     * Error message if execution failed
     */
    @Column(length = 500)
    private String errorMessage;

    /**
     * Create a terminate session command.
     *
     * @param sessionId the session to terminate
     * @return the command
     */
    public static AdminCommand terminateSession(String sessionId) {
        return AdminCommand.builder()
                .commandType(AdminCommandType.TERMINATE_SESSION)
                .targetId(sessionId)
                .build();
    }

    /**
     * Create a stop scheduler command.
     *
     * @param subscriptionKey the scheduler to stop
     * @return the command
     */
    public static AdminCommand stopScheduler(String subscriptionKey) {
        return AdminCommand.builder()
                .commandType(AdminCommandType.STOP_SCHEDULER)
                .targetId(subscriptionKey)
                .build();
    }

    /**
     * Create a trigger scheduler command.
     *
     * @param subscriptionKey the scheduler to trigger
     * @return the command
     */
    public static AdminCommand triggerScheduler(String subscriptionKey) {
        return AdminCommand.builder()
                .commandType(AdminCommandType.TRIGGER_SCHEDULER)
                .targetId(subscriptionKey)
                .build();
    }

    /**
     * Mark the command as executed.
     *
     * @param instanceId the instance that executed the command
     */
    public void markExecuted(String instanceId) {
        this.status = AdminCommandStatus.EXECUTED;
        this.executedAt = Instant.now();
        this.executedBy = instanceId;
    }

    /**
     * Mark the command as failed.
     *
     * @param instanceId the instance that attempted execution
     * @param error      the error message
     */
    public void markFailed(String instanceId, String error) {
        this.status = AdminCommandStatus.FAILED;
        this.executedAt = Instant.now();
        this.executedBy = instanceId;
        this.errorMessage = error != null && error.length() > 500
                ? error.substring(0, 500)
                : error;
    }

    /**
     * Mark the command as target not found.
     *
     * @param instanceId the instance that checked
     */
    public void markNotFound(String instanceId) {
        this.status = AdminCommandStatus.NOT_FOUND;
        this.executedAt = Instant.now();
        this.executedBy = instanceId;
        this.errorMessage = "Target not found on any instance";
    }

    /**
     * Mark the command as expired.
     */
    public void markExpired() {
        this.status = AdminCommandStatus.EXPIRED;
        this.executedAt = Instant.now();
    }
}
