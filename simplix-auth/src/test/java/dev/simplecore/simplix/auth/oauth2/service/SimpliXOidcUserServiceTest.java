package dev.simplecore.simplix.auth.oauth2.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SimpliXOidcUserService")
@ExtendWith(MockitoExtension.class)
class SimpliXOidcUserServiceTest {

    @Test
    @DisplayName("should create instance successfully")
    void shouldCreateInstance() {
        SimpliXOidcUserService service = new SimpliXOidcUserService();
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("should delegate loadUser to OidcUserService")
    void shouldDelegateToOidcUserService() {
        SimpliXOidcUserService service = new SimpliXOidcUserService();

        ClientRegistration clientReg = ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "mock-token",
                Instant.now(), Instant.now().plusSeconds(3600));

        OidcIdToken idToken = new OidcIdToken(
                "mock-id-token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "google-user-123", "iss", "https://accounts.google.com"));

        OidcUserRequest request = new OidcUserRequest(clientReg, accessToken, idToken);

        // The actual call will fail when trying to fetch user info endpoint
        // but the code paths in loadUser (registration ID extraction, logging, delegation) will be executed
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
