package dev.simplecore.simplix.auth.oauth2;

/**
 * Intent for OAuth2 authentication flow.
 * <p>
 * Determines the behavior when authenticating via OAuth2:
 * <ul>
 *   <li>{@link #LOGIN} - Only authenticate existing users</li>
 *   <li>{@link #REGISTER} - Create new accounts if not exists, reject if already exists</li>
 *   <li>{@link #AUTO} - Automatic behavior (login if exists, create if not)</li>
 * </ul>
 */
public enum OAuth2Intent {

    /**
     * Login-only mode.
     * <p>
     * If the social account is not linked to any user, return NO_LINKED_ACCOUNT error.
     */
    LOGIN,

    /**
     * Registration mode.
     * <p>
     * If the social account is already linked to a user, return SOCIAL_ALREADY_REGISTERED error.
     * This prevents duplicate account creation.
     */
    REGISTER,

    /**
     * Automatic mode (default).
     * <p>
     * Login if social account exists, create new account if not.
     * This is the traditional OAuth2 behavior.
     */
    AUTO
}
