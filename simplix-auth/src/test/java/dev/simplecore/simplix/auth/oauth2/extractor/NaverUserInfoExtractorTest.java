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

@DisplayName("NaverUserInfoExtractor")
class NaverUserInfoExtractorTest {

    private NaverUserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new NaverUserInfoExtractor();
    }

    @Test
    @DisplayName("should return NAVER provider type")
    void shouldReturnNaverProviderType() {
        assertThat(extractor.getProviderType()).isEqualTo(OAuth2ProviderType.NAVER);
    }

    @Test
    @DisplayName("should extract user info from Naver response object")
    void shouldExtractFromNaverResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "naver-abc123");
        response.put("email", "user@naver.com");
        response.put("name", "Naver User");
        response.put("profile_image", "https://naver.com/profile.jpg");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("resultcode", "00");
        attributes.put("message", "success");
        attributes.put("response", response);

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "resultcode");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.NAVER);
        assertThat(info.getProviderId()).isEqualTo("naver-abc123");
        assertThat(info.getEmail()).isEqualTo("user@naver.com");
        assertThat(info.isEmailVerified()).isTrue();
        assertThat(info.getName()).isEqualTo("Naver User");
        assertThat(info.getProfileImageUrl()).isEqualTo("https://naver.com/profile.jpg");
    }

    @Test
    @DisplayName("should handle missing response object")
    void shouldHandleMissingResponse() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("resultcode", "00");

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "resultcode");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.NAVER);
        assertThat(info.getProviderId()).isNull();
        assertThat(info.getEmail()).isNull();
    }
}
