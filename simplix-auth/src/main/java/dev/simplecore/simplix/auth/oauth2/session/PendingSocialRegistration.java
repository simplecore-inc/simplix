package dev.simplecore.simplix.auth.oauth2.session;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Holds pending social registration information in session.
 * <p>
 * When a user attempts to login with a social account that is not linked to any existing account,
 * the social information is stored in this object and saved to the session.
 * This allows the user to proceed with registration without re-authenticating with the social provider.
 * <p>
 * TTL is configurable via {@code simplix.auth.oauth2.pending-registration-ttl-seconds}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingSocialRegistration implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default expiry time in seconds (10 minutes).
     * Used when TTL is not configured.
     */
    public static final long DEFAULT_TTL_SECONDS = 600;

    /**
     * Session attribute key for storing pending registration.
     */
    public static final String SESSION_ATTR = "oauth2.pending";

    /**
     * Social provider type (e.g., GOOGLE, KAKAO).
     */
    private OAuth2ProviderType provider;

    /**
     * Unique identifier from the social provider.
     */
    private String providerId;

    /**
     * Email address from the social provider.
     */
    private String email;

    /**
     * Display name from the social provider.
     */
    private String name;

    /**
     * First name from the social provider.
     */
    private String firstName;

    /**
     * Last name from the social provider.
     */
    private String lastName;

    /**
     * Profile image URL from the social provider.
     */
    private String profileImageUrl;

    /**
     * Whether the email is verified by the social provider.
     */
    private boolean emailVerified;

    /**
     * Locale information from the social provider.
     */
    private String locale;

    /**
     * Timestamp when this pending registration was created.
     */
    private Instant createdAt;

    /**
     * Configured TTL in seconds for this registration.
     * Used by isExpired() to check validity.
     */
    private long ttlSeconds;

    /**
     * Create a PendingSocialRegistration from OAuth2UserInfo.
     *
     * @param userInfo   the OAuth2 user info
     * @param ttlSeconds the TTL in seconds
     * @return the pending registration
     */
    public static PendingSocialRegistration from(OAuth2UserInfo userInfo, long ttlSeconds) {
        return PendingSocialRegistration.builder()
                .provider(userInfo.getProvider())
                .providerId(userInfo.getProviderId())
                .email(userInfo.getEmail())
                .name(userInfo.getName())
                .firstName(userInfo.getFirstName())
                .lastName(userInfo.getLastName())
                .profileImageUrl(userInfo.getProfileImageUrl())
                .emailVerified(userInfo.isEmailVerified())
                .locale(userInfo.getLocale())
                .createdAt(Instant.now())
                .ttlSeconds(ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS)
                .build();
    }

    /**
     * Check if this pending registration has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        if (createdAt == null) {
            return true;
        }
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        return createdAt.plusSeconds(effectiveTtl).isBefore(Instant.now());
    }

    /**
     * Get provider name as string for backward compatibility.
     *
     * @return provider name or null
     */
    public String getProviderName() {
        return provider != null ? provider.name() : null;
    }
}
