package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from Facebook OAuth2 responses.
 * <p>
 * Facebook provides profile picture in a nested "picture.data.url" structure.
 * Email addresses from Facebook are considered verified.
 * <p>
 * Response format:
 * <pre>{@code
 * {
 *   "id": "123456789",
 *   "email": "user@example.com",
 *   "name": "John Doe",
 *   "first_name": "John",
 *   "last_name": "Doe",
 *   "picture": {
 *     "data": {
 *       "url": "https://..."
 *     }
 *   }
 * }
 * }</pre>
 */
public class FacebookUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.FACEBOOK;
    }

    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.FACEBOOK)
                .providerId(getString(attributes, "id"))
                .email(getString(attributes, "email"))
                .emailVerified(true) // Facebook emails are verified
                .name(getString(attributes, "name"))
                .firstName(getString(attributes, "first_name"))
                .lastName(getString(attributes, "last_name"))
                .profileImageUrl(extractPictureUrl(attributes))
                .attributes(attributes)
                .build();
    }

    /**
     * Extract profile picture URL from nested Facebook structure.
     */
    private String extractPictureUrl(Map<String, Object> attributes) {
        Map<String, Object> pictureObj = getMap(attributes, "picture");
        Map<String, Object> data = getMap(pictureObj, "data");
        return getString(data, "url");
    }
}
