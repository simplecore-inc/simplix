package dev.simplecore.simplix.auth.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

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
        AuthenticationFailureHandler handler = config.authenticationFailureHandler();
        assertThat(handler).isNotNull();
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
