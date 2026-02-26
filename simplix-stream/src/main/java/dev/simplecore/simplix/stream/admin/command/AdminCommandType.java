package dev.simplecore.simplix.stream.admin.command;

/**
 * Types of admin commands that can be queued for distributed execution.
 */
public enum AdminCommandType {

    /**
     * Terminate a session
     */
    TERMINATE_SESSION,

    /**
     * Stop a scheduler
     */
    STOP_SCHEDULER,

    /**
     * Trigger immediate scheduler execution
     */
    TRIGGER_SCHEDULER
}
