package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from GitHub OAuth2 responses.
 * <p>
 * GitHub uses numeric ID and may not provide email if the user
 * has set their email to private. In that case, a separate API
 * call to /user/emails may be needed.
 * <p>
 * Response format:
 * <pre>{@code
 * {
 *   "id": 123456789,
 *   "login": "username",
 *   "name": "User Name",
 *   "email": "user@example.com",  // may be null if private
 *   "avatar_url": "https://..."
 * }
 * }</pre>
 */
public class GitHubUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.GITHUB;
    }

    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        // GitHub uses numeric ID
        Object id = attributes.get("id");
        String providerId = id != null ? String.valueOf(id) : null;

        // Note: Email might be null if user has private email setting
        // Caller may need to fetch from /user/emails endpoint separately
        String email = getString(attributes, "email");

        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.GITHUB)
                .providerId(providerId)
                .email(email)
                .emailVerified(email != null) // If email is provided, it's verified
                .name(getString(attributes, "name"))
                .profileImageUrl(getString(attributes, "avatar_url"))
                .attributes(attributes)
                .build();
    }
}
