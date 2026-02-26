package dev.simplecore.simplix.stream.persistence.service;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.persistence.entity.StreamServerInstanceEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamSessionEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamSubscriptionEntity;
import dev.simplecore.simplix.stream.persistence.repository.StreamServerInstanceRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSubscriptionRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for retrieving stream statistics from the database.
 * <p>
 * Provides aggregated statistics about sessions, subscriptions, and
 * server instances across the entire distributed cluster.
 */
@Slf4j
@RequiredArgsConstructor
public class StreamStatisticsService {

    private final StreamSessionRepository sessionRepository;
    private final StreamSubscriptionRepository subscriptionRepository;
    private final StreamServerInstanceRepository serverRepository;

    /**
     * Get overall stream statistics.
     *
     * @return the statistics
     */
    @Transactional(readOnly = true)
    public StreamStats getStats() {
        long connectedSessions = sessionRepository.countByState(SessionState.CONNECTED);
        long disconnectedSessions = sessionRepository.countByState(SessionState.DISCONNECTED);
        long activeSubscriptions = subscriptionRepository.countByActiveTrue();
        long activeServers = serverRepository.countByStatus(StreamServerInstanceEntity.Status.ACTIVE);

        List<ResourceStats> topResources = getTopResources(10);
        List<ServerStats> serverStats = getServerStats();

        return StreamStats.builder()
                .connectedSessions(connectedSessions)
                .disconnectedSessions(disconnectedSessions)
                .totalActiveSessions(connectedSessions + disconnectedSessions)
                .activeSubscriptions(activeSubscriptions)
                .activeServers(activeServers)
                .topResources(topResources)
                .serverInstances(serverStats)
                .build();
    }

    /**
     * Get session statistics by state.
     *
     * @return map of state to count
     */
    @Transactional(readOnly = true)
    public Map<SessionState, Long> getSessionStatsByState() {
        return Map.of(
                SessionState.CONNECTED, sessionRepository.countByState(SessionState.CONNECTED),
                SessionState.DISCONNECTED, sessionRepository.countByState(SessionState.DISCONNECTED),
                SessionState.TERMINATED, sessionRepository.countByState(SessionState.TERMINATED)
        );
    }

    /**
     * Get top resources by subscription count.
     *
     * @param limit maximum number of results
     * @return list of resource stats
     */
    @Transactional(readOnly = true)
    public List<ResourceStats> getTopResources(int limit) {
        return subscriptionRepository.getResourceSubscriptionCounts().stream()
                .limit(limit)
                .map(row -> ResourceStats.builder()
                        .resource((String) row[0])
                        .subscriptionCount((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get server instance statistics.
     *
     * @return list of server stats
     */
    @Transactional(readOnly = true)
    public List<ServerStats> getServerStats() {
        return serverRepository.findActive().stream()
                .map(server -> ServerStats.builder()
                        .instanceId(server.getInstanceId())
                        .hostname(server.getHostname())
                        .status(server.getStatus().name())
                        .activeSessions(server.getActiveSessions())
                        .activeSchedulers(server.getActiveSchedulers())
                        .startedAt(server.getStartedAt().toString())
                        .lastHeartbeatAt(server.getLastHeartbeatAt().toString())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get all sessions (with pagination support).
     *
     * @param state optional state filter
     * @return list of session summaries
     */
    @Transactional(readOnly = true)
    public List<SessionSummary> getSessions(SessionState state) {
        List<StreamSessionEntity> sessions = state != null
                ? sessionRepository.findByState(state)
                : sessionRepository.findAll();

        return sessions.stream()
                .map(this::toSessionSummary)
                .collect(Collectors.toList());
    }

    /**
     * Get session details by ID.
     *
     * @param sessionId the session ID
     * @return the session details if found
     */
    @Transactional(readOnly = true)
    public SessionDetails getSessionDetails(String sessionId) {
        return sessionRepository.findById(sessionId)
                .map(this::toSessionDetails)
                .orElse(null);
    }

    /**
     * Get subscriptions by resource.
     *
     * @param resource the resource name
     * @return list of subscription summaries
     */
    @Transactional(readOnly = true)
    public List<SubscriptionSummary> getSubscriptionsByResource(String resource) {
        return subscriptionRepository.findByResourceAndActiveTrue(resource).stream()
                .map(this::toSubscriptionSummary)
                .collect(Collectors.toList());
    }

    private SessionSummary toSessionSummary(StreamSessionEntity entity) {
        long subscriptionCount = subscriptionRepository.countBySessionIdAndActiveTrue(entity.getId());

        return SessionSummary.builder()
                .sessionId(entity.getId())
                .userId(entity.getUserId())
                .transportType(entity.getTransportType().name())
                .state(entity.getState().name())
                .instanceId(entity.getInstanceId())
                .connectedAt(entity.getConnectedAt().toString())
                .lastActiveAt(entity.getLastActiveAt().toString())
                .subscriptionCount(subscriptionCount)
                .build();
    }

    private SessionDetails toSessionDetails(StreamSessionEntity entity) {
        List<StreamSubscriptionEntity> subscriptions =
                subscriptionRepository.findBySessionIdAndActiveTrue(entity.getId());

        return SessionDetails.builder()
                .sessionId(entity.getId())
                .userId(entity.getUserId())
                .transportType(entity.getTransportType().name())
                .state(entity.getState().name())
                .instanceId(entity.getInstanceId())
                .connectedAt(entity.getConnectedAt().toString())
                .lastActiveAt(entity.getLastActiveAt().toString())
                .disconnectedAt(entity.getDisconnectedAt() != null
                        ? entity.getDisconnectedAt().toString() : null)
                .clientIp(entity.getClientIp())
                .userAgent(entity.getUserAgent())
                .messagesSent(entity.getMessagesSent())
                .bytesSent(entity.getBytesSent())
                .subscriptions(subscriptions.stream()
                        .map(this::toSubscriptionSummary)
                        .collect(Collectors.toList()))
                .build();
    }

    private SubscriptionSummary toSubscriptionSummary(StreamSubscriptionEntity entity) {
        return SubscriptionSummary.builder()
                .subscriptionKey(entity.getSubscriptionKey())
                .resource(entity.getResource())
                .intervalMs(entity.getIntervalMs())
                .subscribedAt(entity.getSubscribedAt().toString())
                .build();
    }

    /**
     * Overall stream statistics.
     */
    @Data
    @Builder
    public static class StreamStats {
        private long connectedSessions;
        private long disconnectedSessions;
        private long totalActiveSessions;
        private long activeSubscriptions;
        private long activeServers;
        private List<ResourceStats> topResources;
        private List<ServerStats> serverInstances;
    }

    /**
     * Resource subscription statistics.
     */
    @Data
    @Builder
    public static class ResourceStats {
        private String resource;
        private long subscriptionCount;
    }

    /**
     * Server instance statistics.
     */
    @Data
    @Builder
    public static class ServerStats {
        private String instanceId;
        private String hostname;
        private String status;
        private int activeSessions;
        private int activeSchedulers;
        private String startedAt;
        private String lastHeartbeatAt;
    }

    /**
     * Session summary for list view.
     */
    @Data
    @Builder
    public static class SessionSummary {
        private String sessionId;
        private String userId;
        private String transportType;
        private String state;
        private String instanceId;
        private String connectedAt;
        private String lastActiveAt;
        private long subscriptionCount;
    }

    /**
     * Session details with subscriptions.
     */
    @Data
    @Builder
    public static class SessionDetails {
        private String sessionId;
        private String userId;
        private String transportType;
        private String state;
        private String instanceId;
        private String connectedAt;
        private String lastActiveAt;
        private String disconnectedAt;
        private String clientIp;
        private String userAgent;
        private Long messagesSent;
        private Long bytesSent;
        private List<SubscriptionSummary> subscriptions;
    }

    /**
     * Subscription summary.
     */
    @Data
    @Builder
    public static class SubscriptionSummary {
        private String subscriptionKey;
        private String resource;
        private long intervalMs;
        private String subscribedAt;
    }
}
