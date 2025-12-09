package dev.simplecore.simplix.auth.oauth2.properties;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for SimpliX OAuth2 social login.
 */
@Getter
@Setter
public class SimpliXOAuth2Properties {

    /**
     * Enable OAuth2 social login functionality.
     */
    private boolean enabled = true;

    /**
     * URL to redirect after successful login.
     */
    private String successUrl = "/";

    /**
     * URL to redirect after failed login.
     */
    private String failureUrl = "/login?error=social";

    /**
     * URL to redirect after successful account linking.
     */
    private String linkSuccessUrl = "/settings/social?linked=true";

    /**
     * URL to redirect after failed account linking.
     */
    private String linkFailureUrl = "/settings/social?error=link_failed";

    /**
     * Base URL for account linking endpoints.
     */
    private String linkBaseUrl = "/oauth2/link";

    /**
     * Base URL for OAuth2 authorization endpoints.
     */
    private String authorizationBaseUrl = "/oauth2/authorize";

    /**
     * Base URL for OAuth2 callback endpoints.
     */
    private String callbackBaseUrl = "/oauth2/callback";

    /**
     * Base URL for login-only OAuth2 endpoints.
     * Login mode only authenticates existing users and rejects new ones.
     */
    private String loginBaseUrl = "/oauth2/login";

    /**
     * Base URL for registration OAuth2 endpoints.
     * Registration mode creates new accounts if not exists.
     */
    private String registerBaseUrl = "/oauth2/register";

    /**
     * Policy for handling email conflicts during registration.
     */
    private EmailConflictPolicy emailConflictPolicy = EmailConflictPolicy.REJECT;

    /**
     * Method for delivering tokens after successful authentication.
     */
    private TokenDeliveryMethod tokenDeliveryMethod = TokenDeliveryMethod.COOKIE;

    /**
     * Cookie settings for token delivery.
     */
    private CookieSettings cookie = new CookieSettings();

    /**
     * Allowed origins for postMessage.
     * <p>
     * Used when tokenDeliveryMethod is POST_MESSAGE.
     * If empty, '*' is used (not recommended for production).
     * <p>
     * Example:
     * <pre>{@code
     * simplix:
     *   auth:
     *     oauth2:
     *       allowed-origins:
     *         - http://localhost:5173
     *         - https://your-domain.com
     * }</pre>
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * TTL in seconds for pending social registration session data.
     * <p>
     * When a user attempts social login without an existing account,
     * their social profile info is stored in session for this duration
     * to allow completing registration.
     * <p>
     * Default: 600 seconds (10 minutes)
     */
    private long pendingRegistrationTtlSeconds = 600;

    /**
     * Get the first allowed origin for postMessage, or '*' if none configured.
     *
     * @return the origin to use in postMessage
     */
    public String getPostMessageOrigin() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return "*";
        }
        return allowedOrigins.get(0);
    }

    /**
     * Policy for handling email conflicts when a user tries to login
     * with a social account that has an email already registered
     * with a different login method.
     */
    public enum EmailConflictPolicy {
        /**
         * Reject the login and show an error message.
         * User must login with existing method and link the social account.
         */
        REJECT,

        /**
         * Automatically link the social account to the existing user.
         * Less secure but more convenient.
         */
        AUTO_LINK
    }

    /**
     * Method for delivering JWT tokens to the client after successful OAuth2 login.
     */
    public enum TokenDeliveryMethod {
        /**
         * Add tokens as query parameters in redirect URL.
         * Warning: Tokens may be logged in browser history.
         */
        REDIRECT,

        /**
         * Store tokens in HttpOnly cookies.
         * Recommended for web applications.
         */
        COOKIE,

        /**
         * Render an HTML page that sends tokens via window.postMessage.
         * Recommended for SPAs using popup-based OAuth2 flow.
         */
        POST_MESSAGE
    }

    /**
     * Cookie settings for token delivery.
     */
    @Getter
    @Setter
    public static class CookieSettings {
        /**
         * Name of the access token cookie.
         */
        private String accessTokenName = "access_token";

        /**
         * Name of the refresh token cookie.
         */
        private String refreshTokenName = "refresh_token";

        /**
         * Cookie path.
         */
        private String path = "/";

        /**
         * Whether cookies should be HttpOnly.
         */
        private boolean httpOnly = true;

        /**
         * Whether cookies should be secure (HTTPS only).
         */
        private boolean secure = true;

        /**
         * SameSite attribute for cookies.
         */
        private String sameSite = "Lax";
    }
}
