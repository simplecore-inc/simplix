package dev.simplecore.simplix.auth.oauth2.filter;

import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("OAuth2IntentFilter")
@ExtendWith(MockitoExtension.class)
class OAuth2IntentFilterTest {

    @Mock
    private FilterChain filterChain;

    private SimpliXOAuth2Properties properties;
    private OAuth2IntentFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SimpliXOAuth2Properties();
        filter = new OAuth2IntentFilter(properties);
    }

    @Test
    @DisplayName("should set login intent and redirect for login URL")
    void shouldSetLoginIntentAndRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/oauth2/login/google");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuth2IntentFilter.OAUTH2_INTENT_ATTR))
                .isEqualTo(OAuth2IntentFilter.INTENT_LOGIN);
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorize/google");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("should set register intent and redirect for register URL")
    void shouldSetRegisterIntentAndRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/oauth2/register/kakao");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuth2IntentFilter.OAUTH2_INTENT_ATTR))
                .isEqualTo(OAuth2IntentFilter.INTENT_REGISTER);
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorize/kakao");
    }

    @Test
    @DisplayName("should pass through for non-matching URLs")
    void shouldPassThroughForNonMatchingUrls() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not redirect for base login URL without provider")
    void shouldNotRedirectForBaseLoginUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/oauth2/login/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // empty provider should be filtered out
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should have correct constants")
    void shouldHaveCorrectConstants() {
        assertThat(OAuth2IntentFilter.OAUTH2_INTENT_ATTR).isEqualTo("oauth2_intent");
        assertThat(OAuth2IntentFilter.INTENT_LOGIN).isEqualTo("login");
        assertThat(OAuth2IntentFilter.INTENT_REGISTER).isEqualTo("register");
    }
}
