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

@DisplayName("GitHubUserInfoExtractor")
class GitHubUserInfoExtractorTest {

    private GitHubUserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new GitHubUserInfoExtractor();
    }

    @Test
    @DisplayName("should return GITHUB provider type")
    void shouldReturnGitHubProviderType() {
        assertThat(extractor.getProviderType()).isEqualTo(OAuth2ProviderType.GITHUB);
    }

    @Test
    @DisplayName("should extract user info from GitHub response")
    void shouldExtractFromGitHubResponse() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 123456789);
        attributes.put("login", "octocat");
        attributes.put("name", "The Octocat");
        attributes.put("email", "octocat@github.com");
        attributes.put("avatar_url", "https://github.com/avatar.jpg");

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.GITHUB);
        assertThat(info.getProviderId()).isEqualTo("123456789");
        assertThat(info.getEmail()).isEqualTo("octocat@github.com");
        assertThat(info.isEmailVerified()).isTrue();
        assertThat(info.getName()).isEqualTo("The Octocat");
        assertThat(info.getProfileImageUrl()).isEqualTo("https://github.com/avatar.jpg");
    }

    @Test
    @DisplayName("should handle null email (private email setting)")
    void shouldHandleNullEmail() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 987654321);
        attributes.put("login", "private-user");
        attributes.put("name", "Private User");

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getEmail()).isNull();
        assertThat(info.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("should handle null id")
    void shouldHandleNullId() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("login", "no-id-user");

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "login");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProviderId()).isNull();
    }
}
