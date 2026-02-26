package dev.simplecore.simplix.stream.admin.command;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing admin commands in distributed environment.
 * <p>
 * In distributed mode, admin commands (session termination, scheduler control)
 * cannot be executed directly because the target may reside on a different instance.
 * This service queues commands to a database and each instance polls and executes
 * commands for targets it owns.
 */
public interface AdminCommandService {

    /**
     * Queue a command for execution.
     *
     * @param command the command to queue
     * @return the saved command with generated ID
     */
    AdminCommand queueCommand(AdminCommand command);

    /**
     * Queue a terminate session command.
     *
     * @param sessionId the session to terminate
     * @return the queued command
     */
    AdminCommand queueTerminateSession(String sessionId);

    /**
     * Queue a stop scheduler command.
     *
     * @param subscriptionKey the scheduler to stop
     * @return the queued command
     */
    AdminCommand queueStopScheduler(String subscriptionKey);

    /**
     * Queue a trigger scheduler command.
     *
     * @param subscriptionKey the scheduler to trigger
     * @return the queued command
     */
    AdminCommand queueTriggerScheduler(String subscriptionKey);

    /**
     * Get a command by ID.
     *
     * @param commandId the command ID
     * @return the command if found
     */
    Optional<AdminCommand> getCommand(Long commandId);

    /**
     * Get recent commands for a target.
     *
     * @param targetId the target ID
     * @return list of commands
     */
    List<AdminCommand> getCommandsForTarget(String targetId);

    /**
     * Get pending commands.
     *
     * @return list of pending commands
     */
    List<AdminCommand> getPendingCommands();

    /**
     * Check if distributed admin mode is enabled.
     *
     * @return true if enabled
     */
    boolean isEnabled();
}
