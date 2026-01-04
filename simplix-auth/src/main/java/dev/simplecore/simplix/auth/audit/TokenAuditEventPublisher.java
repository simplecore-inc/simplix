package dev.simplecore.simplix.auth.audit;

import java.time.Duration;

/**
 * Interface for publishing token-related audit events.
 * <p>
 * Implementations of this interface are responsible for persisting audit events
 * to their chosen storage mechanism (database, event bus, logging system, etc.).
 * <p>
 * The SimpliX framework calls these methods at appropriate points in the token
 * lifecycle, allowing applications to maintain comprehensive audit trails for:
 * - Security monitoring and threat detection
 * - Compliance requirements (ISO 27001, SOC2, GDPR)
 * - Debugging and troubleshooting
 * <p>
 * Implementations should:
 * - Handle exceptions gracefully (never throw exceptions that disrupt main flow)
 * - Process events asynchronously if persistence is slow
 * - Mask or hash sensitive data as required by security policies
 * <p>
 * Security Compliance:
 * - ISO 27001 A.12.4.1: Event logging
 * - SOC2 CC7.2: System monitoring
 * - GDPR Art. 30: Records of processing activities
 */
public interface TokenAuditEventPublisher {

    // ========================================
    // Token Validation Events
    // ========================================

    /**
     * Publishes an audit event when token validation fails.
     * <p>
     * Called when any token validation check fails, including:
     * - Token expired
     * - Token revoked (blacklisted)
     * - Invalid signature
     * - Malformed token
     * - IP mismatch
     * - User-Agent mismatch
     *
     * @param event The audit event containing failure details
     */
    void publishTokenValidationFailed(TokenAuditEvent event);

    // ========================================
    // Token Refresh Events
    // ========================================

    /**
     * Publishes an audit event when token refresh succeeds.
     * <p>
     * Called after a new token pair is successfully generated from a refresh token.
     * The event should contain both old and new JTI values for tracking.
     *
     * @param event The audit event containing refresh details
     */
    void publishTokenRefreshSuccess(TokenAuditEvent event);

    /**
     * Publishes an audit event when token refresh fails.
     * <p>
     * Called when refresh token validation or new token generation fails.
     *
     * @param event The audit event containing failure details
     */
    void publishTokenRefreshFailed(TokenAuditEvent event);

    // ========================================
    // Token Issue Events
    // ========================================

    /**
     * Publishes an audit event when token issuance succeeds.
     * <p>
     * Called after successful authentication and token pair generation.
     *
     * @param event The audit event containing issue details
     */
    void publishTokenIssueSuccess(TokenAuditEvent event);

    /**
     * Publishes an audit event when token issuance fails.
     * <p>
     * Called when authentication fails or token generation encounters an error.
     *
     * @param event The audit event containing failure details
     */
    void publishTokenIssueFailed(TokenAuditEvent event);

    // ========================================
    // Token Revocation Events
    // ========================================

    /**
     * Publishes an audit event when a token is revoked.
     * <p>
     * Called during logout or explicit token revocation.
     * This typically precedes the token being added to the blacklist.
     *
     * @param event The audit event containing revocation details
     */
    void publishTokenRevoked(TokenAuditEvent event);

    // ========================================
    // Blacklist Events
    // ========================================

    /**
     * Publishes an audit event when a token is added to the blacklist.
     * <p>
     * Called when a token JTI is added to the blacklist, typically during
     * logout or token refresh (when old token is invalidated).
     *
     * @param jti The JWT ID being blacklisted
     * @param ttl The time-to-live for the blacklist entry
     * @param username The username associated with the token (may be null)
     */
    void publishTokenBlacklisted(String jti, Duration ttl, String username);

    /**
     * Publishes an audit event when a blacklisted token is used.
     * <p>
     * Called when validation detects that a token's JTI is in the blacklist.
     * This may indicate attempted token reuse after logout.
     *
     * @param jti The JWT ID that was found in the blacklist
     * @param username The username from the token (may be null)
     * @param clientIp The client IP address attempting to use the token
     */
    void publishBlacklistedTokenUsed(String jti, String username, String clientIp);
}
