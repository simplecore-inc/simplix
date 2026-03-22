package dev.simplecore.simplix.stream.persistence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.persistence.entity.StreamSessionEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamSubscriptionEntity;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-backed session registry.
 * <p>
 * Maintains an in-memory cache of active sessions while persisting
 * session data to the database for cross-server recovery.
 */
@Slf4j
@RequiredArgsConstructor
public class DbSessionRegistry implements SessionRegistry {

    private final StreamSessionRepository sessionRepository;
    private final StreamSubscriptionRepository subscriptionRepository;
    private final StreamProperties properties;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    // In-memory cache of active sessions on this server
    private final Map<String, StreamSession> localSessions = new ConcurrentHashMap<>();

    private volatile boolean available = false;

    @Override
    @PostConstruct
    public void initialize() {
        log.info("Initializing DbSessionRegistry for instance: {}", instanceId);
        available = true;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DbSessionRegistry");
        available = false;
        localSessions.clear();
    }

    @Override
    @Transactional
    public void register(StreamSession session) {
        // Store in local cache
        localSessions.put(session.getId(), session);

        // Persist to database
        StreamSessionEntity entity = toEntity(session);
        sessionRepository.save(entity);

        log.debug("Session registered: {} (user={}, instance={})",
                session.getId(), session.getUserId(), instanceId);
    }

    @Override
    @Transactional
    public void unregister(String sessionId) {
        // Remove from local cache
        localSessions.remove(sessionId);

        // Update database (mark as terminated)
        sessionRepository.findById(sessionId).ifPresent(entity -> {
            entity.markTerminated();
            sessionRepository.save(entity);
        });

        log.debug("Session unregistered: {}", sessionId);
    }

    @Override
    public Optional<StreamSession> findById(String sessionId) {
        // First check local cache
        StreamSession local = localSessions.get(sessionId);
        if (local != null) {
            return Optional.of(local);
        }

        // Not in local cache - this session might be on another server
        // Return empty for local operations, use restoreSession for cross-server
        return Optional.empty();
    }

    @Override
    public Collection<StreamSession> findByUserId(String userId) {
        // Return only local sessions for this user
        return localSessions.values().stream()
                .filter(s -> Objects.equals(s.getUserId(), userId))
                .toList();
    }

    @Override
    public Collection<StreamSession> findAll() {
        return Collections.unmodifiableCollection(localSessions.values());
    }

    @Override
    public long count() {
        return localSessions.size();
    }

    @Override
    public long countByUserId(String userId) {
        return localSessions.values().stream()
                .filter(s -> Objects.equals(s.getUserId(), userId))
                .count();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Restore a session from database (for cross-server reconnection).
     * <p>
     * This method loads session data from DB and recreates the in-memory
     * StreamSession object, including subscriptions.
     *
     * @param sessionId the session ID to restore
     * @param userId    the user ID for ownership validation
     * @return the restored session if found and valid
     */
    @Transactional
    public Optional<StreamSession> restoreSession(String sessionId, String userId) {
        // Check if already local
        StreamSession local = localSessions.get(sessionId);
        if (local != null) {
            if (!Objects.equals(local.getUserId(), userId)) {
                log.warn("Session ownership mismatch during restore: session={}, owner={}, requester={}",
                        sessionId, local.getUserId(), userId);
                return Optional.empty();
            }
            return Optional.of(local);
        }

        // Load from database
        Optional<StreamSessionEntity> entityOpt = sessionRepository.findById(sessionId);
        if (entityOpt.isEmpty()) {
            log.debug("Session not found in database: {}", sessionId);
            return Optional.empty();
        }

        StreamSessionEntity entity = entityOpt.get();

        // Validate ownership
        if (!Objects.equals(entity.getUserId(), userId)) {
            log.warn("Session ownership mismatch during restore: session={}, owner={}, requester={}",
                    sessionId, entity.getUserId(), userId);
            return Optional.empty();
        }

        // Check if session can be restored (only DISCONNECTED sessions)
        if (entity.getState() != SessionState.DISCONNECTED) {
            log.debug("Session cannot be restored (state={}): {}", entity.getState(), sessionId);
            return Optional.empty();
        }

        // Restore session
        StreamSession restored = fromEntity(entity);

        // Load subscriptions
        List<StreamSubscriptionEntity> subscriptions =
                subscriptionRepository.findBySessionIdAndActiveTrue(sessionId);
        for (StreamSubscriptionEntity sub : subscriptions) {
            SubscriptionKey key = parseSubscriptionKey(sub);
            if (key != null) {
                restored.addSubscription(key);
            }
        }

        // Update entity for new instance
        entity.setInstanceId(instanceId);
        entity.markReconnected();
        sessionRepository.save(entity);

        // Add to local cache
        localSessions.put(sessionId, restored);

        log.info("Session restored from database: {} (user={}, subscriptions={})",
                sessionId, userId, subscriptions.size());

        return Optional.of(restored);
    }

    /**
     * Update session in database.
     *
     * @param session the session to update
     */
    @Transactional
    public void update(StreamSession session) {
        sessionRepository.findById(session.getId()).ifPresent(entity -> {
            entity.setLastActiveAt(session.getLastActiveAt());
            entity.setState(session.getState());
            if (session.getState() == SessionState.DISCONNECTED) {
                entity.setDisconnectedAt(session.getDisconnectedAt());
            }
            sessionRepository.save(entity);
        });
    }

    /**
     * Update session last active timestamp.
     *
     * @param sessionId the session ID
     */
    @Transactional
    public void touch(String sessionId) {
        sessionRepository.updateLastActiveAt(sessionId, Instant.now());
    }

    /**
     * Get session count from database (global count).
     *
     * @param state the session state
     * @return the count
     */
    public long countByState(SessionState state) {
        return sessionRepository.countByState(state);
    }

    /**
     * Get active session count from database for a user (global count).
     *
     * @param userId the user ID
     * @return the count
     */
    public long countActiveByUserIdGlobal(String userId) {
        return sessionRepository.countActiveByUserId(userId);
    }

    /**
     * Find session entity by ID (for admin operations).
     *
     * @param sessionId the session ID
     * @return the entity if found
     */
    public Optional<StreamSessionEntity> findEntityById(String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /**
     * Find all session entities by state (for admin operations).
     *
     * @param state the session state
     * @return list of entities
     */
    public List<StreamSessionEntity> findEntitiesByState(SessionState state) {
        return sessionRepository.findByState(state);
    }

    /**
     * Find all session entities by instance ID (for orphan detection).
     *
     * @param instanceId the server instance ID
     * @return list of entities
     */
    public List<StreamSessionEntity> findEntitiesByInstanceId(String instanceId) {
        return sessionRepository.findByInstanceId(instanceId);
    }

    // Note: Only persistent metadata (getMetadata()) is serialized.
    // Transient metadata (getTransientMetadata()) is intentionally excluded
    // as it exists only for the lifetime of the in-memory session.
    private StreamSessionEntity toEntity(StreamSession session) {
        return StreamSessionEntity.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .transportType(session.getTransportType())
                .state(session.getState())
                .instanceId(instanceId)
                .connectedAt(session.getConnectedAt())
                .lastActiveAt(session.getLastActiveAt())
                .disconnectedAt(session.getDisconnectedAt())
                .metadataJson(serializeMetadata(session.getMetadata()))
                .messagesSent(0L)
                .bytesSent(0L)
                .build();
    }

    private StreamSession fromEntity(StreamSessionEntity entity) {
        Map<String, Object> metadata = deserializeMetadata(entity.getMetadataJson());

        return StreamSession.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .transportType(entity.getTransportType())
                .metadata(metadata)
                .build();
    }

    private SubscriptionKey parseSubscriptionKey(StreamSubscriptionEntity entity) {
        try {
            Map<String, Object> params = entity.getParamsJson() != null
                    ? objectMapper.readValue(entity.getParamsJson(), new TypeReference<>() {})
                    : Map.of();
            return SubscriptionKey.of(entity.getResource(), params);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse subscription params: {}", entity.getSubscriptionKey(), e);
            return null;
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata", e);
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize metadata", e);
            return new HashMap<>();
        }
    }
}
