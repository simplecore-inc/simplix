package dev.simplecore.simplix.auth.oauth2;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Standardized OAuth2 user information across all providers.
 * This class provides a unified interface to access user data
 * regardless of the OAuth2/OIDC provider.
 */
@Getter
@Builder
public class OAuth2UserInfo {

    /**
     * The OAuth2 provider type.
     */
    private final OAuth2ProviderType provider;

    /**
     * The unique user ID from the provider.
     * This is the primary identifier for the user in the provider's system.
     */
    private final String providerId;

    /**
     * The user's email address.
     * May be null if the user did not grant email permission.
     */
    private final String email;

    /**
     * Whether the email has been verified by the provider.
     */
    private final boolean emailVerified;

    /**
     * The user's display name.
     */
    private final String name;

    /**
     * The user's first name (if available).
     */
    private final String firstName;

    /**
     * The user's last name (if available).
     */
    private final String lastName;

    /**
     * URL to the user's profile image.
     */
    private final String profileImageUrl;

    /**
     * The user's locale/language preference.
     */
    private final String locale;

    /**
     * Raw attributes from the provider's response.
     * Contains all original data for custom processing.
     */
    private final Map<String, Object> attributes;
}
