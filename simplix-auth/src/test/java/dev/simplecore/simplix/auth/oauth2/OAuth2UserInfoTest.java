package dev.simplecore.simplix.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2UserInfo")
class OAuth2UserInfoTest {

    @Test
    @DisplayName("should build with all fields")
    void shouldBuildWithAllFields() {
        Map<String, Object> attributes = Map.of("key", "value");

        OAuth2UserInfo info = OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.GOOGLE)
                .providerId("google-123")
                .email("user@gmail.com")
                .emailVerified(true)
                .name("John Doe")
                .firstName("John")
                .lastName("Doe")
                .profileImageUrl("https://example.com/photo.jpg")
                .locale("en")
                .attributes(attributes)
                .build();

        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.GOOGLE);
        assertThat(info.getProviderId()).isEqualTo("google-123");
        assertThat(info.getEmail()).isEqualTo("user@gmail.com");
        assertThat(info.isEmailVerified()).isTrue();
        assertThat(info.getName()).isEqualTo("John Doe");
        assertThat(info.getFirstName()).isEqualTo("John");
        assertThat(info.getLastName()).isEqualTo("Doe");
        assertThat(info.getProfileImageUrl()).isEqualTo("https://example.com/photo.jpg");
        assertThat(info.getLocale()).isEqualTo("en");
        assertThat(info.getAttributes()).containsEntry("key", "value");
    }

    @Test
    @DisplayName("should allow null optional fields")
    void shouldAllowNullOptionalFields() {
        OAuth2UserInfo info = OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.GITHUB)
                .providerId("gh-456")
                .build();

        assertThat(info.getEmail()).isNull();
        assertThat(info.isEmailVerified()).isFalse();
        assertThat(info.getName()).isNull();
        assertThat(info.getFirstName()).isNull();
        assertThat(info.getLastName()).isNull();
        assertThat(info.getProfileImageUrl()).isNull();
        assertThat(info.getLocale()).isNull();
        assertThat(info.getAttributes()).isNull();
    }
}
