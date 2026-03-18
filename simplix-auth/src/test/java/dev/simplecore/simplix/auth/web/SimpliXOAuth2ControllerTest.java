package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SimpliXOAuth2Controller")
@ExtendWith(MockitoExtension.class)
class SimpliXOAuth2ControllerTest {

    @Mock
    private OAuth2AuthenticationService authService;

    private SimpliXOAuth2Properties properties;
    private SimpliXOAuth2Controller controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        properties = new SimpliXOAuth2Properties();
        controller = new SimpliXOAuth2Controller(properties, authService);
    }

    @Nested
    @DisplayName("linkAccount")
    class LinkAccount {

        @Test
        @DisplayName("should redirect to OAuth2 authorization endpoint")
        void shouldRedirectToAuthorizationEndpoint() {
            // Create UserDetails with getId() method
            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/oauth2/authorize/google");
            assertThat(request.getSession(false)).isNotNull();
            assertThat(request.getSession().getAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR))
                    .isEqualTo("testuser");
        }

        @Test
        @DisplayName("should redirect to failure URL for invalid provider")
        void shouldRedirectToFailureForInvalidProvider() {
            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("invalid-provider", request);

            assertThat(result.getUrl()).contains("error=INVALID_PROVIDER");
        }

        @Test
        @DisplayName("should redirect to failure URL when user not authenticated")
        void shouldRedirectWhenNotAuthenticated() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).contains("error=UNAUTHENTICATED");
        }

        @Test
        @DisplayName("should redirect to failure URL for anonymous user")
        void shouldRedirectForAnonymousUser() {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).contains("error=UNAUTHENTICATED");
        }

        @Test
        @DisplayName("should extract user ID from OAuth2 token via provider connection")
        void shouldExtractUserIdFromOAuth2ViaProviderConnection() {
            OAuth2User oauth2User = new DefaultOAuth2User(
                    Collections.emptyList(),
                    Map.of("sub", "oauth2-sub-123", "email", "user@test.com"),
                    "sub");

            OAuth2AuthenticationToken oauth2Auth = new OAuth2AuthenticationToken(
                    oauth2User, Collections.emptyList(), "google");
            SecurityContextHolder.getContext().setAuthentication(oauth2Auth);

            when(authService.findUserIdByProviderConnection(OAuth2ProviderType.GOOGLE, "oauth2-sub-123"))
                    .thenReturn("user-42");

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/oauth2/authorize/google");
            assertThat(request.getSession().getAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR))
                    .isEqualTo("user-42");
        }

        @Test
        @DisplayName("should fall back to email when provider connection not found")
        void shouldFallbackToEmail() {
            OAuth2User oauth2User = new DefaultOAuth2User(
                    Collections.emptyList(),
                    Map.of("sub", "sub-123", "email", "user@example.com"),
                    "sub");

            OAuth2AuthenticationToken oauth2Auth = new OAuth2AuthenticationToken(
                    oauth2User, Collections.emptyList(), "google");
            SecurityContextHolder.getContext().setAuthentication(oauth2Auth);

            when(authService.findUserIdByProviderConnection(any(), anyString())).thenReturn(null);
            when(authService.findUserIdByEmail("user@example.com")).thenReturn("user-99");

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/oauth2/authorize/google");
            assertThat(request.getSession().getAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR))
                    .isEqualTo("user-99");
        }

        @Test
        @DisplayName("should handle OAuth2 user without email attribute")
        void shouldHandleOAuth2UserWithoutEmail() {
            OAuth2User oauth2User = new DefaultOAuth2User(
                    Collections.emptyList(),
                    Map.of("sub", "sub-123"),
                    "sub");

            OAuth2AuthenticationToken oauth2Auth = new OAuth2AuthenticationToken(
                    oauth2User, Collections.emptyList(), "google");
            SecurityContextHolder.getContext().setAuthentication(oauth2Auth);

            when(authService.findUserIdByProviderConnection(any(), anyString())).thenReturn(null);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            // Should still work - falls back to INVALID_PRINCIPAL or continues
            assertThat(result.getUrl()).isNotNull();
        }

        @Test
        @DisplayName("should handle custom authorization base URL")
        void shouldHandleCustomAuthorizationBaseUrl() {
            properties.setAuthorizationBaseUrl("/custom/oauth2/auth");

            UserDetails user = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/custom/oauth2/auth/google");
        }
    }

    @Nested
    @DisplayName("linkAccount - extractUserId edge cases")
    class ExtractUserIdEdgeCases {

        @Test
        @DisplayName("should extract user ID from principal with getId() method")
        void shouldExtractUserIdFromGetId() {
            // Create a custom UserDetails with getId()
            var principal = new UserDetailsWithId("user-42", "testuser");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/oauth2/authorize/google");
            assertThat(request.getSession().getAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR))
                    .isEqualTo("user-42");
        }

        @Test
        @DisplayName("should extract user ID from principal with getUserId() method")
        void shouldExtractUserIdFromGetUserId() {
            var principal = new UserDetailsWithGetUserId("user-77", "testuser");
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/oauth2/authorize/google");
            assertThat(request.getSession().getAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR))
                    .isEqualTo("user-77");
        }

        @Test
        @DisplayName("should handle findUserIdByEmail returning null")
        void shouldHandleEmailLookupReturningNull() {
            OAuth2User oauth2User = new DefaultOAuth2User(
                    Collections.emptyList(),
                    Map.of("sub", "sub-999", "email", "nofound@example.com"),
                    "sub");

            OAuth2AuthenticationToken oauth2Auth = new OAuth2AuthenticationToken(
                    oauth2User, Collections.emptyList(), "google");
            SecurityContextHolder.getContext().setAuthentication(oauth2Auth);

            when(authService.findUserIdByProviderConnection(any(), anyString())).thenReturn(null);
            when(authService.findUserIdByEmail("nofound@example.com")).thenReturn(null);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            // When no user ID can be extracted, should redirect to failure
            assertThat(result.getUrl()).contains("error=INVALID_PRINCIPAL");
        }

        @Test
        @DisplayName("should handle findUserIdByEmail throwing exception")
        void shouldHandleEmailLookupException() {
            OAuth2User oauth2User = new DefaultOAuth2User(
                    Collections.emptyList(),
                    Map.of("sub", "sub-999", "email", "err@example.com"),
                    "sub");

            OAuth2AuthenticationToken oauth2Auth = new OAuth2AuthenticationToken(
                    oauth2User, Collections.emptyList(), "google");
            SecurityContextHolder.getContext().setAuthentication(oauth2Auth);

            when(authService.findUserIdByProviderConnection(any(), anyString())).thenReturn(null);
            when(authService.findUserIdByEmail("err@example.com")).thenThrow(new RuntimeException("DB error"));

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).contains("error=INVALID_PRINCIPAL");
        }

        @Test
        @DisplayName("should handle provider connection lookup throwing exception")
        void shouldHandleProviderConnectionException() {
            OAuth2User oauth2User = new DefaultOAuth2User(
                    Collections.emptyList(),
                    Map.of("sub", "sub-999", "email", "user@example.com"),
                    "sub");

            OAuth2AuthenticationToken oauth2Auth = new OAuth2AuthenticationToken(
                    oauth2User, Collections.emptyList(), "google");
            SecurityContextHolder.getContext().setAuthentication(oauth2Auth);

            when(authService.findUserIdByProviderConnection(any(), anyString()))
                    .thenThrow(new RuntimeException("DB error"));
            when(authService.findUserIdByEmail("user@example.com")).thenReturn("user-fallback");

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("/oauth2/authorize/google");
            assertThat(request.getSession().getAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR))
                    .isEqualTo("user-fallback");
        }

        @Test
        @DisplayName("should handle appendQueryParam with existing query params")
        void shouldAppendToExistingQueryParams() throws Exception {
            properties.setLinkFailureUrl("/settings?tab=social");

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            // Should append with & since URL already has ?
            assertThat(result.getUrl()).contains("&error=UNAUTHENTICATED");
        }

        @Test
        @DisplayName("should handle null linkFailureUrl in appendQueryParam")
        void shouldHandleNullLinkFailureUrl() throws Exception {
            properties.setLinkFailureUrl(null);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            MockHttpServletRequest request = new MockHttpServletRequest();

            RedirectView result = controller.linkAccount("google", request);

            assertThat(result.getUrl()).isEqualTo("?error=UNAUTHENTICATED");
        }
    }

    /**
     * Custom UserDetails with getId() method for testing extractUserId reflection.
     */
    private static class UserDetailsWithId implements org.springframework.security.core.userdetails.UserDetails {
        private final String id;
        private final String username;

        UserDetailsWithId(String id, String username) {
            this.id = id;
            this.username = username;
        }

        public String getId() { return id; }
        @Override public String getUsername() { return username; }
        @Override public String getPassword() { return "pass"; }
        @Override public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }
    }

    /**
     * Custom UserDetails with getUserId() method (but no getId()).
     */
    private static class UserDetailsWithGetUserId implements org.springframework.security.core.userdetails.UserDetails {
        private final String userId;
        private final String username;

        UserDetailsWithGetUserId(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public String getUserId() { return userId; }
        @Override public String getUsername() { return username; }
        @Override public String getPassword() { return "pass"; }
        @Override public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }
    }

    @Nested
    @DisplayName("authorize")
    class Authorize {

        @Test
        @DisplayName("should exist as no-op for Swagger documentation")
        void shouldExistAsNoOp() {
            // This method is a no-op, just verify it does not throw
            controller.authorize("google");
        }
    }

    @Nested
    @DisplayName("callback")
    class Callback {

        @Test
        @DisplayName("should exist as no-op for Swagger documentation")
        void shouldExistAsNoOp() {
            // This method is a no-op, just verify it does not throw
            controller.callback("google");
        }
    }
}
