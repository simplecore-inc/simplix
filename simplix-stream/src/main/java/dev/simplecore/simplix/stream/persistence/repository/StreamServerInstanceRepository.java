package dev.simplecore.simplix.stream.persistence.repository;

import dev.simplecore.simplix.stream.persistence.entity.StreamServerInstanceEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamServerInstanceEntity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for stream server instance entities.
 */
public interface StreamServerInstanceRepository extends JpaRepository<StreamServerInstanceEntity, String> {

    /**
     * Find all server instances by status.
     *
     * @param status the server status
     * @return list of server instances
     */
    List<StreamServerInstanceEntity> findByStatus(Status status);

    /**
     * Find all active server instances.
     *
     * @return list of active server instances
     */
    default List<StreamServerInstanceEntity> findActive() {
        return findByStatus(Status.ACTIVE);
    }

    /**
     * Find server instances that have not sent a heartbeat since the cutoff.
     *
     * @param cutoff the cutoff timestamp
     * @return list of server instances
     */
    @Query("SELECT s FROM StreamServerInstanceEntity s WHERE s.lastHeartbeatAt < :cutoff AND s.status != 'DEAD'")
    List<StreamServerInstanceEntity> findMissedHeartbeats(@Param("cutoff") Instant cutoff);

    /**
     * Find suspected server instances that have been suspected longer than the threshold.
     *
     * @param cutoff the cutoff timestamp
     * @return list of server instances
     */
    @Query("SELECT s FROM StreamServerInstanceEntity s WHERE s.status = 'SUSPECTED' AND s.lastHeartbeatAt < :cutoff")
    List<StreamServerInstanceEntity> findDeadServers(@Param("cutoff") Instant cutoff);

    /**
     * Count server instances by status.
     *
     * @param status the server status
     * @return the count
     */
    long countByStatus(Status status);

    /**
     * Update heartbeat timestamp for a server.
     *
     * @param instanceId the server instance ID
     * @param timestamp  the new timestamp
     * @return the number of updated rows
     */
    @Modifying
    @Query("UPDATE StreamServerInstanceEntity s SET s.lastHeartbeatAt = :timestamp, s.status = 'ACTIVE' WHERE s.instanceId = :instanceId")
    int updateHeartbeat(@Param("instanceId") String instanceId, @Param("timestamp") Instant timestamp);

    /**
     * Update server statistics.
     *
     * @param instanceId       the server instance ID
     * @param activeSessions   the active session count
     * @param activeSchedulers the active scheduler count
     * @return the number of updated rows
     */
    @Modifying
    @Query("UPDATE StreamServerInstanceEntity s SET s.activeSessions = :activeSessions, s.activeSchedulers = :activeSchedulers WHERE s.instanceId = :instanceId")
    int updateStats(
            @Param("instanceId") String instanceId,
            @Param("activeSessions") int activeSessions,
            @Param("activeSchedulers") int activeSchedulers);

    /**
     * Mark servers as suspected if they have missed heartbeats.
     *
     * @param cutoff the cutoff timestamp
     * @return the number of updated rows
     */
    @Modifying
    @Query("UPDATE StreamServerInstanceEntity s SET s.status = 'SUSPECTED' WHERE s.lastHeartbeatAt < :cutoff AND s.status = 'ACTIVE'")
    int markSuspected(@Param("cutoff") Instant cutoff);

    /**
     * Mark suspected servers as dead if they exceed the dead threshold.
     *
     * @param cutoff the cutoff timestamp
     * @return the number of updated rows
     */
    @Modifying
    @Query("UPDATE StreamServerInstanceEntity s SET s.status = 'DEAD' WHERE s.lastHeartbeatAt < :cutoff AND s.status = 'SUSPECTED'")
    int markDead(@Param("cutoff") Instant cutoff);

    /**
     * Delete dead server instances.
     *
     * @return the number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM StreamServerInstanceEntity s WHERE s.status = 'DEAD'")
    int deleteDead();

    /**
     * Get total active sessions across all active servers.
     *
     * @return the total count
     */
    @Query("SELECT COALESCE(SUM(s.activeSessions), 0) FROM StreamServerInstanceEntity s WHERE s.status = 'ACTIVE'")
    long getTotalActiveSessions();

    /**
     * Get total active schedulers across all active servers.
     *
     * @return the total count
     */
    @Query("SELECT COALESCE(SUM(s.activeSchedulers), 0) FROM StreamServerInstanceEntity s WHERE s.status = 'ACTIVE'")
    long getTotalActiveSchedulers();
}
