package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthAutoConfiguration")
class SimpliXAuthAutoConfigurationTest {

    private final SimpliXAuthAutoConfiguration config = new SimpliXAuthAutoConfiguration();

    @Test
    @DisplayName("should create default authentication success handler")
    void shouldCreateAuthenticationSuccessHandler() {
        AuthenticationSuccessHandler handler = config.authenticationSuccessHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should create default authentication failure handler")
    void shouldCreateAuthenticationFailureHandler() {
        AuthenticationFailureHandler handler = config.authenticationFailureHandler(new SimpliXAuthProperties());
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("failure handler redirects to the configured login page path")
    void failureHandlerFollowsConfiguredLoginPagePath() {
        SimpliXAuthProperties properties = new SimpliXAuthProperties();
        properties.getSecurity().setLoginPagePath("/backend/login");

        AuthenticationFailureHandler handler = config.authenticationFailureHandler(properties);

        assertThat(handler).isInstanceOf(SimpleUrlAuthenticationFailureHandler.class);
        assertThat(handler).hasFieldOrPropertyWithValue("defaultFailureUrl", "/backend/login?error");
    }

    @Test
    @DisplayName("should create token authentication success handler")
    void shouldCreateTokenAuthSuccessHandler() {
        AuthenticationSuccessHandler handler = config.tokenAuthenticationSuccessHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should create token authentication failure handler")
    void shouldCreateTokenAuthFailureHandler() {
        AuthenticationFailureHandler handler = config.tokenAuthenticationFailureHandler();
        assertThat(handler).isNotNull();
    }
}
