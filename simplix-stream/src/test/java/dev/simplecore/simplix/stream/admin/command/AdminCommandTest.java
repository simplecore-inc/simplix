package dev.simplecore.simplix.stream.admin.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AdminCommand entity.
 */
@DisplayName("AdminCommand")
class AdminCommandTest {

    @Nested
    @DisplayName("terminateSession()")
    class TerminateSessionFactory {

        @Test
        @DisplayName("should create terminate session command")
        void shouldCreateTerminateSessionCommand() {
            AdminCommand command = AdminCommand.terminateSession("sess-123");

            assertThat(command.getCommandType()).isEqualTo(AdminCommandType.TERMINATE_SESSION);
            assertThat(command.getTargetId()).isEqualTo("sess-123");
            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.PENDING);
            assertThat(command.getCreatedAt()).isNotNull();
            assertThat(command.getExecutedAt()).isNull();
            assertThat(command.getExecutedBy()).isNull();
            assertThat(command.getErrorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("stopScheduler()")
    class StopSchedulerFactory {

        @Test
        @DisplayName("should create stop scheduler command")
        void shouldCreateStopSchedulerCommand() {
            AdminCommand command = AdminCommand.stopScheduler("stock:abc123");

            assertThat(command.getCommandType()).isEqualTo(AdminCommandType.STOP_SCHEDULER);
            assertThat(command.getTargetId()).isEqualTo("stock:abc123");
            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("triggerScheduler()")
    class TriggerSchedulerFactory {

        @Test
        @DisplayName("should create trigger scheduler command")
        void shouldCreateTriggerSchedulerCommand() {
            AdminCommand command = AdminCommand.triggerScheduler("forex:def456");

            assertThat(command.getCommandType()).isEqualTo(AdminCommandType.TRIGGER_SCHEDULER);
            assertThat(command.getTargetId()).isEqualTo("forex:def456");
            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("markExecuted()")
    class MarkExecuted {

        @Test
        @DisplayName("should update status to EXECUTED with instance info")
        void shouldUpdateStatusToExecuted() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            Instant before = Instant.now();

            command.markExecuted("instance-A");

            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.EXECUTED);
            assertThat(command.getExecutedBy()).isEqualTo("instance-A");
            assertThat(command.getExecutedAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("should update status to FAILED with error message")
        void shouldUpdateStatusToFailed() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");

            command.markFailed("instance-B", "Connection refused");

            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.FAILED);
            assertThat(command.getExecutedBy()).isEqualTo("instance-B");
            assertThat(command.getErrorMessage()).isEqualTo("Connection refused");
            assertThat(command.getExecutedAt()).isNotNull();
        }

        @Test
        @DisplayName("should truncate error message longer than 500 characters")
        void shouldTruncateLongErrorMessage() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            String longMessage = "x".repeat(600);

            command.markFailed("instance-C", longMessage);

            assertThat(command.getErrorMessage()).hasSize(500);
        }

        @Test
        @DisplayName("should handle null error message")
        void shouldHandleNullErrorMessage() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");

            command.markFailed("instance-D", null);

            assertThat(command.getErrorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("markNotFound()")
    class MarkNotFound {

        @Test
        @DisplayName("should update status to NOT_FOUND")
        void shouldUpdateStatusToNotFound() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");

            command.markNotFound("instance-E");

            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.NOT_FOUND);
            assertThat(command.getExecutedBy()).isEqualTo("instance-E");
            assertThat(command.getErrorMessage()).isEqualTo("Target not found on any instance");
            assertThat(command.getExecutedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("markExpired()")
    class MarkExpired {

        @Test
        @DisplayName("should update status to EXPIRED")
        void shouldUpdateStatusToExpired() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");

            command.markExpired();

            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.EXPIRED);
            assertThat(command.getExecutedAt()).isNotNull();
            assertThat(command.getExecutedBy()).isNull();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have PENDING status by default")
        void shouldHavePendingStatusByDefault() {
            AdminCommand command = AdminCommand.builder()
                    .commandType(AdminCommandType.TERMINATE_SESSION)
                    .targetId("sess-1")
                    .build();

            assertThat(command.getStatus()).isEqualTo(AdminCommandStatus.PENDING);
        }

        @Test
        @DisplayName("should have createdAt set by default")
        void shouldHaveCreatedAtSetByDefault() {
            AdminCommand command = AdminCommand.builder()
                    .commandType(AdminCommandType.STOP_SCHEDULER)
                    .targetId("key-1")
                    .build();

            assertThat(command.getCreatedAt()).isNotNull();
        }
    }
}
