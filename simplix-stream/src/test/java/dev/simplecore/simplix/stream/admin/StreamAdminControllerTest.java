package dev.simplecore.simplix.stream.admin;

import dev.simplecore.simplix.stream.admin.command.AdminCommand;
import dev.simplecore.simplix.stream.admin.command.AdminCommandService;
import dev.simplecore.simplix.stream.admin.command.AdminCommandStatus;
import dev.simplecore.simplix.stream.admin.dto.CommandResponse;
import dev.simplecore.simplix.stream.admin.dto.SchedulerInfo;
import dev.simplecore.simplix.stream.admin.dto.SessionInfo;
import dev.simplecore.simplix.stream.admin.dto.StreamStats;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.scheduler.SubscriptionScheduler;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.persistence.service.StreamStatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StreamAdminController.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamAdminController")
class StreamAdminControllerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SchedulerManager schedulerManager;

    @Mock
    private BroadcastService broadcastService;

    @Mock
    private AdminCommandService commandService;

    @Mock
    private StreamStatisticsService statisticsService;

    private StreamProperties properties;
    private StreamAdminController controller;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
    }

    private StreamAdminController createController(boolean withCommandService, boolean withStatsService) {
        return new StreamAdminController(
                sessionManager,
                sessionRegistry,
                schedulerManager,
                broadcastService,
                properties,
                withCommandService ? Optional.of(commandService) : Optional.empty(),
                withStatsService ? Optional.of(statisticsService) : Optional.empty()
        );
    }

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("should return stream statistics")
        void shouldReturnStreamStatistics() {
            controller = createController(false, false);
            when(sessionRegistry.count()).thenReturn(5L);
            when(schedulerManager.getSchedulerCount()).thenReturn(3);
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionManager.getAllSessions()).thenReturn(List.of());

            ResponseEntity<StreamStats> response = controller.getStats();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            StreamStats stats = response.getBody();
            assertThat(stats).isNotNull();
            assertThat(stats.getActiveSessions()).isEqualTo(5);
            assertThat(stats.getActiveSchedulers()).isEqualTo(3);
            assertThat(stats.isSessionRegistryAvailable()).isTrue();
            assertThat(stats.isBroadcastServiceAvailable()).isTrue();
            assertThat(stats.getMode()).isEqualTo("LOCAL");
            assertThat(stats.isDistributedAdminEnabled()).isFalse();
        }

        @Test
        @DisplayName("should calculate total subscriptions from sessions")
        void shouldCalculateTotalSubscriptions() {
            controller = createController(false, false);
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            session.addSubscription(SubscriptionKey.of("stock", Map.of("symbol", "AAPL")));
            session.addSubscription(SubscriptionKey.of("forex", Map.of("pair", "EUR/USD")));

            when(sessionRegistry.count()).thenReturn(1L);
            when(schedulerManager.getSchedulerCount()).thenReturn(2);
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionManager.getAllSessions()).thenReturn(List.of(session));

            ResponseEntity<StreamStats> response = controller.getStats();

            assertThat(response.getBody().getTotalSubscriptions()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getGlobalStats()")
    class GetGlobalStats {

        @Test
        @DisplayName("should return 404 when statistics service not available")
        void shouldReturn404WhenStatisticsServiceNotAvailable() {
            controller = createController(false, false);

            ResponseEntity<StreamStatisticsService.StreamStats> response = controller.getGlobalStats();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return global stats when service is available")
        void shouldReturnGlobalStatsWhenServiceAvailable() {
            controller = createController(false, true);
            StreamStatisticsService.StreamStats stats = mock(StreamStatisticsService.StreamStats.class);
            when(statisticsService.getStats()).thenReturn(stats);

            ResponseEntity<StreamStatisticsService.StreamStats> response = controller.getGlobalStats();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(stats);
        }
    }

    @Nested
    @DisplayName("getGlobalSessions()")
    class GetGlobalSessions {

        @Test
        @DisplayName("should return 404 when statistics service not available")
        void shouldReturn404WhenStatisticsServiceNotAvailable() {
            controller = createController(false, false);

            var response = controller.getGlobalSessions(null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return global sessions with state filter")
        void shouldReturnGlobalSessionsWithStateFilter() {
            controller = createController(false, true);
            when(statisticsService.getSessions(SessionState.CONNECTED)).thenReturn(List.of());

            var response = controller.getGlobalSessions(SessionState.CONNECTED);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(statisticsService).getSessions(SessionState.CONNECTED);
        }
    }

    @Nested
    @DisplayName("getGlobalSessionDetails()")
    class GetGlobalSessionDetails {

        @Test
        @DisplayName("should return 404 when statistics service not available")
        void shouldReturn404WhenServiceNotAvailable() {
            controller = createController(false, false);

            var response = controller.getGlobalSessionDetails("sess-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when session details not found")
        void shouldReturn404WhenSessionNotFound() {
            controller = createController(false, true);
            when(statisticsService.getSessionDetails("sess-1")).thenReturn(null);

            var response = controller.getGlobalSessionDetails("sess-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return session details when found")
        void shouldReturnSessionDetailsWhenFound() {
            controller = createController(false, true);
            StreamStatisticsService.SessionDetails details = mock(StreamStatisticsService.SessionDetails.class);
            when(statisticsService.getSessionDetails("sess-1")).thenReturn(details);

            var response = controller.getGlobalSessionDetails("sess-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(details);
        }
    }

    @Nested
    @DisplayName("getSubscriptionsByResource()")
    class GetSubscriptionsByResource {

        @Test
        @DisplayName("should return 404 when statistics service not available")
        void shouldReturn404WhenServiceNotAvailable() {
            controller = createController(false, false);

            var response = controller.getSubscriptionsByResource("stock");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return subscriptions by resource")
        void shouldReturnSubscriptionsByResource() {
            controller = createController(false, true);
            when(statisticsService.getSubscriptionsByResource("stock")).thenReturn(List.of());

            var response = controller.getSubscriptionsByResource("stock");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("getServers()")
    class GetServers {

        @Test
        @DisplayName("should return 404 when statistics service not available")
        void shouldReturn404WhenServiceNotAvailable() {
            controller = createController(false, false);

            var response = controller.getServers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return server stats")
        void shouldReturnServerStats() {
            controller = createController(false, true);
            when(statisticsService.getServerStats()).thenReturn(List.of());

            var response = controller.getServers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("getSessions()")
    class GetSessions {

        @Test
        @DisplayName("should return all sessions as SessionInfo list")
        void shouldReturnAllSessions() {
            controller = createController(false, false);
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            when(sessionManager.getAllSessions()).thenReturn(List.of(session));

            ResponseEntity<List<SessionInfo>> response = controller.getSessions();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("should return empty list when no sessions")
        void shouldReturnEmptyListWhenNoSessions() {
            controller = createController(false, false);
            when(sessionManager.getAllSessions()).thenReturn(List.of());

            ResponseEntity<List<SessionInfo>> response = controller.getSessions();

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSession()")
    class GetSession {

        @Test
        @DisplayName("should return session info when found")
        void shouldReturnSessionInfoWhenFound() {
            controller = createController(false, false);
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            when(sessionManager.findSession(session.getId())).thenReturn(Optional.of(session));

            ResponseEntity<SessionInfo> response = controller.getSession(session.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("should return 404 when session not found")
        void shouldReturn404WhenSessionNotFound() {
            controller = createController(false, false);
            when(sessionManager.findSession("non-existent")).thenReturn(Optional.empty());

            ResponseEntity<SessionInfo> response = controller.getSession("non-existent");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getSessionsByUser()")
    class GetSessionsByUser {

        @Test
        @DisplayName("should return sessions for a user")
        void shouldReturnSessionsForUser() {
            controller = createController(false, false);
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            when(sessionManager.getSessionsByUser("user-1")).thenReturn(List.of(session));

            ResponseEntity<List<SessionInfo>> response = controller.getSessionsByUser("user-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("terminateSession()")
    class TerminateSession {

        @Test
        @DisplayName("should terminate session directly in local mode")
        void shouldTerminateDirectlyInLocalMode() {
            controller = createController(false, false);

            ResponseEntity<?> response = controller.terminateSession("sess-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(sessionManager).terminateSession("sess-1");
        }

        @Test
        @DisplayName("should queue command in distributed mode")
        void shouldQueueCommandInDistributedMode() {
            controller = createController(true, false);
            when(commandService.isEnabled()).thenReturn(true);
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(1L);
            when(commandService.queueTerminateSession("sess-1")).thenReturn(command);

            ResponseEntity<?> response = controller.terminateSession("sess-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody()).isInstanceOf(CommandResponse.class);
            verify(sessionManager, never()).terminateSession(anyString());
        }
    }

    @Nested
    @DisplayName("getSchedulers()")
    class GetSchedulers {

        @Test
        @DisplayName("should return all schedulers")
        void shouldReturnAllSchedulers() {
            controller = createController(false, false);
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionScheduler scheduler = new SubscriptionScheduler(key, Duration.ofSeconds(5));
            when(schedulerManager.getAllSchedulers()).thenReturn(List.of(scheduler));

            ResponseEntity<List<SchedulerInfo>> response = controller.getSchedulers();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getScheduler()")
    class GetScheduler {

        @Test
        @DisplayName("should return scheduler when found")
        void shouldReturnSchedulerWhenFound() {
            controller = createController(false, false);
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionScheduler scheduler = new SubscriptionScheduler(key, Duration.ofSeconds(5));
            when(schedulerManager.getScheduler("stock:abc")).thenReturn(Optional.of(scheduler));

            ResponseEntity<SchedulerInfo> response = controller.getScheduler("stock:abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return 404 when scheduler not found")
        void shouldReturn404WhenSchedulerNotFound() {
            controller = createController(false, false);
            when(schedulerManager.getScheduler("nonexistent")).thenReturn(Optional.empty());

            ResponseEntity<SchedulerInfo> response = controller.getScheduler("nonexistent");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("stopScheduler()")
    class StopScheduler {

        @Test
        @DisplayName("should stop scheduler directly in local mode")
        void shouldStopDirectlyInLocalMode() {
            controller = createController(false, false);

            ResponseEntity<?> response = controller.stopScheduler("stock:abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(schedulerManager).stopScheduler("stock:abc");
        }

        @Test
        @DisplayName("should queue command in distributed mode")
        void shouldQueueCommandInDistributedMode() {
            controller = createController(true, false);
            when(commandService.isEnabled()).thenReturn(true);
            AdminCommand command = AdminCommand.stopScheduler("stock:abc");
            command.setId(2L);
            when(commandService.queueStopScheduler("stock:abc")).thenReturn(command);

            ResponseEntity<?> response = controller.stopScheduler("stock:abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("triggerScheduler()")
    class TriggerScheduler {

        @Test
        @DisplayName("should trigger scheduler directly in local mode")
        void shouldTriggerDirectlyInLocalMode() {
            controller = createController(false, false);

            ResponseEntity<?> response = controller.triggerScheduler("stock:abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(schedulerManager).triggerNow("stock:abc");
        }

        @Test
        @DisplayName("should queue command in distributed mode")
        void shouldQueueCommandInDistributedMode() {
            controller = createController(true, false);
            when(commandService.isEnabled()).thenReturn(true);
            AdminCommand command = AdminCommand.triggerScheduler("stock:abc");
            command.setId(3L);
            when(commandService.queueTriggerScheduler("stock:abc")).thenReturn(command);

            ResponseEntity<?> response = controller.triggerScheduler("stock:abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("getCommandStatus()")
    class GetCommandStatus {

        @Test
        @DisplayName("should return 404 when distributed admin not enabled")
        void shouldReturn404WhenNotEnabled() {
            controller = createController(false, false);

            ResponseEntity<CommandResponse> response = controller.getCommandStatus(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return command status when found")
        void shouldReturnCommandStatusWhenFound() {
            controller = createController(true, false);
            when(commandService.isEnabled()).thenReturn(true);
            AdminCommand command = AdminCommand.terminateSession("sess-1");
            command.setId(1L);
            command.markExecuted("inst-1");
            when(commandService.getCommand(1L)).thenReturn(Optional.of(command));

            ResponseEntity<CommandResponse> response = controller.getCommandStatus(1L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(AdminCommandStatus.EXECUTED);
        }

        @Test
        @DisplayName("should return 404 when command not found")
        void shouldReturn404WhenCommandNotFound() {
            controller = createController(true, false);
            when(commandService.isEnabled()).thenReturn(true);
            when(commandService.getCommand(99L)).thenReturn(Optional.empty());

            ResponseEntity<CommandResponse> response = controller.getCommandStatus(99L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getPendingCommands()")
    class GetPendingCommands {

        @Test
        @DisplayName("should return empty list when distributed admin not enabled")
        void shouldReturnEmptyListWhenNotEnabled() {
            controller = createController(false, false);

            ResponseEntity<List<CommandResponse>> response = controller.getPendingCommands();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should return pending commands")
        void shouldReturnPendingCommands() {
            controller = createController(true, false);
            when(commandService.isEnabled()).thenReturn(true);
            AdminCommand cmd = AdminCommand.terminateSession("sess-1");
            cmd.setId(1L);
            when(commandService.getPendingCommands()).thenReturn(List.of(cmd));

            ResponseEntity<List<CommandResponse>> response = controller.getPendingCommands();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("setInstanceId()")
    class SetInstanceId {

        @Test
        @DisplayName("should update instance ID in stats")
        void shouldUpdateInstanceId() {
            controller = createController(false, false);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(0);
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionManager.getAllSessions()).thenReturn(List.of());

            controller.setInstanceId("custom-instance");
            ResponseEntity<StreamStats> response = controller.getStats();

            assertThat(response.getBody().getInstanceId()).isEqualTo("custom-instance");
        }
    }
}
