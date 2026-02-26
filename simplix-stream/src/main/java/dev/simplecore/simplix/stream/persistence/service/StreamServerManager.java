package dev.simplecore.simplix.stream.persistence.service;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.persistence.entity.StreamServerInstanceEntity;
import dev.simplecore.simplix.stream.persistence.repository.StreamServerInstanceRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages server instance lifecycle in distributed mode.
 * <p>
 * Responsible for:
 * - Registering this server instance on startup
 * - Sending periodic heartbeats
 * - Detecting dead servers and cleaning up orphan sessions
 */
@Slf4j
public class StreamServerManager {

    private final StreamServerInstanceRepository serverRepository;
    private final StreamSessionRepository sessionRepository;
    private final StreamProperties properties;

    @Getter
    private final String instanceId;

    private final String hostname;

    private volatile boolean running = false;
    private volatile Consumer<String> onDeadServerDetected;

    public StreamServerManager(
            StreamServerInstanceRepository serverRepository,
            StreamSessionRepository sessionRepository,
            StreamProperties properties) {
        this.serverRepository = serverRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.instanceId = resolveInstanceId();
        this.hostname = resolveHostname();
    }

    @PostConstruct
    public void initialize() {
        registerInstance();
        running = true;
        log.info("StreamServerManager initialized: instanceId={}, hostname={}",
                instanceId, hostname);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        unregisterInstance();
        log.info("StreamServerManager shutdown: instanceId={}", instanceId);
    }

    /**
     * Register this server instance in the database.
     */
    @Transactional
    public void registerInstance() {
        StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                .instanceId(instanceId)
                .hostname(hostname)
                .startedAt(Instant.now())
                .lastHeartbeatAt(Instant.now())
                .status(StreamServerInstanceEntity.Status.ACTIVE)
                .activeSessions(0)
                .activeSchedulers(0)
                .build();

        serverRepository.save(entity);
        log.info("Server instance registered: {}", instanceId);
    }

    /**
     * Unregister this server instance from the database.
     */
    @Transactional
    public void unregisterInstance() {
        serverRepository.findById(instanceId).ifPresent(entity -> {
            entity.markDead();
            serverRepository.save(entity);
        });
        log.info("Server instance unregistered: {}", instanceId);
    }

    /**
     * Send heartbeat and update statistics.
     * <p>
     * Called periodically by the scheduler.
     */
    @Scheduled(fixedRateString = "${simplix.stream.server.heartbeat-interval:30000}")
    @Transactional
    public void heartbeat() {
        if (!running) {
            return;
        }

        serverRepository.updateHeartbeat(instanceId, Instant.now());
        log.trace("Heartbeat sent: {}", instanceId);
    }

    /**
     * Update server statistics.
     *
     * @param activeSessions   current active session count
     * @param activeSchedulers current active scheduler count
     */
    @Transactional
    public void updateStats(int activeSessions, int activeSchedulers) {
        serverRepository.updateStats(instanceId, activeSessions, activeSchedulers);
    }

    /**
     * Check for dead servers and clean up orphan sessions.
     * <p>
     * Called periodically by the scheduler.
     */
    @Scheduled(fixedRateString = "${simplix.stream.server.cleanup-interval:60000}")
    @Transactional
    public void cleanupDeadServers() {
        if (!running) {
            return;
        }

        Duration heartbeatInterval = properties.getServer().getHeartbeatInterval();
        Duration deadThreshold = properties.getServer().getDeadThreshold();

        Instant suspectedCutoff = Instant.now().minus(heartbeatInterval.multipliedBy(2));
        Instant deadCutoff = Instant.now().minus(deadThreshold);

        // Mark servers as suspected if they missed heartbeats
        int suspected = serverRepository.markSuspected(suspectedCutoff);
        if (suspected > 0) {
            log.warn("Marked {} server(s) as suspected", suspected);
        }

        // Find dead servers
        List<StreamServerInstanceEntity> deadServers = serverRepository.findDeadServers(deadCutoff);

        for (StreamServerInstanceEntity server : deadServers) {
            handleDeadServer(server);
        }

        // Mark suspected servers as dead if they exceed threshold
        int dead = serverRepository.markDead(deadCutoff);
        if (dead > 0) {
            log.warn("Marked {} server(s) as dead", dead);
        }
    }

    /**
     * Handle a detected dead server.
     * <p>
     * Terminates orphan sessions and notifies listeners.
     *
     * @param server the dead server entity
     */
    @Transactional
    public void handleDeadServer(StreamServerInstanceEntity server) {
        String deadInstanceId = server.getInstanceId();

        log.warn("Dead server detected: {} (lastHeartbeat={})",
                deadInstanceId, server.getLastHeartbeatAt());

        // Terminate orphan sessions
        int terminated = sessionRepository.terminateSessionsByInstanceId(deadInstanceId);
        log.info("Terminated {} orphan session(s) from dead server: {}",
                terminated, deadInstanceId);

        // Notify listener
        if (onDeadServerDetected != null) {
            onDeadServerDetected.accept(deadInstanceId);
        }
    }

    /**
     * Get all active server instances.
     *
     * @return list of active servers
     */
    public List<StreamServerInstanceEntity> getActiveServers() {
        return serverRepository.findActive();
    }

    /**
     * Get server instance by ID.
     *
     * @param instanceId the instance ID
     * @return the server entity if found
     */
    public StreamServerInstanceEntity getServer(String instanceId) {
        return serverRepository.findById(instanceId).orElse(null);
    }

    /**
     * Get the count of active servers.
     *
     * @return the count
     */
    public long getActiveServerCount() {
        return serverRepository.countByStatus(StreamServerInstanceEntity.Status.ACTIVE);
    }

    /**
     * Set callback for dead server detection.
     *
     * @param callback the callback
     */
    public void setOnDeadServerDetected(Consumer<String> callback) {
        this.onDeadServerDetected = callback;
    }

    /**
     * Check if this instance is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    private String resolveInstanceId() {
        String configured = properties.getServer().getInstanceId();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Could not resolve hostname", e);
            return "unknown";
        }
    }
}
