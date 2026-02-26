package dev.simplecore.simplix.stream.infrastructure.local;

import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of SessionRegistry.
 * <p>
 * Uses ConcurrentHashMap for thread-safe session storage.
 * Suitable for single-server deployments or sticky session environments.
 */
@Slf4j
public class LocalSessionRegistry implements SessionRegistry {

    private final ConcurrentHashMap<String, StreamSession> sessions = new ConcurrentHashMap<>();
    private volatile boolean available = false;

    @Override
    public void register(StreamSession session) {
        sessions.put(session.getId(), session);
        log.debug("Session registered: {} (user={})", session.getId(), session.getUserId());
    }

    @Override
    public void unregister(String sessionId) {
        StreamSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("Session unregistered: {} (user={})", sessionId, removed.getUserId());
        }
    }

    @Override
    public Optional<StreamSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Collection<StreamSession> findByUserId(String userId) {
        return sessions.values().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<StreamSession> findAll() {
        return sessions.values();
    }

    @Override
    public long count() {
        return sessions.size();
    }

    @Override
    public long countByUserId(String userId) {
        return sessions.values().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .count();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize() {
        available = true;
        log.info("Local session registry initialized");
    }

    @Override
    public void shutdown() {
        available = false;
        sessions.clear();
        log.info("Local session registry shutdown");
    }
}
