package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from Apple Sign In (OIDC) responses.
 * <p>
 * Apple provides minimal user info through OIDC ID token.
 * Important: Apple only provides name on the FIRST login. Subsequent
 * logins will not include the name, so it must be stored on first auth.
 * <p>
 * Response format (first login):
 * <pre>{@code
 * {
 *   "sub": "001234.abc...xyz",
 *   "email": "user@privaterelay.appleid.com",
 *   "email_verified": "true",
 *   "name": {
 *     "firstName": "John",
 *     "lastName": "Doe"
 *   }
 * }
 * }</pre>
 * <p>
 * Response format (subsequent logins):
 * <pre>{@code
 * {
 *   "sub": "001234.abc...xyz",
 *   "email": "user@privaterelay.appleid.com",
 *   "email_verified": "true"
 * }
 * }</pre>
 */
public class AppleUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.APPLE;
    }

    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        // Apple provides info via OIDC ID token
        if (oauth2User instanceof OidcUser oidcUser) {
            // Note: Apple only provides name on first login
            String name = oidcUser.getFullName();
            String firstName = null;
            String lastName = null;

            if (name == null) {
                // Try to get from attributes (first login only)
                Map<String, Object> nameObj = getMap(attributes, "name");
                firstName = getString(nameObj, "firstName");
                lastName = getString(nameObj, "lastName");
                if (firstName != null || lastName != null) {
                    name = buildFullName(firstName, lastName);
                }
            } else {
                firstName = oidcUser.getGivenName();
                lastName = oidcUser.getFamilyName();
            }

            return OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.APPLE)
                    .providerId(oidcUser.getSubject())
                    .email(oidcUser.getEmail())
                    .emailVerified(Boolean.TRUE.equals(oidcUser.getEmailVerified()))
                    .name(name)
                    .firstName(firstName)
                    .lastName(lastName)
                    .attributes(attributes)
                    .build();
        }

        // Fallback for non-OIDC flow (rare case)
        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.APPLE)
                .providerId(getString(attributes, "sub"))
                .email(getString(attributes, "email"))
                .emailVerified(getBoolean(attributes, "email_verified"))
                .attributes(attributes)
                .build();
    }

    private String buildFullName(String firstName, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            sb.append(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(lastName);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
