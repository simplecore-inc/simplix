package dev.simplecore.simplix.auth.audit;

import java.util.Map;

/**
 * Immutable record representing a token-related audit event.
 * <p>
 * Contains all relevant information for auditing token operations including:
 * - User identification
 * - Token identification (JTI)
 * - Failure reason (if applicable)
 * - Client information (IP, User-Agent)
 * - Token type and additional context
 * <p>
 * Security Compliance:
 * - ISO 27001 A.12.4.1: Event logging
 * - SOC2 CC7.2: System monitoring
 * - GDPR Art. 30: Records of processing activities
 *
 * @param username The username associated with the token (may be null for anonymous)
 * @param jti The JWT ID (unique token identifier)
 * @param failureReason The reason for failure (NONE for success)
 * @param clientIp The client's IP address
 * @param userAgent The client's User-Agent header
 * @param tokenType The type of token ("access" or "refresh")
 * @param additionalDetails Additional context-specific details
 */
public record TokenAuditEvent(
        String username,
        String jti,
        TokenFailureReason failureReason,
        String clientIp,
        String userAgent,
        String tokenType,
        Map<String, Object> additionalDetails
) {

    /**
     * Creates a success audit event.
     *
     * @param username The username
     * @param jti The JWT ID
     * @param clientIp The client IP address
     * @param userAgent The User-Agent header
     * @param tokenType The token type
     * @return A new TokenAuditEvent with NONE failure reason
     */
    public static TokenAuditEvent success(
            String username,
            String jti,
            String clientIp,
            String userAgent,
            String tokenType
    ) {
        return new TokenAuditEvent(
                username,
                jti,
                TokenFailureReason.NONE,
                clientIp,
                userAgent,
                tokenType,
                null
        );
    }

    /**
     * Creates a failure audit event.
     *
     * @param username The username (may be null)
     * @param jti The JWT ID (may be null if token unparseable)
     * @param failureReason The specific failure reason
     * @param clientIp The client IP address
     * @param userAgent The User-Agent header
     * @param tokenType The token type
     * @return A new TokenAuditEvent with the specified failure reason
     */
    public static TokenAuditEvent failure(
            String username,
            String jti,
            TokenFailureReason failureReason,
            String clientIp,
            String userAgent,
            String tokenType
    ) {
        return new TokenAuditEvent(
                username,
                jti,
                failureReason,
                clientIp,
                userAgent,
                tokenType,
                null
        );
    }

    /**
     * Creates a failure audit event with additional details.
     *
     * @param username The username (may be null)
     * @param jti The JWT ID (may be null if token unparseable)
     * @param failureReason The specific failure reason
     * @param clientIp The client IP address
     * @param userAgent The User-Agent header
     * @param tokenType The token type
     * @param additionalDetails Additional context details
     * @return A new TokenAuditEvent with the specified failure reason and details
     */
    public static TokenAuditEvent failure(
            String username,
            String jti,
            TokenFailureReason failureReason,
            String clientIp,
            String userAgent,
            String tokenType,
            Map<String, Object> additionalDetails
    ) {
        return new TokenAuditEvent(
                username,
                jti,
                failureReason,
                clientIp,
                userAgent,
                tokenType,
                additionalDetails
        );
    }

    /**
     * Checks if this event represents a successful operation.
     *
     * @return true if the failure reason is NONE
     */
    public boolean isSuccess() {
        return failureReason == TokenFailureReason.NONE;
    }
}
