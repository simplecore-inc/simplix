package dev.simplecore.simplix.stream.admin;

import dev.simplecore.simplix.stream.admin.command.AdminCommand;
import dev.simplecore.simplix.stream.admin.command.AdminCommandService;
import dev.simplecore.simplix.stream.admin.dto.CommandResponse;
import dev.simplecore.simplix.stream.admin.dto.SchedulerInfo;
import dev.simplecore.simplix.stream.admin.dto.SessionInfo;
import dev.simplecore.simplix.stream.admin.dto.StreamStats;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.scheduler.SubscriptionScheduler;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.persistence.service.StreamStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin REST controller for stream management.
 * <p>
 * Provides endpoints for monitoring and managing stream sessions,
 * schedulers, and overall system state.
 * <p>
 * In distributed mode with admin.enabled=true, control operations
 * (terminate session, stop scheduler, trigger scheduler) are queued
 * to the database and executed asynchronously by the instance that
 * owns the target resource.
 */
@Slf4j
@RestController
@RequestMapping("/api/stream/admin")
public class StreamAdminController {

    private final SessionManager sessionManager;
    private final SessionRegistry sessionRegistry;
    private final SchedulerManager schedulerManager;
    private final BroadcastService broadcastService;
    private final StreamProperties properties;
    private final AdminCommandService commandService;
    private final StreamStatisticsService statisticsService;

    private final Instant startedAt = Instant.now();
    private String instanceId = "local";

    public StreamAdminController(
            SessionManager sessionManager,
            SessionRegistry sessionRegistry,
            SchedulerManager schedulerManager,
            BroadcastService broadcastService,
            StreamProperties properties,
            Optional<AdminCommandService> commandService,
            Optional<StreamStatisticsService> statisticsService) {
        this.sessionManager = sessionManager;
        this.sessionRegistry = sessionRegistry;
        this.schedulerManager = schedulerManager;
        this.broadcastService = broadcastService;
        this.properties = properties;
        this.commandService = commandService.orElse(null);
        this.statisticsService = statisticsService.orElse(null);
    }

    /**
     * Get overall stream statistics (local instance).
     *
     * @return stream statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<StreamStats> getStats() {
        StreamStats stats = StreamStats.builder()
                .activeSessions(sessionRegistry.count())
                .activeSchedulers(schedulerManager.getSchedulerCount())
                .totalSubscriptions(calculateTotalSubscriptions())
                .mode(properties.getMode().name())
                .sessionRegistryAvailable(sessionRegistry.isAvailable())
                .broadcastServiceAvailable(broadcastService.isAvailable())
                .serverStartedAt(startedAt)
                .instanceId(instanceId)
                .distributedAdminEnabled(isDistributedAdminEnabled())
                .build();

        return ResponseEntity.ok(stats);
    }

    /**
     * Get global stream statistics (all servers, DB-based).
     * <p>
     * Returns aggregated statistics across all server instances.
     * Requires DB persistence to be configured.
     *
     * @return global statistics or 404 if DB persistence not available
     */
    @GetMapping("/stats/global")
    public ResponseEntity<StreamStatisticsService.StreamStats> getGlobalStats() {
        if (statisticsService == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statisticsService.getStats());
    }

    /**
     * Get global sessions (all servers, DB-based).
     *
     * @param state optional state filter
     * @return list of session summaries
     */
    @GetMapping("/sessions/global")
    public ResponseEntity<List<StreamStatisticsService.SessionSummary>> getGlobalSessions(
            @RequestParam(required = false) SessionState state) {
        if (statisticsService == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statisticsService.getSessions(state));
    }

    /**
     * Get global session details (DB-based).
     *
     * @param sessionId the session ID
     * @return session details
     */
    @GetMapping("/sessions/global/{sessionId}")
    public ResponseEntity<StreamStatisticsService.SessionDetails> getGlobalSessionDetails(
            @PathVariable String sessionId) {
        if (statisticsService == null) {
            return ResponseEntity.notFound().build();
        }

        StreamStatisticsService.SessionDetails details = statisticsService.getSessionDetails(sessionId);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Get subscriptions by resource (DB-based).
     *
     * @param resource the resource name
     * @return list of subscription summaries
     */
    @GetMapping("/subscriptions/resource/{resource}")
    public ResponseEntity<List<StreamStatisticsService.SubscriptionSummary>> getSubscriptionsByResource(
            @PathVariable String resource) {
        if (statisticsService == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statisticsService.getSubscriptionsByResource(resource));
    }

    /**
     * Get server instances (DB-based).
     *
     * @return list of server stats
     */
    @GetMapping("/servers")
    public ResponseEntity<List<StreamStatisticsService.ServerStats>> getServers() {
        if (statisticsService == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statisticsService.getServerStats());
    }

    /**
     * Get all active sessions (local instance).
     *
     * @return list of session info
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getSessions() {
        List<SessionInfo> sessions = sessionManager.getAllSessions().stream()
                .map(this::toSessionInfo)
                .collect(Collectors.toList());

        return ResponseEntity.ok(sessions);
    }

    /**
     * Get a specific session.
     *
     * @param sessionId the session ID
     * @return session info
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String sessionId) {
        return sessionManager.findSession(sessionId)
                .map(this::toSessionInfo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get sessions for a specific user.
     *
     * @param userId the user ID
     * @return list of session info
     */
    @GetMapping("/sessions/user/{userId}")
    public ResponseEntity<List<SessionInfo>> getSessionsByUser(@PathVariable String userId) {
        List<SessionInfo> sessions = sessionManager.getSessionsByUser(userId).stream()
                .map(this::toSessionInfo)
                .collect(Collectors.toList());

        return ResponseEntity.ok(sessions);
    }

    /**
     * Terminate a session.
     * <p>
     * In distributed mode, the command is queued and executed asynchronously.
     *
     * @param sessionId the session ID
     * @return command response (202 Accepted if queued, 204 No Content if executed directly)
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> terminateSession(@PathVariable String sessionId) {
        if (isDistributedAdminEnabled()) {
            AdminCommand command = commandService.queueTerminateSession(sessionId);
            log.info("Queued terminate session command: sessionId={}, commandId={}",
                    sessionId, command.getId());
            return ResponseEntity.accepted().body(CommandResponse.queued(command));
        }

        sessionManager.terminateSession(sessionId);
        log.info("Admin terminated session: {}", sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all active schedulers.
     *
     * @return list of scheduler info
     */
    @GetMapping("/schedulers")
    public ResponseEntity<List<SchedulerInfo>> getSchedulers() {
        List<SchedulerInfo> schedulers = schedulerManager.getAllSchedulers().stream()
                .map(this::toSchedulerInfo)
                .collect(Collectors.toList());

        return ResponseEntity.ok(schedulers);
    }

    /**
     * Get a specific scheduler.
     *
     * @param subscriptionKey the subscription key
     * @return scheduler info
     */
    @GetMapping("/schedulers/{subscriptionKey}")
    public ResponseEntity<SchedulerInfo> getScheduler(@PathVariable String subscriptionKey) {
        return schedulerManager.getScheduler(subscriptionKey)
                .map(this::toSchedulerInfo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Stop a scheduler.
     * <p>
     * In distributed mode, the command is queued and executed asynchronously.
     *
     * @param subscriptionKey the subscription key
     * @return command response (202 Accepted if queued, 204 No Content if executed directly)
     */
    @DeleteMapping("/schedulers/{subscriptionKey}")
    public ResponseEntity<?> stopScheduler(@PathVariable String subscriptionKey) {
        if (isDistributedAdminEnabled()) {
            AdminCommand command = commandService.queueStopScheduler(subscriptionKey);
            log.info("Queued stop scheduler command: key={}, commandId={}",
                    subscriptionKey, command.getId());
            return ResponseEntity.accepted().body(CommandResponse.queued(command));
        }

        schedulerManager.stopScheduler(subscriptionKey);
        log.info("Admin stopped scheduler: {}", subscriptionKey);
        return ResponseEntity.noContent().build();
    }

    /**
     * Force trigger a scheduler execution.
     * <p>
     * In distributed mode, the command is queued and executed asynchronously.
     *
     * @param subscriptionKey the subscription key
     * @return command response (202 Accepted if queued, 204 No Content if executed directly)
     */
    @PostMapping("/schedulers/{subscriptionKey}/trigger")
    public ResponseEntity<?> triggerScheduler(@PathVariable String subscriptionKey) {
        if (isDistributedAdminEnabled()) {
            AdminCommand command = commandService.queueTriggerScheduler(subscriptionKey);
            log.info("Queued trigger scheduler command: key={}, commandId={}",
                    subscriptionKey, command.getId());
            return ResponseEntity.accepted().body(CommandResponse.queued(command));
        }

        schedulerManager.triggerNow(subscriptionKey);
        log.info("Admin triggered scheduler: {}", subscriptionKey);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get a command status.
     *
     * @param commandId the command ID
     * @return command status
     */
    @GetMapping("/commands/{commandId}")
    public ResponseEntity<CommandResponse> getCommandStatus(@PathVariable Long commandId) {
        if (!isDistributedAdminEnabled()) {
            return ResponseEntity.notFound().build();
        }

        return commandService.getCommand(commandId)
                .map(CommandResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get pending commands.
     *
     * @return list of pending commands
     */
    @GetMapping("/commands/pending")
    public ResponseEntity<List<CommandResponse>> getPendingCommands() {
        if (!isDistributedAdminEnabled()) {
            return ResponseEntity.ok(List.of());
        }

        List<CommandResponse> commands = commandService.getPendingCommands().stream()
                .map(CommandResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(commands);
    }

    /**
     * Set the instance ID (for distributed mode).
     *
     * @param instanceId the instance ID
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    private boolean isDistributedAdminEnabled() {
        return commandService != null && commandService.isEnabled();
    }

    private SessionInfo toSessionInfo(StreamSession session) {
        return SessionInfo.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .transportType(session.getTransportType())
                .state(session.getState())
                .connectedAt(session.getConnectedAt())
                .lastActiveAt(session.getLastActiveAt())
                .subscriptions(session.getSubscriptions().stream()
                        .map(k -> k.toKeyString())
                        .collect(Collectors.toSet()))
                .subscriptionCount(session.getSubscriptions().size())
                .instanceId(instanceId)
                .build();
    }

    private SchedulerInfo toSchedulerInfo(SubscriptionScheduler scheduler) {
        return SchedulerInfo.builder()
                .subscriptionKey(scheduler.getKey().toKeyString())
                .resource(scheduler.getKey().getResource())
                .params(scheduler.getKey().getParams())
                .state(scheduler.getState())
                .intervalMs(scheduler.getIntervalMs())
                .subscribers(scheduler.getSubscribers())
                .subscriberCount(scheduler.getSubscriberCount())
                .createdAt(scheduler.getCreatedAt())
                .lastExecutedAt(scheduler.getLastExecutedAt())
                .executionCount(scheduler.getExecutionCount())
                .errorCount(scheduler.getErrorCount())
                .consecutiveErrors(scheduler.getConsecutiveErrors())
                .instanceId(instanceId)
                .build();
    }

    private long calculateTotalSubscriptions() {
        return sessionManager.getAllSessions().stream()
                .mapToLong(s -> s.getSubscriptions().size())
                .sum();
    }
}
