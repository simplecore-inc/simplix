package dev.simplecore.simplix.auth.oauth2;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

/**
 * Exception thrown during OAuth2 authentication process.
 * Contains an error code for client-side handling and i18n.
 */
@Getter
public class OAuth2AuthenticationException extends AuthenticationException {

    /**
     * Error code for email already registered with different login method.
     */
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";

    /**
     * Error code for social account already linked to another user.
     */
    public static final String SOCIAL_ALREADY_LINKED = "SOCIAL_ALREADY_LINKED";

    /**
     * Error code for provider already linked to current user.
     */
    public static final String PROVIDER_ALREADY_LINKED = "PROVIDER_ALREADY_LINKED";

    /**
     * Error code for attempting to unlink the last login method.
     */
    public static final String LAST_LOGIN_METHOD = "LAST_LOGIN_METHOD";

    /**
     * Error code for provider communication error.
     */
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";

    /**
     * Error code for linking operation failure.
     */
    public static final String LINKING_FAILED = "LINKING_FAILED";

    /**
     * Error code for user not found.
     */
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    /**
     * Error code for no linked account found during login-only mode.
     * Used when social login is attempted but no existing account is linked.
     */
    public static final String NO_LINKED_ACCOUNT = "NO_LINKED_ACCOUNT";

    /**
     * Error code for when an account with the same email exists but is not linked to social provider.
     * User should login with existing method and link the social account.
     */
    public static final String EMAIL_ACCOUNT_EXISTS_NOT_LINKED = "EMAIL_ACCOUNT_EXISTS_NOT_LINKED";

    /**
     * Error code for attempting registration with a social account that is already registered.
     * Used in REGISTER intent mode when the social account is already linked to an existing user.
     */
    public static final String SOCIAL_ALREADY_REGISTERED = "SOCIAL_ALREADY_REGISTERED";

    private final String errorCode;

    public OAuth2AuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OAuth2AuthenticationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
