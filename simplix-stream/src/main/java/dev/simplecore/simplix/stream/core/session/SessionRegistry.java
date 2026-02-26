package dev.simplecore.simplix.stream.core.session;

import dev.simplecore.simplix.stream.core.model.StreamSession;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for managing stream sessions.
 * <p>
 * Implementations provide Local (ConcurrentHashMap) or Distributed (Redis) storage.
 */
public interface SessionRegistry {

    /**
     * Register a new session.
     *
     * @param session the session to register
     */
    void register(StreamSession session);

    /**
     * Unregister a session by ID.
     *
     * @param sessionId the session ID
     */
    void unregister(String sessionId);

    /**
     * Find a session by ID.
     *
     * @param sessionId the session ID
     * @return the session if found
     */
    Optional<StreamSession> findById(String sessionId);

    /**
     * Find all sessions for a user.
     *
     * @param userId the user ID
     * @return collection of sessions
     */
    Collection<StreamSession> findByUserId(String userId);

    /**
     * Find all sessions.
     *
     * @return collection of all sessions
     */
    Collection<StreamSession> findAll();

    /**
     * Get the total session count.
     *
     * @return the session count
     */
    long count();

    /**
     * Get the session count for a user.
     *
     * @param userId the user ID
     * @return the session count
     */
    long countByUserId(String userId);

    /**
     * Check if the registry is available.
     *
     * @return true if available
     */
    boolean isAvailable();

    /**
     * Initialize the registry.
     */
    void initialize();

    /**
     * Shutdown the registry.
     */
    void shutdown();
}
