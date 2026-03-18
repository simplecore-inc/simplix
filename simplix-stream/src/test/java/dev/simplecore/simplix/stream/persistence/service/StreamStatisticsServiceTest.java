package dev.simplecore.simplix.stream.persistence.service;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.persistence.entity.StreamServerInstanceEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamSessionEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamSubscriptionEntity;
import dev.simplecore.simplix.stream.persistence.repository.StreamServerInstanceRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StreamStatisticsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamStatisticsService")
class StreamStatisticsServiceTest {

    @Mock
    private StreamSessionRepository sessionRepository;

    @Mock
    private StreamSubscriptionRepository subscriptionRepository;

    @Mock
    private StreamServerInstanceRepository serverRepository;

    private StreamStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StreamStatisticsService(sessionRepository, subscriptionRepository, serverRepository);
    }

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("should return aggregated statistics")
        void shouldReturnAggregatedStats() {
            when(sessionRepository.countByState(SessionState.CONNECTED)).thenReturn(10L);
            when(sessionRepository.countByState(SessionState.DISCONNECTED)).thenReturn(2L);
            when(subscriptionRepository.countByActiveTrue()).thenReturn(25L);
            when(serverRepository.countByStatus(StreamServerInstanceEntity.Status.ACTIVE)).thenReturn(3L);
            when(subscriptionRepository.getResourceSubscriptionCounts()).thenReturn(List.of());
            when(serverRepository.findActive()).thenReturn(List.of());

            StreamStatisticsService.StreamStats stats = service.getStats();

            assertThat(stats.getConnectedSessions()).isEqualTo(10);
            assertThat(stats.getDisconnectedSessions()).isEqualTo(2);
            assertThat(stats.getTotalActiveSessions()).isEqualTo(12);
            assertThat(stats.getActiveSubscriptions()).isEqualTo(25);
            assertThat(stats.getActiveServers()).isEqualTo(3);
            assertThat(stats.getTopResources()).isEmpty();
            assertThat(stats.getServerInstances()).isEmpty();
        }

        @Test
        @DisplayName("should include top resources")
        void shouldIncludeTopResources() {
            when(sessionRepository.countByState(SessionState.CONNECTED)).thenReturn(5L);
            when(sessionRepository.countByState(SessionState.DISCONNECTED)).thenReturn(1L);
            when(subscriptionRepository.countByActiveTrue()).thenReturn(10L);
            when(serverRepository.countByStatus(StreamServerInstanceEntity.Status.ACTIVE)).thenReturn(1L);
            when(subscriptionRepository.getResourceSubscriptionCounts())
                    .thenReturn(List.of(
                            new Object[]{"stock", 15L},
                            new Object[]{"weather", 8L}
                    ));
            when(serverRepository.findActive()).thenReturn(List.of());

            StreamStatisticsService.StreamStats stats = service.getStats();

            assertThat(stats.getTopResources()).hasSize(2);
            assertThat(stats.getTopResources().get(0).getResource()).isEqualTo("stock");
            assertThat(stats.getTopResources().get(0).getSubscriptionCount()).isEqualTo(15);
        }

        @Test
        @DisplayName("should include server instances")
        void shouldIncludeServerInstances() {
            Instant now = Instant.now();
            StreamServerInstanceEntity server = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .hostname("server-1")
                    .status(StreamServerInstanceEntity.Status.ACTIVE)
                    .activeSessions(5)
                    .activeSchedulers(3)
                    .startedAt(now)
                    .lastHeartbeatAt(now)
                    .build();

            when(sessionRepository.countByState(SessionState.CONNECTED)).thenReturn(5L);
            when(sessionRepository.countByState(SessionState.DISCONNECTED)).thenReturn(0L);
            when(subscriptionRepository.countByActiveTrue()).thenReturn(10L);
            when(serverRepository.countByStatus(StreamServerInstanceEntity.Status.ACTIVE)).thenReturn(1L);
            when(subscriptionRepository.getResourceSubscriptionCounts()).thenReturn(List.of());
            when(serverRepository.findActive()).thenReturn(List.of(server));

            StreamStatisticsService.StreamStats stats = service.getStats();

            assertThat(stats.getServerInstances()).hasSize(1);
            assertThat(stats.getServerInstances().get(0).getInstanceId()).isEqualTo("inst-1");
            assertThat(stats.getServerInstances().get(0).getHostname()).isEqualTo("server-1");
            assertThat(stats.getServerInstances().get(0).getStatus()).isEqualTo("ACTIVE");
            assertThat(stats.getServerInstances().get(0).getActiveSessions()).isEqualTo(5);
            assertThat(stats.getServerInstances().get(0).getActiveSchedulers()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getSessionStatsByState()")
    class GetSessionStatsByState {

        @Test
        @DisplayName("should return counts by state")
        void shouldReturnCountsByState() {
            when(sessionRepository.countByState(SessionState.CONNECTED)).thenReturn(10L);
            when(sessionRepository.countByState(SessionState.DISCONNECTED)).thenReturn(3L);
            when(sessionRepository.countByState(SessionState.TERMINATED)).thenReturn(50L);

            Map<SessionState, Long> stats = service.getSessionStatsByState();

            assertThat(stats).containsEntry(SessionState.CONNECTED, 10L);
            assertThat(stats).containsEntry(SessionState.DISCONNECTED, 3L);
            assertThat(stats).containsEntry(SessionState.TERMINATED, 50L);
        }
    }

    @Nested
    @DisplayName("getTopResources()")
    class GetTopResources {

        @Test
        @DisplayName("should return resources limited by count")
        void shouldReturnResourcesLimitedByCount() {
            when(subscriptionRepository.getResourceSubscriptionCounts())
                    .thenReturn(List.of(
                            new Object[]{"stock", 20L},
                            new Object[]{"weather", 10L},
                            new Object[]{"news", 5L}
                    ));

            List<StreamStatisticsService.ResourceStats> result = service.getTopResources(2);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getResource()).isEqualTo("stock");
            assertThat(result.get(1).getResource()).isEqualTo("weather");
        }
    }

    @Nested
    @DisplayName("getServerStats()")
    class GetServerStats {

        @Test
        @DisplayName("should return stats for active servers")
        void shouldReturnStatsForActiveServers() {
            Instant now = Instant.now();
            StreamServerInstanceEntity server = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .hostname("host-1")
                    .status(StreamServerInstanceEntity.Status.ACTIVE)
                    .activeSessions(10)
                    .activeSchedulers(5)
                    .startedAt(now)
                    .lastHeartbeatAt(now)
                    .build();
            when(serverRepository.findActive()).thenReturn(List.of(server));

            List<StreamStatisticsService.ServerStats> result = service.getServerStats();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getInstanceId()).isEqualTo("inst-1");
        }
    }

    @Nested
    @DisplayName("getSessions()")
    class GetSessions {

        @Test
        @DisplayName("should return sessions filtered by state")
        void shouldReturnSessionsFilteredByState() {
            Instant now = Instant.now();
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .transportType(TransportType.SSE)
                    .state(SessionState.CONNECTED)
                    .instanceId("inst-1")
                    .connectedAt(now)
                    .lastActiveAt(now)
                    .build();
            when(sessionRepository.findByState(SessionState.CONNECTED)).thenReturn(List.of(entity));
            when(subscriptionRepository.countBySessionIdAndActiveTrue("sess-1")).thenReturn(3L);

            List<StreamStatisticsService.SessionSummary> result =
                    service.getSessions(SessionState.CONNECTED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSessionId()).isEqualTo("sess-1");
            assertThat(result.get(0).getUserId()).isEqualTo("user-1");
            assertThat(result.get(0).getTransportType()).isEqualTo("SSE");
            assertThat(result.get(0).getState()).isEqualTo("CONNECTED");
            assertThat(result.get(0).getSubscriptionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return all sessions when state is null")
        void shouldReturnAllSessionsWhenStateNull() {
            Instant now = Instant.now();
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .transportType(TransportType.SSE)
                    .state(SessionState.CONNECTED)
                    .instanceId("inst-1")
                    .connectedAt(now)
                    .lastActiveAt(now)
                    .build();
            when(sessionRepository.findAll()).thenReturn(List.of(entity));
            when(subscriptionRepository.countBySessionIdAndActiveTrue("sess-1")).thenReturn(0L);

            List<StreamStatisticsService.SessionSummary> result = service.getSessions(null);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getSessionDetails()")
    class GetSessionDetails {

        @Test
        @DisplayName("should return session details with subscriptions")
        void shouldReturnSessionDetailsWithSubscriptions() {
            Instant now = Instant.now();
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .transportType(TransportType.SSE)
                    .state(SessionState.CONNECTED)
                    .instanceId("inst-1")
                    .connectedAt(now)
                    .lastActiveAt(now)
                    .disconnectedAt(null)
                    .clientIp("127.0.0.1")
                    .userAgent("TestAgent")
                    .messagesSent(100L)
                    .bytesSent(5000L)
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            StreamSubscriptionEntity sub = StreamSubscriptionEntity.builder()
                    .subscriptionKey("stock:{\"symbol\":\"AAPL\"}")
                    .resource("stock")
                    .intervalMs(1000L)
                    .subscribedAt(now)
                    .active(true)
                    .build();
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of(sub));

            StreamStatisticsService.SessionDetails details = service.getSessionDetails("sess-1");

            assertThat(details).isNotNull();
            assertThat(details.getSessionId()).isEqualTo("sess-1");
            assertThat(details.getClientIp()).isEqualTo("127.0.0.1");
            assertThat(details.getUserAgent()).isEqualTo("TestAgent");
            assertThat(details.getMessagesSent()).isEqualTo(100L);
            assertThat(details.getBytesSent()).isEqualTo(5000L);
            assertThat(details.getDisconnectedAt()).isNull();
            assertThat(details.getSubscriptions()).hasSize(1);
            assertThat(details.getSubscriptions().get(0).getResource()).isEqualTo("stock");
        }

        @Test
        @DisplayName("should return session details with disconnectedAt")
        void shouldReturnDetailsWithDisconnectedAt() {
            Instant now = Instant.now();
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .transportType(TransportType.SSE)
                    .state(SessionState.DISCONNECTED)
                    .instanceId("inst-1")
                    .connectedAt(now)
                    .lastActiveAt(now)
                    .disconnectedAt(now)
                    .messagesSent(0L)
                    .bytesSent(0L)
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of());

            StreamStatisticsService.SessionDetails details = service.getSessionDetails("sess-1");

            assertThat(details).isNotNull();
            assertThat(details.getDisconnectedAt()).isNotNull();
        }

        @Test
        @DisplayName("should return null when session not found")
        void shouldReturnNullWhenNotFound() {
            when(sessionRepository.findById("nonexistent")).thenReturn(Optional.empty());

            StreamStatisticsService.SessionDetails details = service.getSessionDetails("nonexistent");

            assertThat(details).isNull();
        }
    }

    @Nested
    @DisplayName("getSubscriptionsByResource()")
    class GetSubscriptionsByResource {

        @Test
        @DisplayName("should return subscriptions for a resource")
        void shouldReturnSubscriptionsForResource() {
            Instant now = Instant.now();
            StreamSubscriptionEntity sub = StreamSubscriptionEntity.builder()
                    .subscriptionKey("stock:{\"symbol\":\"AAPL\"}")
                    .resource("stock")
                    .intervalMs(1000L)
                    .subscribedAt(now)
                    .active(true)
                    .build();
            when(subscriptionRepository.findByResourceAndActiveTrue("stock"))
                    .thenReturn(List.of(sub));

            List<StreamStatisticsService.SubscriptionSummary> result =
                    service.getSubscriptionsByResource("stock");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getResource()).isEqualTo("stock");
            assertThat(result.get(0).getIntervalMs()).isEqualTo(1000L);
        }
    }
}
