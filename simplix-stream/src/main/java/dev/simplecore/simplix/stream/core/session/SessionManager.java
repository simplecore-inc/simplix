package dev.simplecore.simplix.stream.core.session;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.exception.SessionExpiredException;
import dev.simplecore.simplix.stream.exception.SessionNotFoundException;
import dev.simplecore.simplix.stream.persistence.service.DbSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages stream session lifecycle.
 * <p>
 * Handles session creation, connection state transitions, grace period management,
 * and cleanup of terminated sessions. Supports cross-server session restoration
 * when using DbSessionRegistry.
 */
@Slf4j
public class SessionManager {

    private final SessionRegistry sessionRegistry;
    private final StreamProperties properties;
    private final ScheduledExecutorService scheduledExecutor;

    // Optional DbSessionRegistry for cross-server session restoration
    private final DbSessionRegistry dbSessionRegistry;

    public SessionManager(
            SessionRegistry sessionRegistry,
            StreamProperties properties,
            ScheduledExecutorService scheduledExecutor) {
        this(sessionRegistry, properties, scheduledExecutor, null);
    }

    public SessionManager(
            SessionRegistry sessionRegistry,
            StreamProperties properties,
            ScheduledExecutorService scheduledExecutor,
            DbSessionRegistry dbSessionRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
        this.scheduledExecutor = scheduledExecutor;
        this.dbSessionRegistry = dbSessionRegistry;

        if (dbSessionRegistry != null) {
            log.info("SessionManager initialized with DB persistence support");
        }
    }

    // Grace period timers for disconnected sessions
    private final ConcurrentHashMap<String, ScheduledFuture<?>> gracePeriodTimers = new ConcurrentHashMap<>();

    // Lock for grace period operations
    private final Object gracePeriodLock = new Object();

    // Callbacks for session events (volatile for thread visibility)
    private volatile Consumer<StreamSession> onSessionTerminated;

    /**
     * Create and register a new session.
     *
     * @param userId        the authenticated user ID
     * @param transportType the transport type
     * @return the created session
     */
    public StreamSession createSession(String userId, TransportType transportType) {
        // Check user session limit
        int maxPerUser = properties.getSession().getMaxPerUser();
        if (maxPerUser > 0) {
            long currentCount = sessionRegistry.countByUserId(userId);
            if (currentCount >= maxPerUser) {
                log.warn("User {} has reached session limit: {}/{}", userId, currentCount, maxPerUser);
                // Option: terminate oldest session or reject new connection
                // Currently rejecting - can be configured
                terminateOldestSession(userId);
            }
        }

        StreamSession session = StreamSession.create(userId, transportType);
        sessionRegistry.register(session);

        log.info("Session created: {} (user={}, transport={})",
                session.getId(), userId, transportType);

        return session;
    }

    /**
     * Get a session by ID.
     *
     * @param sessionId the session ID
     * @return the session
     * @throws SessionNotFoundException if not found
     */
    public StreamSession getSession(String sessionId) {
        return sessionRegistry.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    /**
     * Get a session by ID, optionally.
     *
     * @param sessionId the session ID
     * @return the session if found
     */
    public Optional<StreamSession> findSession(String sessionId) {
        return sessionRegistry.findById(sessionId);
    }

    /**
     * Get a session and validate ownership.
     *
     * @param sessionId the session ID
     * @param userId    the expected user ID
     * @return the session
     * @throws SessionNotFoundException if not found
     * @throws SecurityException        if user doesn't own session
     */
    public StreamSession getSessionForUser(String sessionId, String userId) {
        StreamSession session = getSession(sessionId);

        if (!session.getUserId().equals(userId)) {
            log.warn("Session ownership violation: session={}, owner={}, requester={}",
                    sessionId, session.getUserId(), userId);
            throw new SecurityException("Session not owned by user");
        }

        if (session.isTerminated()) {
            throw new SessionExpiredException(sessionId);
        }

        return session;
    }

    /**
     * Mark a session as disconnected and start grace period timer.
     *
     * @param sessionId the session ID
     */
    public void markDisconnected(String sessionId) {
        sessionRegistry.findById(sessionId).ifPresent(session -> {
            synchronized (gracePeriodLock) {
                if (session.getState() == SessionState.CONNECTED) {
                    session.markDisconnected();

                    // Update DB if available
                    if (dbSessionRegistry != null) {
                        dbSessionRegistry.update(session);
                    }

                    startGracePeriodTimer(session);
                    log.info("Session disconnected, grace period started: {} (user={})",
                            sessionId, session.getUserId());
                }
            }
        });
    }

    /**
     * Reconnect a session within grace period (same server).
     *
     * @param sessionId the session ID
     * @return true if reconnected successfully
     */
    public boolean reconnect(String sessionId) {
        Optional<StreamSession> sessionOpt = sessionRegistry.findById(sessionId);

        if (sessionOpt.isEmpty()) {
            return false;
        }

        StreamSession session = sessionOpt.get();

        synchronized (gracePeriodLock) {
            if (session.getState() == SessionState.DISCONNECTED) {
                // Cancel grace period timer
                cancelGracePeriodTimer(sessionId);
                session.markReconnected();

                // Update DB if available
                if (dbSessionRegistry != null) {
                    dbSessionRegistry.update(session);
                }

                log.info("Session reconnected: {} (user={})", sessionId, session.getUserId());
                return true;
            }

            return session.getState() == SessionState.CONNECTED;
        }
    }

    /**
     * Restore a session from another server (cross-server reconnection).
     * <p>
     * This method is used when a client reconnects to a different server than
     * the one it was originally connected to. The session data is loaded from
     * the database and recreated on this server.
     *
     * @param sessionId the session ID to restore
     * @param userId    the user ID for ownership validation
     * @return the restored session with subscriptions, or empty if not found or invalid
     */
    public Optional<StreamSession> restoreSession(String sessionId, String userId) {
        if (dbSessionRegistry == null) {
            log.debug("Session restore not available: DB persistence not configured");
            return Optional.empty();
        }

        // First check if session is already local
        Optional<StreamSession> localSession = sessionRegistry.findById(sessionId);
        if (localSession.isPresent()) {
            StreamSession session = localSession.get();
            if (!session.getUserId().equals(userId)) {
                log.warn("Session restore denied: ownership mismatch (session={}, owner={}, requester={})",
                        sessionId, session.getUserId(), userId);
                return Optional.empty();
            }
            // Session is already on this server, just reconnect
            if (reconnect(sessionId)) {
                return Optional.of(session);
            }
            return Optional.empty();
        }

        // Try to restore from database
        Optional<StreamSession> restored = dbSessionRegistry.restoreSession(sessionId, userId);

        if (restored.isPresent()) {
            StreamSession session = restored.get();

            // Cancel any existing grace period timer (just in case)
            cancelGracePeriodTimer(sessionId);

            log.info("Session restored from another server: {} (user={}, subscriptions={})",
                    sessionId, userId, session.getSubscriptionCount());

            return restored;
        }

        log.debug("Session restore failed: not found or not restorable (sessionId={})", sessionId);
        return Optional.empty();
    }

    /**
     * Check if session restoration is supported.
     *
     * @return true if DB persistence is configured
     */
    public boolean isRestorationSupported() {
        return dbSessionRegistry != null;
    }

    /**
     * Terminate a session immediately.
     *
     * @param sessionId the session ID
     * @return the cleared subscriptions
     */
    public Set<SubscriptionKey> terminateSession(String sessionId) {
        Optional<StreamSession> sessionOpt = sessionRegistry.findById(sessionId);

        if (sessionOpt.isEmpty()) {
            return Set.of();
        }

        StreamSession session = sessionOpt.get();

        synchronized (gracePeriodLock) {
            // Cancel any pending grace period timer
            cancelGracePeriodTimer(sessionId);

            // Mark terminated
            session.markTerminated();
        }

        return completeTermination(session);
    }

    /**
     * Complete session termination after state is marked as terminated.
     * <p>
     * This method should be called after acquiring gracePeriodLock and marking
     * the session as terminated.
     *
     * @param session the session to terminate
     * @return the cleared subscriptions
     */
    private Set<SubscriptionKey> completeTermination(StreamSession session) {
        Set<SubscriptionKey> clearedSubscriptions = session.clearSubscriptions();

        // Unregister from registry
        sessionRegistry.unregister(session.getId());

        log.info("Session terminated: {} (user={}, subscriptions={})",
                session.getId(), session.getUserId(), clearedSubscriptions.size());

        // Notify callback
        if (onSessionTerminated != null) {
            onSessionTerminated.accept(session);
        }

        return clearedSubscriptions;
    }

    /**
     * Update session last active time.
     *
     * @param sessionId the session ID
     */
    public void touch(String sessionId) {
        sessionRegistry.findById(sessionId).ifPresent(StreamSession::touch);
    }

    /**
     * Get all sessions for a user.
     *
     * @param userId the user ID
     * @return collection of sessions
     */
    public Collection<StreamSession> getSessionsByUser(String userId) {
        return sessionRegistry.findByUserId(userId);
    }

    /**
     * Get all active sessions.
     *
     * @return collection of all sessions
     */
    public Collection<StreamSession> getAllSessions() {
        return sessionRegistry.findAll();
    }

    /**
     * Get total session count.
     *
     * @return the count
     */
    public long getSessionCount() {
        return sessionRegistry.count();
    }

    /**
     * Set callback for session termination events.
     *
     * @param callback the callback
     */
    public void setOnSessionTerminated(Consumer<StreamSession> callback) {
        this.onSessionTerminated = callback;
    }

    /**
     * Cleanup inactive sessions periodically.
     */
    @Scheduled(fixedRateString = "${simplix.stream.session.cleanup-interval:30000}")
    public void cleanupInactiveSessions() {
        Duration timeout = properties.getSession().getTimeout();
        if (timeout.isZero()) {
            return; // Timeout disabled
        }

        Instant cutoff = Instant.now().minus(timeout);

        sessionRegistry.findAll().stream()
                .filter(s -> s.getState() == SessionState.CONNECTED)
                .filter(s -> s.getLastActiveAt().isBefore(cutoff))
                .forEach(s -> {
                    log.info("Session timed out due to inactivity: {} (lastActive={})",
                            s.getId(), s.getLastActiveAt());
                    markDisconnected(s.getId());
                });
    }

    private void startGracePeriodTimer(StreamSession session) {
        Duration gracePeriod = properties.getSession().getGracePeriod();

        ScheduledFuture<?> future = scheduledExecutor.schedule(
                () -> handleGracePeriodExpired(session.getId()),
                gracePeriod.toMillis(),
                TimeUnit.MILLISECONDS
        );

        gracePeriodTimers.put(session.getId(), future);
    }

    private void cancelGracePeriodTimer(String sessionId) {
        ScheduledFuture<?> future = gracePeriodTimers.remove(sessionId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void handleGracePeriodExpired(String sessionId) {
        StreamSession sessionToTerminate = null;

        synchronized (gracePeriodLock) {
            gracePeriodTimers.remove(sessionId);

            Optional<StreamSession> sessionOpt = sessionRegistry.findById(sessionId);
            if (sessionOpt.isPresent()) {
                StreamSession session = sessionOpt.get();
                if (session.getState() == SessionState.DISCONNECTED) {
                    log.info("Grace period expired, terminating session: {} (user={})",
                            sessionId, session.getUserId());
                    session.markTerminated();
                    sessionToTerminate = session;
                }
            }
        }

        // Complete termination outside the lock to avoid holding it too long
        if (sessionToTerminate != null) {
            completeTermination(sessionToTerminate);
        }
    }

    private void terminateOldestSession(String userId) {
        sessionRegistry.findByUserId(userId).stream()
                .min((a, b) -> a.getConnectedAt().compareTo(b.getConnectedAt()))
                .ifPresent(oldest -> {
                    log.info("Terminating oldest session for user limit: {} (user={})",
                            oldest.getId(), userId);
                    terminateSession(oldest.getId());
                });
    }
}
