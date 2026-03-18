package dev.simplecore.simplix.auth.oauth2.config;

import dev.simplecore.simplix.auth.oauth2.extractor.OAuth2UserInfoExtractor;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginFailureHandler;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOAuth2UserService;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOidcUserService;
import dev.simplecore.simplix.auth.oauth2.filter.OAuth2IntentFilter;
import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXOAuth2Configuration")
@ExtendWith(MockitoExtension.class)
class SimpliXOAuth2ConfigurationTest {

    @Mock
    private OAuth2AuthenticationService authService;

    @Mock
    private SimpliXJweTokenProvider tokenProvider;

    private SimpliXAuthProperties authProperties;
    private SimpliXOAuth2Configuration configuration;

    @BeforeEach
    void setUp() {
        authProperties = new SimpliXAuthProperties();
        configuration = new SimpliXOAuth2Configuration(authProperties);
    }

    @Test
    @DisplayName("should create OAuth2UserInfoExtractor bean")
    void shouldCreateOAuth2UserInfoExtractor() {
        OAuth2UserInfoExtractor extractor = configuration.oauth2UserInfoExtractor();
        assertThat(extractor).isNotNull();
    }

    @Test
    @DisplayName("should create SimpliXOAuth2UserService bean")
    void shouldCreateOAuth2UserService() {
        SimpliXOAuth2UserService service = configuration.simplixOAuth2UserService();
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("should create SimpliXOidcUserService bean")
    void shouldCreateOidcUserService() {
        SimpliXOidcUserService service = configuration.simplixOidcUserService();
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("should create OAuth2LoginSuccessHandler bean")
    void shouldCreateSuccessHandler() {
        OAuth2UserInfoExtractor extractor = new OAuth2UserInfoExtractor();
        var handler = configuration.oauth2LoginSuccessHandler(authService, extractor, tokenProvider);
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should create OAuth2LoginFailureHandler bean")
    void shouldCreateFailureHandler() {
        OAuth2LoginFailureHandler handler = configuration.oauth2LoginFailureHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should create OAuth2IntentFilter bean")
    void shouldCreateIntentFilter() {
        OAuth2IntentFilter filter = configuration.oauth2IntentFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    @DisplayName("should create OAuth2IntentFilter registration bean")
    void shouldCreateIntentFilterRegistration() {
        OAuth2IntentFilter filter = new OAuth2IntentFilter(authProperties.getOauth2());
        FilterRegistrationBean<OAuth2IntentFilter> registration =
                configuration.oauth2IntentFilterRegistration(filter);

        assertThat(registration).isNotNull();
        assertThat(registration.getFilter()).isEqualTo(filter);
    }

    @Test
    @DisplayName("extractPath should remove query parameters")
    void shouldExtractPath() throws Exception {
        java.lang.reflect.Method method = SimpliXOAuth2Configuration.class.getDeclaredMethod("extractPath", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(configuration, "/settings?tab=social")).isEqualTo("/settings");
        assertThat(method.invoke(configuration, "/settings")).isEqualTo("/settings");
        assertThat(method.invoke(configuration, "")).isEqualTo("");
        assertThat(method.invoke(configuration, (Object) null)).isNull();
    }
}
