package dev.simplecore.simplix.stream.admin.dto;

import dev.simplecore.simplix.stream.admin.command.AdminCommand;
import dev.simplecore.simplix.stream.admin.command.AdminCommandStatus;
import dev.simplecore.simplix.stream.admin.command.AdminCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CommandResponse DTO.
 */
@DisplayName("CommandResponse")
class CommandResponseTest {

    @Nested
    @DisplayName("queued()")
    class QueuedMethod {

        @Test
        @DisplayName("should create response from queued command")
        void shouldCreateResponseFromQueuedCommand() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(42L);

            CommandResponse response = CommandResponse.queued(command);

            assertThat(response.getCommandId()).isEqualTo(42L);
            assertThat(response.getCommandType()).isEqualTo(AdminCommandType.TERMINATE_SESSION);
            assertThat(response.getTargetId()).isEqualTo("sess-1");
            assertThat(response.getStatus()).isEqualTo(AdminCommandStatus.PENDING);
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Command queued for execution");
            assertThat(response.getExecutedAt()).isNull();
            assertThat(response.getExecutedBy()).isNull();
        }
    }

    @Nested
    @DisplayName("from()")
    class FromMethod {

        @Test
        @DisplayName("should create response from executed command")
        void shouldCreateResponseFromExecutedCommand() {
            AdminCommand command = AdminCommand.stopScheduler("stock:abc");
            command.setId(10L);
            command.markExecuted("instance-A");

            CommandResponse response = CommandResponse.from(command);

            assertThat(response.getCommandId()).isEqualTo(10L);
            assertThat(response.getCommandType()).isEqualTo(AdminCommandType.STOP_SCHEDULER);
            assertThat(response.getTargetId()).isEqualTo("stock:abc");
            assertThat(response.getStatus()).isEqualTo(AdminCommandStatus.EXECUTED);
            assertThat(response.getExecutedBy()).isEqualTo("instance-A");
            assertThat(response.getExecutedAt()).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Command executed successfully");
        }

        @Test
        @DisplayName("should set correct status message for PENDING")
        void shouldSetCorrectMessageForPending() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(1L);

            CommandResponse response = CommandResponse.from(command);

            assertThat(response.getMessage()).isEqualTo("Command is waiting for execution");
        }

        @Test
        @DisplayName("should set correct status message for FAILED")
        void shouldSetCorrectMessageForFailed() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(2L);
            command.markFailed("inst-1", "Error occurred");

            CommandResponse response = CommandResponse.from(command);

            assertThat(response.getMessage()).isEqualTo("Command execution failed");
            assertThat(response.getErrorMessage()).isEqualTo("Error occurred");
        }

        @Test
        @DisplayName("should set correct status message for EXPIRED")
        void shouldSetCorrectMessageForExpired() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(3L);
            command.markExpired();

            CommandResponse response = CommandResponse.from(command);

            assertThat(response.getMessage()).isEqualTo("Command expired without execution");
        }

        @Test
        @DisplayName("should set correct status message for NOT_FOUND")
        void shouldSetCorrectMessageForNotFound() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(4L);
            command.markNotFound("inst-1");

            CommandResponse response = CommandResponse.from(command);

            assertThat(response.getMessage()).isEqualTo("Target not found on any instance");
        }
    }

    @Nested
    @DisplayName("builder()")
    class BuilderMethod {

        @Test
        @DisplayName("should build response with all fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();
            CommandResponse response = CommandResponse.builder()
                    .commandId(99L)
                    .commandType(AdminCommandType.TRIGGER_SCHEDULER)
                    .targetId("forex:xyz")
                    .status(AdminCommandStatus.EXECUTED)
                    .createdAt(now)
                    .executedAt(now)
                    .executedBy("inst-1")
                    .errorMessage(null)
                    .message("done")
                    .build();

            assertThat(response.getCommandId()).isEqualTo(99L);
            assertThat(response.getCommandType()).isEqualTo(AdminCommandType.TRIGGER_SCHEDULER);
            assertThat(response.getTargetId()).isEqualTo("forex:xyz");
            assertThat(response.getStatus()).isEqualTo(AdminCommandStatus.EXECUTED);
            assertThat(response.getMessage()).isEqualTo("done");
        }
    }
}
