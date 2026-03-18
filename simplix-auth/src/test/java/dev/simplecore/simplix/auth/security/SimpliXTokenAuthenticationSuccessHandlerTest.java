package dev.simplecore.simplix.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXTokenAuthenticationSuccessHandler")
class SimpliXTokenAuthenticationSuccessHandlerTest {

    private SimpliXTokenAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SimpliXTokenAuthenticationSuccessHandler();
    }

    @Test
    @DisplayName("should not write to response")
    void shouldNotWriteToResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "TestAgent");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", null);

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should handle authentication without throwing exception")
    void shouldHandleAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", null);

        handler.onAuthenticationSuccess(request, response, auth);

        // No exception should be thrown
    }
}
