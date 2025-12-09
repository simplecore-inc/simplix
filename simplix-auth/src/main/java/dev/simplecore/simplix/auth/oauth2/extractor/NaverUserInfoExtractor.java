package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from Naver OAuth2 responses.
 * <p>
 * Naver wraps all user info inside a "response" object.
 * Email verification status is not provided, assumed verified.
 * <p>
 * Response format:
 * <pre>{@code
 * {
 *   "resultcode": "00",
 *   "message": "success",
 *   "response": {
 *     "id": "abc123",
 *     "email": "user@example.com",
 *     "name": "User Name",
 *     "profile_image": "https://..."
 *   }
 * }
 * }</pre>
 */
public class NaverUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.NAVER;
    }

    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        // Naver wraps user info in "response" object
        Map<String, Object> response = getMap(attributes, "response");

        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.NAVER)
                .providerId(getString(response, "id"))
                .email(getString(response, "email"))
                .emailVerified(true) // Naver doesn't provide this field, assume verified
                .name(getString(response, "name"))
                .profileImageUrl(getString(response, "profile_image"))
                .attributes(attributes)
                .build();
    }
}
