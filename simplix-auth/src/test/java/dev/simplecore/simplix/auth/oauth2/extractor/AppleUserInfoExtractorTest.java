package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppleUserInfoExtractor")
class AppleUserInfoExtractorTest {

    private AppleUserInfoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AppleUserInfoExtractor();
    }

    @Test
    @DisplayName("should return APPLE provider type")
    void shouldReturnAppleProviderType() {
        assertThat(extractor.getProviderType()).isEqualTo(OAuth2ProviderType.APPLE);
    }

    @Nested
    @DisplayName("with OIDC user - first login")
    class WithOidcUserFirstLogin {

        @Test
        @DisplayName("should extract name from attributes on first login")
        void shouldExtractNameFromAttributes() {
            Map<String, Object> nameObj = new HashMap<>();
            nameObj.put("firstName", "John");
            nameObj.put("lastName", "Doe");

            // OIDC ID token claims (name is NOT in the ID token for Apple)
            Map<String, Object> idTokenClaims = new HashMap<>();
            idTokenClaims.put("sub", "apple-001234");
            idTokenClaims.put("email", "user@privaterelay.appleid.com");
            idTokenClaims.put("email_verified", true);
            idTokenClaims.put("iss", "https://appleid.apple.com");
            idTokenClaims.put("aud", List.of("client-id"));
            idTokenClaims.put("iat", Instant.now());
            idTokenClaims.put("exp", Instant.now().plusSeconds(3600));

            OidcIdToken idToken = new OidcIdToken(
                    "token-value", Instant.now(), Instant.now().plusSeconds(3600), idTokenClaims);
            OidcUser oidcUser = new DefaultOidcUser(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

            // Attributes include the name object from Apple's first-login POST body
            Map<String, Object> attributes = new HashMap<>(idTokenClaims);
            attributes.put("name", nameObj);

            OAuth2UserInfo info = extractor.extract(attributes, oidcUser);

            assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.APPLE);
            assertThat(info.getProviderId()).isEqualTo("apple-001234");
            assertThat(info.getEmail()).isEqualTo("user@privaterelay.appleid.com");
            assertThat(info.isEmailVerified()).isTrue();
            assertThat(info.getFirstName()).isEqualTo("John");
            assertThat(info.getLastName()).isEqualTo("Doe");
            assertThat(info.getName()).isEqualTo("John Doe");
        }
    }

    @Nested
    @DisplayName("with OIDC user - subsequent login")
    class WithOidcUserSubsequentLogin {

        @Test
        @DisplayName("should handle missing name on subsequent logins")
        void shouldHandleMissingName() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "apple-001234");
            claims.put("email", "user@privaterelay.appleid.com");
            claims.put("email_verified", true);
            claims.put("iss", "https://appleid.apple.com");
            claims.put("aud", List.of("client-id"));
            claims.put("iat", Instant.now());
            claims.put("exp", Instant.now().plusSeconds(3600));

            OidcIdToken idToken = new OidcIdToken(
                    "token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
            OidcUser oidcUser = new DefaultOidcUser(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

            OAuth2UserInfo info = extractor.extract(claims, oidcUser);

            assertThat(info.getProviderId()).isEqualTo("apple-001234");
            assertThat(info.getName()).isNull();
            assertThat(info.getFirstName()).isNull();
            assertThat(info.getLastName()).isNull();
        }
    }

    @Nested
    @DisplayName("with non-OIDC user (fallback)")
    class WithNonOidcUser {

        @Test
        @DisplayName("should extract from attributes as fallback")
        void shouldExtractFromAttributes() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "apple-fallback");
            attributes.put("email", "fallback@apple.com");
            attributes.put("email_verified", "true");

            OAuth2User oauth2User = new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    attributes, "sub");

            OAuth2UserInfo info = extractor.extract(attributes, oauth2User);

            assertThat(info.getProvider()).isEqualTo(OAuth2ProviderType.APPLE);
            assertThat(info.getProviderId()).isEqualTo("apple-fallback");
            assertThat(info.getEmail()).isEqualTo("fallback@apple.com");
            assertThat(info.isEmailVerified()).isTrue();
        }
    }
}
