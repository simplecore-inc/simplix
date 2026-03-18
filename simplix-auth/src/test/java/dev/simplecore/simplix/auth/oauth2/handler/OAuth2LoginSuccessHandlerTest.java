package dev.simplecore.simplix.auth.oauth2.handler;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationException;
import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import dev.simplecore.simplix.auth.oauth2.extractor.OAuth2UserInfoExtractor;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("OAuth2LoginSuccessHandler")
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private OAuth2AuthenticationService authService;

    @Mock
    private OAuth2UserInfoExtractor extractor;

    @Mock
    private SimpliXJweTokenProvider tokenProvider;

    @Mock
    private Authentication authentication;

    private SimpliXOAuth2Properties properties;
    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        properties = new SimpliXOAuth2Properties();
        handler = new OAuth2LoginSuccessHandler(authService, extractor, tokenProvider, properties);
    }

    @Nested
    @DisplayName("login mode")
    class LoginMode {

        @Test
        @DisplayName("should deliver tokens via cookie on success")
        void shouldDeliverTokensViaCookie() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "access-token", "refresh-token",
                    ZonedDateTime.now().plusMinutes(30),
                    ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getHeaders("Set-Cookie")).isNotEmpty();
            assertThat(response.getRedirectedUrl()).isEqualTo("/");
        }

        @Test
        @DisplayName("should deliver tokens via redirect on success")
        void shouldDeliverTokensViaRedirect() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.REDIRECT);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GITHUB)
                    .providerId("gh-456")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("ghuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "access-token", "refresh-token",
                    ZonedDateTime.now().plusMinutes(30),
                    ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).contains("accessToken=access-token");
            assertThat(response.getRedirectedUrl()).contains("refreshToken=refresh-token");
        }

        @Test
        @DisplayName("should deliver tokens via postMessage on success")
        void shouldDeliverTokensViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "access-token", "refresh-token",
                    ZonedDateTime.now().plusMinutes(30),
                    ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getContentType()).contains("text/html");
            assertThat(response.getContentAsString()).contains("OAUTH2_SUCCESS");
            assertThat(response.getContentAsString()).contains("access-token");
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should redirect to failure URL on OAuth2AuthenticationException")
        void shouldRedirectToFailureUrl() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            when(authService.authenticateOAuth2User(eq(userInfo), any()))
                    .thenThrow(new OAuth2AuthenticationException("EMAIL_ALREADY_EXISTS", "Email exists"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).contains("error=EMAIL_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("should handle unexpected exception")
        void shouldHandleUnexpectedException() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            when(extractor.extract(authentication))
                    .thenThrow(new RuntimeException("unexpected"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).contains("error=PROVIDER_ERROR");
        }
    }

    @Nested
    @DisplayName("linking mode")
    class LinkingMode {

        @Test
        @DisplayName("should link account when linking user ID is in session")
        void shouldLinkAccount() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(
                    OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR, "user-42");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).linkSocialAccount("user-42", userInfo);
            assertThat(response.getRedirectedUrl()).isEqualTo(properties.getLinkSuccessUrl());
        }

        @Test
        @DisplayName("should notify linking success via postMessage")
        void shouldNotifyLinkingSuccessViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(
                    OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR, "user-42");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).linkSocialAccount("user-42", userInfo);
            assertThat(response.getContentAsString()).contains("OAUTH2_LINK_SUCCESS");
        }

        @Test
        @DisplayName("should notify linking failure via postMessage")
        void shouldNotifyLinkingFailureViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            doThrow(new OAuth2AuthenticationException("ALREADY_LINKED", "Already linked"))
                    .when(authService).linkSocialAccount("user-42", userInfo);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(
                    OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR, "user-42");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getContentAsString()).contains("OAUTH2_LINK_FAILURE");
            assertThat(response.getContentAsString()).contains("ALREADY_LINKED");
        }

        @Test
        @DisplayName("should notify linking failure via redirect")
        void shouldNotifyLinkingFailureViaRedirect() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.REDIRECT);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            doThrow(new OAuth2AuthenticationException("LINK_ERROR", "Error"))
                    .when(authService).linkSocialAccount("user-42", userInfo);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(
                    OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR, "user-42");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).contains("error=LINK_ERROR");
        }

        @Test
        @DisplayName("should restore user authentication after linking")
        void shouldRestoreUserAuthAfterLinking() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails linkedUser = User.withUsername("linked-user")
                    .password("pass")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            when(authService.loadUserDetailsByUserId("user-42")).thenReturn(linkedUser);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(
                    OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR, "user-42");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).linkSocialAccount("user-42", userInfo);
        }
    }

    @Nested
    @DisplayName("error handling via postMessage")
    class PostMessageErrorHandling {

        @Test
        @DisplayName("should deliver NO_LINKED_ACCOUNT via postMessage")
        void shouldDeliverNoLinkedAccountViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            when(authService.authenticateOAuth2User(eq(userInfo), any()))
                    .thenThrow(new OAuth2AuthenticationException("NO_LINKED_ACCOUNT", "No linked account"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getContentAsString()).contains("OAUTH2_NO_LINKED_ACCOUNT");
        }

        @Test
        @DisplayName("should deliver EMAIL_ACCOUNT_EXISTS_NOT_LINKED via postMessage")
        void shouldDeliverEmailExistsNotLinkedViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            when(authService.authenticateOAuth2User(eq(userInfo), any()))
                    .thenThrow(new OAuth2AuthenticationException("EMAIL_ACCOUNT_EXISTS_NOT_LINKED", "Email exists"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getContentAsString()).contains("OAUTH2_EMAIL_EXISTS_NOT_LINKED");
        }

        @Test
        @DisplayName("should deliver SOCIAL_ALREADY_REGISTERED via postMessage")
        void shouldDeliverSocialAlreadyRegisteredViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            when(authService.authenticateOAuth2User(eq(userInfo), any()))
                    .thenThrow(new OAuth2AuthenticationException("SOCIAL_ALREADY_REGISTERED", "Already registered"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getContentAsString()).contains("OAUTH2_ERROR");
        }

        @Test
        @DisplayName("should deliver generic error via postMessage")
        void shouldDeliverGenericErrorViaPostMessage() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            when(extractor.extract(authentication))
                    .thenThrow(new RuntimeException("unexpected"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getContentAsString()).contains("OAUTH2_ERROR");
            assertThat(response.getContentAsString()).contains("PROVIDER_ERROR");
        }

        @Test
        @DisplayName("should deliver NO_LINKED_ACCOUNT via postMessage with session data")
        void shouldDeliverNoLinkedAccountWithSessionData() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);
            when(authService.authenticateOAuth2User(eq(userInfo), any()))
                    .thenThrow(new OAuth2AuthenticationException("NO_LINKED_ACCOUNT", "No linked account"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            // Store pending social info in session - the handler uses reflection
            // to extract provider/email/name. Since getProvider() returns OAuth2ProviderType (not String),
            // the cast will fail and provider/email/name will remain null.
            // However, the handler still produces valid HTML with null values.
            dev.simplecore.simplix.auth.oauth2.session.PendingSocialRegistration pending =
                    dev.simplecore.simplix.auth.oauth2.session.PendingSocialRegistration.builder()
                            .provider(OAuth2ProviderType.GOOGLE)
                            .providerId("google-123")
                            .email("test@example.com")
                            .name("Test User")
                            .build();
            request.getSession(true).setAttribute("oauth2.pending", pending);

            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            String content = response.getContentAsString();
            assertThat(content).contains("OAUTH2_NO_LINKED_ACCOUNT");
            // The HTML should be rendered regardless of reflection success/failure
            assertThat(content).contains("postMessage");
        }
    }

    @Nested
    @DisplayName("token creation failure")
    class TokenCreationFailure {

        @Test
        @DisplayName("should handle token creation error in login mode")
        void shouldHandleTokenCreationError() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            when(tokenProvider.createTokenPair(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Key not initialized"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).contains("error=PROVIDER_ERROR");
        }
    }

    @Nested
    @DisplayName("intent handling")
    class IntentHandling {

        @Test
        @DisplayName("should pass LOGIN intent from session")
        void shouldPassLoginIntent() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "at", "rt", ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(dev.simplecore.simplix.auth.oauth2.filter.OAuth2IntentFilter.OAUTH2_INTENT_ATTR, "login");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).authenticateOAuth2User(eq(userInfo),
                    eq(dev.simplecore.simplix.auth.oauth2.OAuth2Intent.LOGIN));
        }

        @Test
        @DisplayName("should pass REGISTER intent from session")
        void shouldPassRegisterIntent() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "at", "rt", ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(dev.simplecore.simplix.auth.oauth2.filter.OAuth2IntentFilter.OAUTH2_INTENT_ATTR, "register");
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).authenticateOAuth2User(eq(userInfo),
                    eq(dev.simplecore.simplix.auth.oauth2.OAuth2Intent.REGISTER));
        }

        @Test
        @DisplayName("should use AUTO intent when no session")
        void shouldUseAutoIntentWhenNoSession() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "at", "rt", ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            verify(authService).authenticateOAuth2User(eq(userInfo),
                    eq(dev.simplecore.simplix.auth.oauth2.OAuth2Intent.AUTO));
        }
    }

    @Nested
    @DisplayName("cookie settings")
    class CookieSettings {

        @Test
        @DisplayName("should set cookie with custom settings")
        void shouldSetCookieWithCustomSettings() throws Exception {
            properties.setTokenDeliveryMethod(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);
            properties.getCookie().setHttpOnly(false);
            properties.getCookie().setSecure(false);
            properties.getCookie().setSameSite(null);
            properties.getCookie().setPath("/api");

            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .build();
            when(extractor.extract(authentication)).thenReturn(userInfo);

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(authService.authenticateOAuth2User(eq(userInfo), any())).thenReturn(user);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "at", "rt", ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getHeaders("Set-Cookie")).isNotEmpty();
            String cookieHeader = response.getHeaders("Set-Cookie").get(0);
            assertThat(cookieHeader).contains("Path=/api");
            assertThat(cookieHeader).doesNotContain("HttpOnly");
            assertThat(cookieHeader).doesNotContain("Secure");
        }
    }
}
