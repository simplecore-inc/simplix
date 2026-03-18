package dev.simplecore.simplix.auth.oauth2.handler;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationException;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2LoginFailureHandler")
class OAuth2LoginFailureHandlerTest {

    private SimpliXOAuth2Properties properties;
    private OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        properties = new SimpliXOAuth2Properties();
        handler = new OAuth2LoginFailureHandler(properties);
    }

    @Test
    @DisplayName("should redirect with custom error code for OAuth2AuthenticationException")
    void shouldRedirectWithCustomErrorCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                "EMAIL_ALREADY_EXISTS", "Email already exists");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .contains("error=EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("should redirect with Spring OAuth2 error code")
    void shouldRedirectWithSpringOAuth2ErrorCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception =
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .contains("error=invalid_token");
    }

    @Test
    @DisplayName("should redirect with PROVIDER_ERROR for generic exceptions")
    void shouldRedirectWithProviderError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException exception = new BadCredentialsException("bad credentials");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .contains("error=PROVIDER_ERROR");
    }

    @Test
    @DisplayName("should redirect to configured failure URL")
    void shouldRedirectToConfiguredFailureUrl() throws Exception {
        properties.setFailureUrl("/custom-error");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                "TEST_ERROR", "test");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl())
                .startsWith("/custom-error")
                .contains("error=TEST_ERROR");
    }
}
