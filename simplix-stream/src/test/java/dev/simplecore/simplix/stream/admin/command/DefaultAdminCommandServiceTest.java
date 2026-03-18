package dev.simplecore.simplix.stream.admin.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultAdminCommandService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminCommandService")
class DefaultAdminCommandServiceTest {

    @Mock
    private AdminCommandRepository commandRepository;

    @Nested
    @DisplayName("queueCommand()")
    class QueueCommand {

        @Test
        @DisplayName("should save command to repository")
        void shouldSaveCommandToRepository() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            AdminCommand saved = AdminCommand.terminateSession("sess-1");
            saved.setId(1L);
            when(commandRepository.save(any())).thenReturn(saved);

            AdminCommand result = service.queueCommand(command);

            assertThat(result.getId()).isEqualTo(1L);
            verify(commandRepository).save(command);
        }
    }

    @Nested
    @DisplayName("queueTerminateSession()")
    class QueueTerminateSession {

        @Test
        @DisplayName("should create and queue terminate session command")
        void shouldCreateAndQueueTerminateSessionCommand() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AdminCommand result = service.queueTerminateSession("sess-123");

            assertThat(result.getCommandType()).isEqualTo(AdminCommandType.TERMINATE_SESSION);
            assertThat(result.getTargetId()).isEqualTo("sess-123");
            verify(commandRepository).save(any(AdminCommand.class));
        }
    }

    @Nested
    @DisplayName("queueStopScheduler()")
    class QueueStopScheduler {

        @Test
        @DisplayName("should create and queue stop scheduler command")
        void shouldCreateAndQueueStopSchedulerCommand() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AdminCommand result = service.queueStopScheduler("stock:abc");

            assertThat(result.getCommandType()).isEqualTo(AdminCommandType.STOP_SCHEDULER);
            assertThat(result.getTargetId()).isEqualTo("stock:abc");
        }
    }

    @Nested
    @DisplayName("queueTriggerScheduler()")
    class QueueTriggerScheduler {

        @Test
        @DisplayName("should create and queue trigger scheduler command")
        void shouldCreateAndQueueTriggerSchedulerCommand() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AdminCommand result = service.queueTriggerScheduler("forex:def");

            assertThat(result.getCommandType()).isEqualTo(AdminCommandType.TRIGGER_SCHEDULER);
            assertThat(result.getTargetId()).isEqualTo("forex:def");
        }
    }

    @Nested
    @DisplayName("getCommand()")
    class GetCommand {

        @Test
        @DisplayName("should return command from repository")
        void shouldReturnCommandFromRepository() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(42L);
            when(commandRepository.findById(42L)).thenReturn(Optional.of(command));

            Optional<AdminCommand> result = service.getCommand(42L);

            assertThat(result).isPresent();
            assertThat(result.get().getTargetId()).isEqualTo("sess-1");
        }

        @Test
        @DisplayName("should return empty when command not found")
        void shouldReturnEmptyWhenNotFound() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            when(commandRepository.findById(99L)).thenReturn(Optional.empty());

            Optional<AdminCommand> result = service.getCommand(99L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCommandsForTarget()")
    class GetCommandsForTarget {

        @Test
        @DisplayName("should return commands for target")
        void shouldReturnCommandsForTarget() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            List<AdminCommand> commands = List.of(
                    AdminCommand.terminateSession("sess-1"),
                    AdminCommand.terminateSession("sess-1")
            );
            when(commandRepository.findByTargetIdOrderByCreatedAtDesc("sess-1")).thenReturn(commands);

            List<AdminCommand> result = service.getCommandsForTarget("sess-1");

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getPendingCommands()")
    class GetPendingCommands {

        @Test
        @DisplayName("should return pending commands")
        void shouldReturnPendingCommands() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);
            List<AdminCommand> pending = List.of(AdminCommand.terminateSession("sess-1"));
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(pending);

            List<AdminCommand> result = service.getPendingCommands();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("isEnabled()")
    class IsEnabled {

        @Test
        @DisplayName("should return true when enabled")
        void shouldReturnTrueWhenEnabled() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, true);

            assertThat(service.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return false when disabled")
        void shouldReturnFalseWhenDisabled() {
            DefaultAdminCommandService service = new DefaultAdminCommandService(commandRepository, false);

            assertThat(service.isEnabled()).isFalse();
        }
    }
}
