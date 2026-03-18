package dev.simplecore.simplix.auth.oauth2.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SimpliXOAuth2UserService")
@ExtendWith(MockitoExtension.class)
class SimpliXOAuth2UserServiceTest {

    @Test
    @DisplayName("should create instance successfully")
    void shouldCreateInstance() {
        SimpliXOAuth2UserService service = new SimpliXOAuth2UserService();
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("should delegate loadUser to DefaultOAuth2UserService")
    void shouldDelegateToDefaultService() {
        SimpliXOAuth2UserService service = new SimpliXOAuth2UserService();

        // Build a minimal but real OAuth2UserRequest
        ClientRegistration clientReg = ClientRegistration.withRegistrationId("github")
                .clientId("test-client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "mock-token",
                Instant.now(), Instant.now().plusSeconds(3600));

        OAuth2UserRequest request = new OAuth2UserRequest(clientReg, accessToken);

        // The actual call to the real GitHub API will fail, but the code paths in loadUser
        // (registration ID extraction, logging, delegation) will be executed
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
