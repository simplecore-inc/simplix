package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KakaoUserInfoExtractor")
class KakaoUserInfoExtractorTest {

    private KakaoUserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new KakaoUserInfoExtractor();
    }

    @Test
    @DisplayName("should return KAKAO provider type")
    void shouldReturnKakaoProviderType() {
        assertThat(extractor.getProviderType()).isEqualTo(OAuth2ProviderType.KAKAO);
    }

    @Test
    @DisplayName("should extract user info from nested Kakao response")
    void shouldExtractFromNestedResponse() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "KakaoUser");
        profile.put("profile_image_url", "https://kakao.com/photo.jpg");

        Map<String, Object> kakaoAccount = new HashMap<>();
        kakaoAccount.put("email", "user@kakao.com");
        kakaoAccount.put("is_email_verified", true);
        kakaoAccount.put("profile", profile);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 123456789L);
        attributes.put("kakao_account", kakaoAccount);

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.KAKAO);
        assertThat(info.getProviderId()).isEqualTo("123456789");
        assertThat(info.getEmail()).isEqualTo("user@kakao.com");
        assertThat(info.isEmailVerified()).isTrue();
        assertThat(info.getName()).isEqualTo("KakaoUser");
        assertThat(info.getProfileImageUrl()).isEqualTo("https://kakao.com/photo.jpg");
    }

    @Test
    @DisplayName("should handle missing kakao_account")
    void shouldHandleMissingKakaoAccount() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 999L);

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProviderId()).isEqualTo("999");
        assertThat(info.getEmail()).isNull();
        assertThat(info.getName()).isNull();
    }
}
