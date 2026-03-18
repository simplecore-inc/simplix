package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OAuth2UserInfoExtractor")
class OAuth2UserInfoExtractorTest {

    private OAuth2UserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new OAuth2UserInfoExtractor();
    }

    @Test
    @DisplayName("should have all default extractors registered")
    void shouldHaveAllDefaultExtractors() {
        for (OAuth2ProviderType type : OAuth2ProviderType.values()) {
            assertThat(extractor.hasExtractor(type))
                    .as("Should have extractor for " + type)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("should extract user info from OAuth2 authentication")
    void shouldExtractUserInfo() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 12345);
        attributes.put("name", "Test User");
        attributes.put("email", "test@github.com");
        attributes.put("avatar_url", "https://github.com/avatar.jpg");

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                "github");

        OAuth2UserInfo info = extractor.extract(authToken);

        assertThat(info).isNotNull();
        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.GITHUB);
        assertThat(info.getProviderId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("should throw for non-OAuth2 authentication")
    void shouldThrowForNonOAuth2Auth() {
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", "pass");

        assertThatThrownBy(() -> extractor.extract(auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be OAuth2AuthenticationToken");
    }

    @Test
    @DisplayName("should support custom extractor registration")
    void shouldSupportCustomExtractorRegistration() {
        OAuth2UserInfoExtractorStrategy customStrategy = new OAuth2UserInfoExtractorStrategy() {
            @Override
            public OAuth2ProviderType getProviderType() {
                return OAuth2ProviderType.GOOGLE;
            }

            @Override
            public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
                return OAuth2UserInfo.builder()
                        .provider(OAuth2ProviderType.GOOGLE)
                        .providerId("custom-id")
                        .build();
            }
        };

        extractor.registerExtractor(customStrategy);

        assertThat(extractor.hasExtractor(OAuth2ProviderType.GOOGLE)).isTrue();
    }

    @Test
    @DisplayName("should create with custom strategy list")
    void shouldCreateWithCustomStrategies() {
        OAuth2UserInfoExtractor customExtractor = new OAuth2UserInfoExtractor(
                List.of(new GoogleUserInfoExtractor()));

        assertThat(customExtractor.hasExtractor(OAuth2ProviderType.GOOGLE)).isTrue();
        assertThat(customExtractor.hasExtractor(OAuth2ProviderType.KAKAO)).isFalse();
    }
}
