package dev.simplecore.simplix.stream.security;

import dev.simplecore.simplix.stream.core.model.StreamSession;

/**
 * Interface for validating active stream sessions.
 * <p>
 * Implementations are invoked periodically (e.g., on heartbeat) to verify
 * that a session is still valid. Common checks include token expiration,
 * user account status, or permission revocation.
 * <p>
 * Implementations SHOULD be non-blocking and complete within 100ms.
 * {@link StreamSession#getSubscriptions()} is weakly-consistent; rely on
 * immutable session properties (userId, metadata) for validation logic.
 */
public interface SessionValidator {

    /**
     * Validate the given session.
     *
     * @param session the session to validate
     * @return the validation result
     */
    SessionValidationResult validate(StreamSession session);
}
