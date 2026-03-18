package dev.simplecore.simplix.stream.admin.command;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.scheduler.SubscriptionScheduler;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminCommandProcessor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCommandProcessor")
class AdminCommandProcessorTest {

    @Mock
    private AdminCommandRepository commandRepository;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SchedulerManager schedulerManager;

    @Mock
    private StreamSession mockSession;

    @Mock
    private SubscriptionScheduler mockScheduler;

    private StreamProperties properties;
    private AdminCommandProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.getAdmin().setCommandTimeout(Duration.ofMinutes(5));
        properties.getAdmin().setRetentionPeriod(Duration.ofDays(7));
        processor = new AdminCommandProcessor(
                commandRepository, sessionManager, sessionRegistry,
                schedulerManager, properties, "instance-1"
        );
    }

    @Nested
    @DisplayName("processCommands()")
    class ProcessCommands {

        @Test
        @DisplayName("should do nothing when no pending commands")
        void shouldDoNothingWhenNoPendingCommands() {
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of());

            processor.processCommands();

            verify(commandRepository).findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING);
            verifyNoMoreInteractions(commandRepository);
            verifyNoInteractions(sessionManager, sessionRegistry, schedulerManager);
        }

        @Test
        @DisplayName("should expire old commands")
        void shouldExpireOldCommands() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setCreatedAt(Instant.now().minus(Duration.ofMinutes(10)));
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));

            processor.processCommands();

            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.EXPIRED;
        }

        @Test
        @DisplayName("should execute terminate session command when session is local")
        void shouldExecuteTerminateSessionWhenLocal() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(1L);
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));
            when(sessionRegistry.findById("sess-1")).thenReturn(Optional.of(mockSession));

            processor.processCommands();

            verify(sessionManager).terminateSession("sess-1");
            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.EXECUTED;
        }

        @Test
        @DisplayName("should mark terminate session as NOT_FOUND when session not in registry")
        void shouldMarkNotFoundWhenSessionNotInRegistry() {
            AdminCommand command = AdminCommand.terminateSession("sess-missing");
            command.setId(2L);
            // First call: isMyTarget check finds session
            // Second call: executeTerminateSession check does not find session
            when(sessionRegistry.findById("sess-missing"))
                    .thenReturn(Optional.of(mockSession))
                    .thenReturn(Optional.empty());
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));

            processor.processCommands();

            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.NOT_FOUND;
        }

        @Test
        @DisplayName("should execute stop scheduler command when scheduler is local")
        void shouldExecuteStopSchedulerWhenLocal() {
            AdminCommand command = AdminCommand.stopScheduler("stock:abc");
            command.setId(3L);
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));
            when(schedulerManager.getScheduler("stock:abc")).thenReturn(Optional.of(mockScheduler));

            processor.processCommands();

            verify(schedulerManager).stopScheduler("stock:abc");
            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.EXECUTED;
        }

        @Test
        @DisplayName("should execute trigger scheduler command when scheduler is local")
        void shouldExecuteTriggerSchedulerWhenLocal() {
            AdminCommand command = AdminCommand.triggerScheduler("forex:def");
            command.setId(4L);
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));
            when(schedulerManager.getScheduler("forex:def")).thenReturn(Optional.of(mockScheduler));

            processor.processCommands();

            verify(schedulerManager).triggerNow("forex:def");
            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.EXECUTED;
        }

        @Test
        @DisplayName("should skip commands targeted at other instances")
        void shouldSkipCommandsForOtherInstances() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setTargetInstanceId("instance-2");
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));

            processor.processCommands();

            verifyNoInteractions(sessionManager);
            verify(commandRepository, never()).save(any());
        }

        @Test
        @DisplayName("should execute commands targeted at this instance")
        void shouldExecuteCommandsForThisInstance() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(5L);
            command.setTargetInstanceId("instance-1");
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));
            when(sessionRegistry.findById("sess-1")).thenReturn(Optional.of(mockSession));

            processor.processCommands();

            verify(sessionManager).terminateSession("sess-1");
        }

        @Test
        @DisplayName("should mark command as FAILED on exception")
        void shouldMarkFailedOnException() {
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(6L);
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));
            when(sessionRegistry.findById("sess-1")).thenReturn(Optional.of(mockSession));
            doThrow(new RuntimeException("unexpected")).when(sessionManager).terminateSession("sess-1");

            processor.processCommands();

            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.FAILED;
        }

        @Test
        @DisplayName("should skip commands where target is not local")
        void shouldSkipCommandsWhereTargetNotLocal() {
            AdminCommand command = AdminCommand.terminateSession("sess-remote");
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));
            when(sessionRegistry.findById("sess-remote")).thenReturn(Optional.empty());

            processor.processCommands();

            verifyNoInteractions(sessionManager);
            verify(commandRepository, never()).save(any());
        }

        @Test
        @DisplayName("should mark stop scheduler as NOT_FOUND when scheduler not in registry")
        void shouldMarkStopSchedulerNotFoundWhenNotInRegistry() {
            AdminCommand command = AdminCommand.stopScheduler("stock:missing");
            command.setId(10L);
            // isMyTarget: first call returns present, executeStopScheduler: second returns empty
            when(schedulerManager.getScheduler("stock:missing"))
                    .thenReturn(Optional.of(mockScheduler))
                    .thenReturn(Optional.empty());
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));

            processor.processCommands();

            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.NOT_FOUND;
        }

        @Test
        @DisplayName("should mark trigger scheduler as NOT_FOUND when scheduler not in registry")
        void shouldMarkTriggerSchedulerNotFoundWhenNotInRegistry() {
            AdminCommand command = AdminCommand.triggerScheduler("forex:missing");
            command.setId(11L);
            // isMyTarget: first call returns present, executeTriggerScheduler: second returns empty
            when(schedulerManager.getScheduler("forex:missing"))
                    .thenReturn(Optional.of(mockScheduler))
                    .thenReturn(Optional.empty());
            when(commandRepository.findByStatusOrderByCreatedAtAsc(AdminCommandStatus.PENDING))
                    .thenReturn(List.of(command));

            processor.processCommands();

            verify(commandRepository).save(command);
            assert command.getStatus() == AdminCommandStatus.NOT_FOUND;
        }
    }

    @Nested
    @DisplayName("cleanupOldCommands()")
    class CleanupOldCommands {

        @Test
        @DisplayName("should delete old commands with terminal statuses")
        void shouldDeleteOldCommandsWithTerminalStatuses() {
            when(commandRepository.deleteOldCommands(anyList(), any(Instant.class))).thenReturn(5);

            processor.cleanupOldCommands();

            verify(commandRepository).deleteOldCommands(anyList(), any(Instant.class));
        }

        @Test
        @DisplayName("should not log when no commands deleted")
        void shouldNotLogWhenNoCommandsDeleted() {
            when(commandRepository.deleteOldCommands(anyList(), any(Instant.class))).thenReturn(0);

            processor.cleanupOldCommands();

            verify(commandRepository).deleteOldCommands(anyList(), any(Instant.class));
        }
    }
}
