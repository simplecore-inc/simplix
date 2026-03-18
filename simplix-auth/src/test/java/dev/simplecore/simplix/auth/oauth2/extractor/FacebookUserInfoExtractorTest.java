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

@DisplayName("FacebookUserInfoExtractor")
class FacebookUserInfoExtractorTest {

    private FacebookUserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new FacebookUserInfoExtractor();
    }

    @Test
    @DisplayName("should return FACEBOOK provider type")
    void shouldReturnFacebookProviderType() {
        assertThat(extractor.getProviderType()).isEqualTo(OAuth2ProviderType.FACEBOOK);
    }

    @Test
    @DisplayName("should extract user info from Facebook response")
    void shouldExtractFromFacebookResponse() {
        Map<String, Object> pictureData = new HashMap<>();
        pictureData.put("url", "https://facebook.com/photo.jpg");

        Map<String, Object> picture = new HashMap<>();
        picture.put("data", pictureData);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-123456");
        attributes.put("email", "user@facebook.com");
        attributes.put("name", "John Doe");
        attributes.put("first_name", "John");
        attributes.put("last_name", "Doe");
        attributes.put("picture", picture);

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.FACEBOOK);
        assertThat(info.getProviderId()).isEqualTo("fb-123456");
        assertThat(info.getEmail()).isEqualTo("user@facebook.com");
        assertThat(info.isEmailVerified()).isTrue();
        assertThat(info.getName()).isEqualTo("John Doe");
        assertThat(info.getFirstName()).isEqualTo("John");
        assertThat(info.getLastName()).isEqualTo("Doe");
        assertThat(info.getProfileImageUrl()).isEqualTo("https://facebook.com/photo.jpg");
    }

    @Test
    @DisplayName("should handle missing picture data")
    void shouldHandleMissingPictureData() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-789");
        attributes.put("name", "No Picture");

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("should handle empty nested picture structure")
    void shouldHandleEmptyNestedPicture() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-empty");
        attributes.put("picture", new HashMap<>());

        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes, "id");

        OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

        assertThat(info.getProfileImageUrl()).isNull();
    }
}
