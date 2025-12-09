package dev.simplecore.simplix.auth.oauth2;

import lombok.Getter;

import java.util.Arrays;

/**
 * Enumeration of supported OAuth2/OIDC providers.
 */
@Getter
public enum OAuth2ProviderType {

    GOOGLE("google", "Google", true),
    KAKAO("kakao", "Kakao", true),
    NAVER("naver", "Naver", false),
    GITHUB("github", "GitHub", false),
    FACEBOOK("facebook", "Facebook", false),
    APPLE("apple", "Apple", true);

    private final String registrationId;
    private final String displayName;
    private final boolean supportsOidc;

    OAuth2ProviderType(String registrationId, String displayName, boolean supportsOidc) {
        this.registrationId = registrationId;
        this.displayName = displayName;
        this.supportsOidc = supportsOidc;
    }

	/**
     * Find provider type by registration ID.
     *
     * @param registrationId the OAuth2 client registration ID
     * @return the matching OAuth2ProviderType
     * @throws IllegalArgumentException if no matching provider is found
     */
    public static OAuth2ProviderType fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(p -> p.registrationId.equalsIgnoreCase(registrationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OAuth2 provider registration ID: " + registrationId));
    }

    /**
     * Check if a registration ID is supported.
     *
     * @param registrationId the OAuth2 client registration ID
     * @return true if the provider is supported
     */
    public static boolean isSupported(String registrationId) {
        return Arrays.stream(values())
                .anyMatch(p -> p.registrationId.equalsIgnoreCase(registrationId));
    }
}
