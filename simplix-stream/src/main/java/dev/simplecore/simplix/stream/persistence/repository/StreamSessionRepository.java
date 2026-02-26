package dev.simplecore.simplix.stream.persistence.repository;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.persistence.entity.StreamSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for stream session entities.
 */
public interface StreamSessionRepository extends JpaRepository<StreamSessionEntity, String> {

    /**
     * Find all sessions by user ID.
     *
     * @param userId the user ID
     * @return list of sessions
     */
    List<StreamSessionEntity> findByUserId(String userId);

    /**
     * Find all sessions by state.
     *
     * @param state the session state
     * @return list of sessions
     */
    List<StreamSessionEntity> findByState(SessionState state);

    /**
     * Find all sessions by server instance ID.
     *
     * @param instanceId the server instance ID
     * @return list of sessions
     */
    List<StreamSessionEntity> findByInstanceId(String instanceId);

    /**
     * Find active sessions (CONNECTED or DISCONNECTED) by user ID.
     *
     * @param userId the user ID
     * @return list of active sessions
     */
    @Query("SELECT s FROM StreamSessionEntity s WHERE s.userId = :userId AND s.state IN ('CONNECTED', 'DISCONNECTED')")
    List<StreamSessionEntity> findActiveByUserId(@Param("userId") String userId);

    /**
     * Find a session by ID and user ID for ownership validation.
     *
     * @param id     the session ID
     * @param userId the user ID
     * @return the session if found and owned by user
     */
    Optional<StreamSessionEntity> findByIdAndUserId(String id, String userId);

    /**
     * Count sessions by user ID and active states.
     *
     * @param userId the user ID
     * @return the count
     */
    @Query("SELECT COUNT(s) FROM StreamSessionEntity s WHERE s.userId = :userId AND s.state IN ('CONNECTED', 'DISCONNECTED')")
    long countActiveByUserId(@Param("userId") String userId);

    /**
     * Count sessions by state.
     *
     * @param state the session state
     * @return the count
     */
    long countByState(SessionState state);

    /**
     * Count sessions by server instance ID.
     *
     * @param instanceId the server instance ID
     * @return the count
     */
    long countByInstanceId(String instanceId);

    /**
     * Find sessions that have been inactive longer than the timeout.
     *
     * @param state  the session state
     * @param cutoff the cutoff timestamp
     * @return list of timed out sessions
     */
    @Query("SELECT s FROM StreamSessionEntity s WHERE s.state = :state AND s.lastActiveAt < :cutoff")
    List<StreamSessionEntity> findTimedOutSessions(
            @Param("state") SessionState state,
            @Param("cutoff") Instant cutoff);

    /**
     * Find disconnected sessions that have exceeded the grace period.
     *
     * @param cutoff the cutoff timestamp
     * @return list of expired sessions
     */
    @Query("SELECT s FROM StreamSessionEntity s WHERE s.state = 'DISCONNECTED' AND s.disconnectedAt < :cutoff")
    List<StreamSessionEntity> findExpiredDisconnectedSessions(@Param("cutoff") Instant cutoff);

    /**
     * Mark sessions as terminated for a dead server instance.
     *
     * @param instanceId the server instance ID
     * @return the number of updated sessions
     */
    @Modifying
    @Query("UPDATE StreamSessionEntity s SET s.state = 'TERMINATED', s.terminatedAt = CURRENT_TIMESTAMP WHERE s.instanceId = :instanceId AND s.state != 'TERMINATED'")
    int terminateSessionsByInstanceId(@Param("instanceId") String instanceId);

    /**
     * Update session last active timestamp.
     *
     * @param sessionId the session ID
     * @param timestamp the new timestamp
     * @return the number of updated sessions
     */
    @Modifying
    @Query("UPDATE StreamSessionEntity s SET s.lastActiveAt = :timestamp WHERE s.id = :sessionId")
    int updateLastActiveAt(@Param("sessionId") String sessionId, @Param("timestamp") Instant timestamp);

    /**
     * Delete terminated sessions older than the specified timestamp.
     *
     * @param cutoff the cutoff timestamp
     * @return the number of deleted sessions
     */
    @Modifying
    @Query("DELETE FROM StreamSessionEntity s WHERE s.state = 'TERMINATED' AND s.terminatedAt < :cutoff")
    int deleteTerminatedBefore(@Param("cutoff") Instant cutoff);
}
