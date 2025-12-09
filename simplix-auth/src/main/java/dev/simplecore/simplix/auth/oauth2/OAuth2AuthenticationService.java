package dev.simplecore.simplix.auth.oauth2;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

/**
 * Service interface for OAuth2 user management.
 * Applications MUST implement this interface to enable social login functionality.
 *
 * <p>This interface defines the contract between SimpliX OAuth2 infrastructure
 * and the application's user management system.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * @Service
 * public class OAuth2AuthenticationServiceImpl implements OAuth2AuthenticationService {
 *
 *     @Override
 *     public UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo) {
 *         // Find or create user based on social profile
 *         return socialConnectionRepository
 *             .findByProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
 *             .map(conn -> loadUserDetails(conn.getUser()))
 *             .orElseGet(() -> createNewUser(userInfo));
 *     }
 * }
 * }</pre>
 */
public interface OAuth2AuthenticationService {

    /**
     * Authenticate user via OAuth2/OIDC.
     * Called on every social login attempt.
     *
     * <p>Implementation should:</p>
     * <ol>
     *   <li>Find existing user by provider + providerId</li>
     *   <li>If not found, check by email (handle conflict based on policy)</li>
     *   <li>Create new user if not found</li>
     *   <li>Return UserDetails for JWT issuance</li>
     * </ol>
     *
     * @param userInfo standardized social profile information
     * @return UserDetails for Spring Security authentication
     * @throws OAuth2AuthenticationException on conflicts or errors
     */
    UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo);

    /**
     * Authenticate user via OAuth2/OIDC with specified intent.
     * <p>
     * This method allows intent-aware authentication:
     * <ul>
     *   <li>{@link OAuth2Intent#LOGIN} - Only authenticate existing users, reject if no linked account</li>
     *   <li>{@link OAuth2Intent#REGISTER} - Reject if social account is already registered</li>
     *   <li>{@link OAuth2Intent#AUTO} - Traditional OAuth2 behavior (login or create)</li>
     * </ul>
     * <p>
     * Default implementation delegates to {@link #authenticateOAuth2User(OAuth2UserInfo)}.
     * Override this method to implement intent-aware authentication.
     *
     * @param userInfo standardized social profile information
     * @param intent the authentication intent
     * @return UserDetails for Spring Security authentication
     * @throws OAuth2AuthenticationException on conflicts, intent violations, or errors
     */
    default UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo, OAuth2Intent intent) {
        return authenticateOAuth2User(userInfo);
    }

    /**
     * Link a social account to an existing user.
     *
     * <p>This method is called when an authenticated user wants to
     * add a new social login method to their account.</p>
     *
     * @param userId current user's ID (from authentication)
     * @param userInfo social profile to link
     * @throws OAuth2AuthenticationException if already linked to another user
     *         or if the provider is already linked to this user
     */
    void linkSocialAccount(String userId, OAuth2UserInfo userInfo);

    /**
     * Unlink a social account from a user.
     *
     * <p>Implementation should verify that the user has at least one
     * remaining login method (password or another social provider)
     * before unlinking.</p>
     *
     * @param userId current user's ID
     * @param provider provider to unlink
     * @throws OAuth2AuthenticationException if this is the last login method
     */
    void unlinkSocialAccount(String userId, OAuth2ProviderType provider);

    /**
     * Get all linked social providers for a user.
     *
     * @param userId user's ID
     * @return set of linked provider types
     */
    Set<OAuth2ProviderType> getLinkedProviders(String userId);

    /**
     * Find user ID by OAuth2 provider connection.
     * <p>
     * Used when a user authenticated via OAuth2 wants to link another social account.
     * In this case, we need to find their internal user ID from the current OAuth2 connection.
     *
     * @param provider the OAuth2 provider type
     * @param providerId the provider-specific user ID
     * @return user ID if found, null otherwise
     */
    default String findUserIdByProviderConnection(OAuth2ProviderType provider, String providerId) {
        return null;
    }

    /**
     * Find user ID by email address.
     * <p>
     * Used as a fallback when a user authenticated via OAuth2 but no social connection record exists.
     * This can happen when the OAuth2 login created/found a user by email but didn't create
     * a social connection record.
     *
     * @param email the user's email address
     * @return user ID if found, null otherwise
     */
    default String findUserIdByEmail(String email) {
        return null;
    }

    /**
     * Load UserDetails by user ID.
     * <p>
     * Used after account linking to issue new JWT tokens.
     * Implementation should return the UserDetails for the given user ID.
     *
     * @param userId the user's internal ID
     * @return UserDetails for the user, or null if not found
     */
    default UserDetails loadUserDetailsByUserId(String userId) {
        return null;
    }

    /**
     * Callback invoked after successful OAuth2 authentication.
     * Use for logging, events, or other post-authentication processing.
     *
     * <p>Default implementation does nothing. Override to add custom behavior.</p>
     *
     * @param user the authenticated user details
     * @param socialInfo the social profile information
     * @param ipAddress client IP address
     * @param userAgent client User-Agent header
     */
    default void onAuthenticationSuccess(
            UserDetails user,
            OAuth2UserInfo socialInfo,
            String ipAddress,
            String userAgent) {
        // Default no-op, applications can override for logging
    }
}
