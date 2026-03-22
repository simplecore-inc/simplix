package dev.simplecore.simplix.stream.security;

/**
 * Result of a session validation check.
 *
 * @param valid  whether the session is valid
 * @param reason the reason for invalidation (null if valid)
 */
public record SessionValidationResult(boolean valid, String reason) {

    public static SessionValidationResult ok() {
        return new SessionValidationResult(true, null);
    }

    public static SessionValidationResult invalid(String reason) {
        return new SessionValidationResult(false, reason);
    }
}
