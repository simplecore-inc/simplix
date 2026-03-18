package dev.simplecore.simplix.auth.oauth2.config;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.extractor.OAuth2UserInfoExtractor;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOAuth2UserService;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOidcUserService;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXOAuth2AutoConfiguration")
@ExtendWith(MockitoExtension.class)
class SimpliXOAuth2AutoConfigurationTest {

    @Mock
    private OAuth2AuthenticationService authService;

    @Mock
    private SimpliXJweTokenProvider tokenProvider;

    private final SimpliXOAuth2AutoConfiguration config = new SimpliXOAuth2AutoConfiguration();

    @Test
    @DisplayName("should create OAuth2UserInfoExtractor bean")
    void shouldCreateExtractor() {
        OAuth2UserInfoExtractor extractor = config.oauth2UserInfoExtractor();
        assertThat(extractor).isNotNull();
    }

    @Test
    @DisplayName("should create SimpliXOAuth2UserService bean")
    void shouldCreateOAuth2UserService() {
        SimpliXOAuth2UserService service = config.simplixOAuth2UserService();
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("should create SimpliXOidcUserService bean")
    void shouldCreateOidcUserService() {
        SimpliXOidcUserService service = config.simplixOidcUserService();
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("should create OAuth2LoginSuccessHandler bean")
    void shouldCreateSuccessHandler() {
        SimpliXAuthProperties properties = new SimpliXAuthProperties();
        OAuth2UserInfoExtractor extractor = new OAuth2UserInfoExtractor();

        var handler = config.oauth2LoginSuccessHandler(authService, extractor, tokenProvider, properties);
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should create OAuth2LoginFailureHandler bean")
    void shouldCreateFailureHandler() {
        SimpliXAuthProperties properties = new SimpliXAuthProperties();
        var handler = config.oauth2LoginFailureHandler(properties);
        assertThat(handler).isNotNull();
    }
}
