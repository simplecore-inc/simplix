package dev.simplecore.simplix.auth.oauth2;

import lombok.Getter;

import java.util.Arrays;

/**
 * Enumeration of supported OAuth2/OIDC providers.
 *
 * <p>The named constants are consumer providers whose responses differ from one another, so each
 * needs an extractor that knows its shape. {@link #OIDC} is what everything else is: a provider
 * that publishes a discovery document and answers with the claims the specification names, which
 * an application registers under whatever id it likes.
 */
@Getter
public enum OAuth2ProviderType {

    GOOGLE("google", "Google", true),
    KAKAO("kakao", "Kakao", true),
    NAVER("naver", "Naver", false),
    GITHUB("github", "GitHub", false),
    FACEBOOK("facebook", "Facebook", false),
    APPLE("apple", "Apple", true),

    /**
     * A standards-compliant OIDC provider this enum does not name — Keycloak, Entra ID, Okta, an
     * organization's own server.
     *
     * <p><b>Its registration id is not {@code "oidc"} in practice.</b> An application registers
     * such a provider under a name of its own choosing and {@link #resolve(String)} answers this
     * constant for it; the id itself travels on {@code OAuth2UserInfo.getRegistrationId()}, which
     * is what tells two of them apart. The id here exists so the constant round-trips through
     * {@link #fromRegistrationId(String)} like the others.
     */
    OIDC("oidc", "OIDC", true);

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
     * Classify a registration ID, treating an unrecognised one as a generic OIDC provider.
     *
     * <p>Use this rather than {@link #fromRegistrationId(String)} anywhere a registration comes
     * from configuration the application owns. An enterprise IdP is registered under a name this
     * enum cannot have anticipated, and refusing it would make every deployment that federates
     * with its own directory an exception the framework cannot express.
     *
     * @param registrationId the OAuth2 client registration ID
     * @return the named provider, or {@link #OIDC} when the id names none of them
     */
    public static OAuth2ProviderType resolve(String registrationId) {
        return Arrays.stream(values())
                .filter(p -> p.registrationId.equalsIgnoreCase(registrationId))
                .findFirst()
                .orElse(OIDC);
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
