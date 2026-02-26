package dev.simplecore.simplix.stream.admin.command;

/**
 * Status of an admin command.
 */
public enum AdminCommandStatus {

    /**
     * Command is waiting to be processed
     */
    PENDING,

    /**
     * Command was executed successfully
     */
    EXECUTED,

    /**
     * Command execution failed
     */
    FAILED,

    /**
     * Command expired without being executed
     */
    EXPIRED,

    /**
     * Target not found (session or scheduler does not exist)
     */
    NOT_FOUND
}
