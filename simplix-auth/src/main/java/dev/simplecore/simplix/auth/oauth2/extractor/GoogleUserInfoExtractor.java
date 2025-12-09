package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from Google OAuth2/OIDC responses.
 * <p>
 * Google supports OIDC, so we prefer extracting from OidcUser claims when available.
 * <p>
 * Response format:
 * <pre>{@code
 * {
 *   "sub": "123456789",
 *   "email": "user@gmail.com",
 *   "email_verified": true,
 *   "name": "John Doe",
 *   "given_name": "John",
 *   "family_name": "Doe",
 *   "picture": "https://...",
 *   "locale": "en"
 * }
 * }</pre>
 */
public class GoogleUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.GOOGLE;
    }

    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        // For OIDC, prefer claims from ID token
        if (oauth2User instanceof OidcUser oidcUser) {
            return OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId(oidcUser.getSubject())
                    .email(oidcUser.getEmail())
                    .emailVerified(Boolean.TRUE.equals(oidcUser.getEmailVerified()))
                    .name(oidcUser.getFullName())
                    .firstName(oidcUser.getGivenName())
                    .lastName(oidcUser.getFamilyName())
                    .profileImageUrl(oidcUser.getPicture())
                    .locale(oidcUser.getLocale())
                    .attributes(attributes)
                    .build();
        }

        // Fallback to attributes for non-OIDC flow
        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.GOOGLE)
                .providerId(getString(attributes, "sub"))
                .email(getString(attributes, "email"))
                .emailVerified(getBoolean(attributes, "email_verified"))
                .name(getString(attributes, "name"))
                .firstName(getString(attributes, "given_name"))
                .lastName(getString(attributes, "family_name"))
                .profileImageUrl(getString(attributes, "picture"))
                .locale(getString(attributes, "locale"))
                .attributes(attributes)
                .build();
    }
}
