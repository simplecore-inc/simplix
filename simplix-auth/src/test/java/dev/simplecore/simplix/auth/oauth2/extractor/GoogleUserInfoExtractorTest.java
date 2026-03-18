package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoogleUserInfoExtractor")
class GoogleUserInfoExtractorTest {

    private GoogleUserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new GoogleUserInfoExtractor();
    }

    @Test
    @DisplayName("should return GOOGLE provider type")
    void shouldReturnGoogleProviderType() {
        assertThat(extractor.getProviderType()).isEqualTo(OAuth2ProviderType.GOOGLE);
    }

    @Nested
    @DisplayName("with OIDC user")
    class WithOidcUser {

        @Test
        @DisplayName("should extract user info from OIDC claims")
        void shouldExtractFromOidcClaims() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "google-123");
            claims.put("email", "user@gmail.com");
            claims.put("email_verified", true);
            claims.put("name", "John Doe");
            claims.put("given_name", "John");
            claims.put("family_name", "Doe");
            claims.put("picture", "https://example.com/photo.jpg");
            claims.put("locale", "en");
            claims.put("iss", "https://accounts.google.com");
            claims.put("aud", List.of("client-id"));
            claims.put("iat", Instant.now());
            claims.put("exp", Instant.now().plusSeconds(3600));

            OidcIdToken idToken = new OidcIdToken(
                    "token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
            OidcUser oidcUser = new DefaultOidcUser(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

            OAuth2UserInfo info = extractor.extract(claims, oidcUser);

            assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.GOOGLE);
            assertThat(info.getProviderId()).isEqualTo("google-123");
            assertThat(info.getEmail()).isEqualTo("user@gmail.com");
            assertThat(info.isEmailVerified()).isTrue();
            assertThat(info.getName()).isEqualTo("John Doe");
            assertThat(info.getFirstName()).isEqualTo("John");
            assertThat(info.getLastName()).isEqualTo("Doe");
            assertThat(info.getProfileImageUrl()).isEqualTo("https://example.com/photo.jpg");
            assertThat(info.getLocale()).isEqualTo("en");
        }
    }

    @Nested
    @DisplayName("with non-OIDC OAuth2 user")
    class WithOAuth2User {

        @Test
        @DisplayName("should extract user info from attributes")
        void shouldExtractFromAttributes() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-456");
            attributes.put("email", "test@gmail.com");
            attributes.put("email_verified", true);
            attributes.put("name", "Jane Smith");
            attributes.put("given_name", "Jane");
            attributes.put("family_name", "Smith");
            attributes.put("picture", "https://example.com/jane.jpg");
            attributes.put("locale", "ko");

            OAuth2User oauth2User = new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    attributes, "sub");

            OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

            assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.GOOGLE);
            assertThat(info.getProviderId()).isEqualTo("google-456");
            assertThat(info.getEmail()).isEqualTo("test@gmail.com");
            assertThat(info.isEmailVerified()).isTrue();
            assertThat(info.getName()).isEqualTo("Jane Smith");
            assertThat(info.getFirstName()).isEqualTo("Jane");
            assertThat(info.getLastName()).isEqualTo("Smith");
            assertThat(info.getProfileImageUrl()).isEqualTo("https://example.com/jane.jpg");
            assertThat(info.getLocale()).isEqualTo("ko");
        }

        @Test
        @DisplayName("should handle missing optional fields")
        void shouldHandleMissingFields() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-789");

            OAuth2User oauth2User = new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    attributes, "sub");

            OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

            assertThat(info.getProviderId()).isEqualTo("google-789");
            assertThat(info.getEmail()).isNull();
            assertThat(info.isEmailVerified()).isFalse();
            assertThat(info.getName()).isNull();
        }
    }
}
