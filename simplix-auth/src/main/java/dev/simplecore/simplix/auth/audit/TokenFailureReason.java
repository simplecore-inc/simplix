package dev.simplecore.simplix.auth.audit;

/**
 * Enum representing the various reasons a token validation or operation can fail.
 * <p>
 * Used in token audit events to provide detailed failure information for:
 * - Security monitoring and threat detection
 * - Compliance reporting (ISO 27001, SOC2, GDPR)
 * - Debugging authentication issues
 * <p>
 * Each reason corresponds to a specific validation check in the token lifecycle.
 */
public enum TokenFailureReason {

    /**
     * No failure - operation was successful.
     * Used for success audit events.
     */
    NONE,

    /**
     * Token has expired (exp claim has passed).
     * The token was valid but is no longer within its validity period.
     */
    TOKEN_EXPIRED,

    /**
     * Token has been revoked and is in the blacklist.
     * Typically occurs after logout or explicit token revocation.
     */
    TOKEN_REVOKED,

    /**
     * Token signature verification failed.
     * Indicates potential tampering or use of wrong signing key.
     */
    INVALID_SIGNATURE,

    /**
     * Token could not be parsed (malformed JWT/JWE).
     * The token structure does not conform to expected format.
     */
    MALFORMED_TOKEN,

    /**
     * Client IP address does not match the IP stored in token claims.
     * Security feature to prevent token theft/replay attacks.
     */
    IP_MISMATCH,

    /**
     * User-Agent does not match the User-Agent stored in token claims.
     * Security feature to detect token usage from different devices.
     */
    USER_AGENT_MISMATCH,

    /**
     * No token was provided in the request.
     * Request is missing Authorization header or token cookie.
     */
    MISSING_TOKEN,

    /**
     * Refresh token header is missing.
     * Required for token refresh operations.
     */
    MISSING_REFRESH_TOKEN,

    /**
     * Basic authentication credentials are invalid.
     * Username/password combination is incorrect.
     */
    INVALID_CREDENTIALS,

    /**
     * User account was not found in the system.
     * The username in the token does not correspond to an existing account.
     */
    USER_NOT_FOUND,

    /**
     * User account is locked.
     * Account has been locked due to security policy (e.g., too many failed attempts).
     */
    ACCOUNT_LOCKED,

    /**
     * User account is disabled.
     * Account has been administratively disabled.
     */
    ACCOUNT_DISABLED,

    /**
     * Unknown or unspecified error.
     * Fallback for unexpected validation failures.
     */
    UNKNOWN
}
