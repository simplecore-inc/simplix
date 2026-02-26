package dev.simplecore.simplix.stream.core.enums;

/**
 * State of a stream session.
 * <p>
 * State transitions:
 * <pre>
 * (new) -> CONNECTED -> DISCONNECTED -> TERMINATED
 *              ^             |
 *              |_____________| (reconnect within grace period)
 * </pre>
 */
public enum SessionState {

    /**
     * Session is actively connected and receiving data
     */
    CONNECTED,

    /**
     * Session disconnected but within grace period for reconnection
     */
    DISCONNECTED,

    /**
     * Session terminated, pending cleanup
     */
    TERMINATED
}
