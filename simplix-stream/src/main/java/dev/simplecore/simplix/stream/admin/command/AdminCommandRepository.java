package dev.simplecore.simplix.stream.admin.command;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for admin command persistence.
 */
@Repository
public interface AdminCommandRepository extends JpaRepository<AdminCommand, Long> {

    /**
     * Find all pending commands ordered by creation time.
     *
     * @return list of pending commands
     */
    List<AdminCommand> findByStatusOrderByCreatedAtAsc(AdminCommandStatus status);

    /**
     * Find pending commands created before a specific time (for expiration).
     *
     * @param status    the status to filter
     * @param createdAt the cutoff time
     * @return list of commands
     */
    List<AdminCommand> findByStatusAndCreatedAtBefore(AdminCommandStatus status, Instant createdAt);

    /**
     * Find commands by target ID.
     *
     * @param targetId the target ID
     * @return list of commands
     */
    List<AdminCommand> findByTargetIdOrderByCreatedAtDesc(String targetId);

    /**
     * Find recent commands with limit.
     *
     * @param status the status to filter
     * @return list of commands
     */
    @Query("SELECT c FROM AdminCommand c WHERE c.status = :status ORDER BY c.createdAt DESC LIMIT 100")
    List<AdminCommand> findRecentByStatus(@Param("status") AdminCommandStatus status);

    /**
     * Delete old executed/expired commands for cleanup.
     *
     * @param statuses  statuses to delete
     * @param createdAt commands created before this time
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM AdminCommand c WHERE c.status IN :statuses AND c.createdAt < :createdAt")
    int deleteOldCommands(@Param("statuses") List<AdminCommandStatus> statuses, @Param("createdAt") Instant createdAt);
}
