package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from Kakao OAuth2/OIDC responses.
 * <p>
 * Kakao wraps user info in nested objects:
 * - Account info in "kakao_account"
 * - Profile in "kakao_account.profile"
 * <p>
 * Response format:
 * <pre>{@code
 * {
 *   "id": 123456789,
 *   "kakao_account": {
 *     "email": "user@example.com",
 *     "is_email_verified": true,
 *     "profile": {
 *       "nickname": "User",
 *       "profile_image_url": "https://..."
 *     }
 *   }
 * }
 * }</pre>
 */
public class KakaoUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.KAKAO;
    }

    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        Map<String, Object> kakaoAccount = getMap(attributes, "kakao_account");
        Map<String, Object> profile = getMap(kakaoAccount, "profile");

        String email = getString(kakaoAccount, "email");
        boolean emailVerified = getBoolean(kakaoAccount, "is_email_verified");

        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.KAKAO)
                .providerId(String.valueOf(attributes.get("id")))
                .email(email)
                .emailVerified(emailVerified)
                .name(getString(profile, "nickname"))
                .profileImageUrl(getString(profile, "profile_image_url"))
                .attributes(attributes)
                .build();
    }
}
