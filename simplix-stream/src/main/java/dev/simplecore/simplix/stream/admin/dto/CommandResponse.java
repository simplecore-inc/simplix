package dev.simplecore.simplix.stream.admin.dto;

import dev.simplecore.simplix.stream.admin.command.AdminCommand;
import dev.simplecore.simplix.stream.admin.command.AdminCommandStatus;
import dev.simplecore.simplix.stream.admin.command.AdminCommandType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for admin command operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResponse {

    /**
     * Command ID for tracking
     */
    private Long commandId;

    /**
     * Type of command
     */
    private AdminCommandType commandType;

    /**
     * Target identifier
     */
    private String targetId;

    /**
     * Current status
     */
    private AdminCommandStatus status;

    /**
     * When the command was created
     */
    private Instant createdAt;

    /**
     * When the command was executed (if applicable)
     */
    private Instant executedAt;

    /**
     * Instance that executed the command (if applicable)
     */
    private String executedBy;

    /**
     * Error message if failed
     */
    private String errorMessage;

    /**
     * Message for the client
     */
    private String message;

    /**
     * Create a response for a queued command.
     *
     * @param command the queued command
     * @return the response
     */
    public static CommandResponse queued(AdminCommand command) {
        return CommandResponse.builder()
                .commandId(command.getId())
                .commandType(command.getCommandType())
                .targetId(command.getTargetId())
                .status(command.getStatus())
                .createdAt(command.getCreatedAt())
                .message("Command queued for execution")
                .build();
    }

    /**
     * Create a response from an existing command.
     *
     * @param command the command
     * @return the response
     */
    public static CommandResponse from(AdminCommand command) {
        return CommandResponse.builder()
                .commandId(command.getId())
                .commandType(command.getCommandType())
                .targetId(command.getTargetId())
                .status(command.getStatus())
                .createdAt(command.getCreatedAt())
                .executedAt(command.getExecutedAt())
                .executedBy(command.getExecutedBy())
                .errorMessage(command.getErrorMessage())
                .message(getStatusMessage(command.getStatus()))
                .build();
    }

    private static String getStatusMessage(AdminCommandStatus status) {
        return switch (status) {
            case PENDING -> "Command is waiting for execution";
            case EXECUTED -> "Command executed successfully";
            case FAILED -> "Command execution failed";
            case EXPIRED -> "Command expired without execution";
            case NOT_FOUND -> "Target not found on any instance";
        };
    }
}
