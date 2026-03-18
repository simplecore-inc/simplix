package dev.simplecore.simplix.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXTokenAuthenticationFailureHandler")
class SimpliXTokenAuthenticationFailureHandlerTest {

    private SimpliXTokenAuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SimpliXTokenAuthenticationFailureHandler();
    }

    @Test
    @DisplayName("should not write to response")
    void shouldNotWriteToResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "TestAgent");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        // Response should remain unmodified
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should handle request with Basic auth header")
    void shouldHandleBasicAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String credentials = Base64.getEncoder().encodeToString("testuser:password".getBytes());
        request.addHeader("Authorization", "Basic " + credentials);

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        // Should not throw exception
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should handle request without auth header")
    void shouldHandleWithoutAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should handle Bearer token header (not Basic)")
    void shouldHandleBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should handle invalid Base64 in Basic auth header")
    void shouldHandleInvalidBase64() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic not-valid-base64!!!");

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        // Should not throw exception
        assertThat(response.getContentAsString()).isEmpty();
    }
}
