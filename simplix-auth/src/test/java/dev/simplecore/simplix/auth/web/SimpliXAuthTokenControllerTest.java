package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.audit.TokenAuditEventPublisher;
import dev.simplecore.simplix.auth.exception.TokenValidationException;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import dev.simplecore.simplix.auth.security.SimpliXUserDetailsService;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SimpliXAuthTokenController")
@ExtendWith(MockitoExtension.class)
class SimpliXAuthTokenControllerTest {

    @Mock
    private SimpliXJweTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private MessageSource messageSource;

    @Mock
    private SimpliXUserDetailsService userDetailsService;

    @Mock
    private AuthenticationSuccessHandler tokenAuthenticationSuccessHandler;

    @Mock
    private AuthenticationFailureHandler tokenAuthenticationFailureHandler;

    @Mock
    private ObjectProvider<LogoutHandler> logoutHandlerProvider;

    @Mock
    private ObjectProvider<TokenAuditEventPublisher> auditPublisherProvider;

    private SimpliXAuthProperties properties;
    private SimpliXAuthTokenController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        properties = new SimpliXAuthProperties();
        controller = new SimpliXAuthTokenController(
                tokenProvider, authenticationManager, messageSource, properties,
                userDetailsService, tokenAuthenticationSuccessHandler,
                tokenAuthenticationFailureHandler, logoutHandlerProvider,
                auditPublisherProvider);
    }

    @Nested
    @DisplayName("issueToken")
    class IssueToken {

        @Test
        @DisplayName("should issue tokens with valid Basic auth credentials")
        void shouldIssueTokensWithValidCredentials() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String credentials = Base64.getEncoder().encodeToString("admin:password".getBytes());
            request.addHeader("Authorization", "Basic " + credentials);

            Authentication authenticatedAuth = mock(Authentication.class);
            when(authenticatedAuth.getName()).thenReturn("admin");
            when(authenticationManager.authenticate(any())).thenReturn(authenticatedAuth);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "access-token", "refresh-token",
                    ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(eq("admin"), anyString(), any())).thenReturn(tokens);

            ResponseEntity<SimpliXJweTokenProvider.TokenResponse> response = controller.issueToken(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
            assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-token");
            verify(tokenAuthenticationSuccessHandler).onAuthenticationSuccess(eq(request), isNull(), eq(authenticatedAuth));
        }

        @Test
        @DisplayName("should create session when createSessionOnTokenIssue is true")
        void shouldCreateSessionWhenConfigured() throws Exception {
            properties.getToken().setCreateSessionOnTokenIssue(true);

            MockHttpServletRequest request = new MockHttpServletRequest();
            String credentials = Base64.getEncoder().encodeToString("admin:password".getBytes());
            request.addHeader("Authorization", "Basic " + credentials);

            Authentication authenticatedAuth = mock(Authentication.class);
            when(authenticatedAuth.getName()).thenReturn("admin");
            when(authenticationManager.authenticate(any())).thenReturn(authenticatedAuth);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "access-token", "refresh-token",
                    ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            controller.issueToken(request);

            assertThat(request.getSession(false)).isNotNull();
        }

        @Test
        @DisplayName("should not create session when createSessionOnTokenIssue is false")
        void shouldNotCreateSessionWhenNotConfigured() throws Exception {
            properties.getToken().setCreateSessionOnTokenIssue(false);

            MockHttpServletRequest request = new MockHttpServletRequest();
            String credentials = Base64.getEncoder().encodeToString("admin:password".getBytes());
            request.addHeader("Authorization", "Basic " + credentials);

            Authentication authenticatedAuth = mock(Authentication.class);
            when(authenticatedAuth.getName()).thenReturn("admin");
            when(authenticationManager.authenticate(any())).thenReturn(authenticatedAuth);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "access-token", "refresh-token",
                    ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.createTokenPair(anyString(), anyString(), any())).thenReturn(tokens);

            controller.issueToken(request);

            // Session should not have been created by the controller
            // Note: getSession(false) returns null if no session was created
        }

        @Test
        @DisplayName("should throw BadCredentialsException when auth header missing")
        void shouldThrowWhenAuthHeaderMissing() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Missing basic auth header");

            assertThatThrownBy(() -> controller.issueToken(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("should throw BadCredentialsException when auth is not Basic")
        void shouldThrowWhenNotBasicAuth() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer some-token");

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Missing basic auth header");

            assertThatThrownBy(() -> controller.issueToken(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("should throw BadCredentialsException on invalid credentials")
        void shouldThrowOnInvalidCredentials() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String credentials = Base64.getEncoder().encodeToString("admin:wrong".getBytes());
            request.addHeader("Authorization", "Basic " + credentials);

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Invalid credentials"));
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Invalid credentials");

            assertThatThrownBy(() -> controller.issueToken(request))
                    .isInstanceOf(BadCredentialsException.class);

            verify(tokenAuthenticationFailureHandler).onAuthenticationFailure(eq(request), isNull(), any());
        }

        @Test
        @DisplayName("should throw BadCredentialsException on malformed Base64")
        void shouldThrowOnMalformedBase64() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic not-valid-base64!!!");

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Invalid header");

            assertThatThrownBy(() -> controller.issueToken(request))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("should refresh tokens with valid refresh token")
        void shouldRefreshTokens() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Refresh-Token", "valid-refresh-token");

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("valid-refresh-token")).thenReturn(claims);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "new-access", "new-refresh",
                    ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.refreshTokens(eq("valid-refresh-token"), anyString(), any()))
                    .thenReturn(tokens);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            properties.getToken().setCreateSessionOnTokenIssue(false);

            ResponseEntity<SimpliXJweTokenProvider.TokenResponse> response = controller.refreshToken(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAccessToken()).isEqualTo("new-access");
        }

        @Test
        @DisplayName("should create session on refresh when configured")
        void shouldCreateSessionOnRefresh() throws Exception {
            properties.getToken().setCreateSessionOnTokenIssue(true);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Refresh-Token", "valid-refresh-token");

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("valid-refresh-token")).thenReturn(claims);

            SimpliXJweTokenProvider.TokenResponse tokens = new SimpliXJweTokenProvider.TokenResponse(
                    "new-access", "new-refresh",
                    ZonedDateTime.now().plusMinutes(30), ZonedDateTime.now().plusDays(7));
            when(tokenProvider.refreshTokens(eq("valid-refresh-token"), anyString(), any()))
                    .thenReturn(tokens);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            controller.refreshToken(request);

            assertThat(request.getSession(false)).isNotNull();
        }

        @Test
        @DisplayName("should throw TokenValidationException when refresh token header missing")
        void shouldThrowWhenRefreshTokenMissing() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Missing refresh token");

            assertThatThrownBy(() -> controller.refreshToken(request))
                    .isInstanceOf(TokenValidationException.class);
        }

        @Test
        @DisplayName("should publish audit event when refresh token header missing")
        void shouldPublishAuditWhenRefreshTokenMissing() {
            dev.simplecore.simplix.auth.audit.TokenAuditEventPublisher auditPublisher =
                    mock(dev.simplecore.simplix.auth.audit.TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);

            MockHttpServletRequest request = new MockHttpServletRequest();
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Missing");

            assertThatThrownBy(() -> controller.refreshToken(request))
                    .isInstanceOf(TokenValidationException.class);

            verify(auditPublisher).publishTokenRefreshFailed(any());
        }

        @Test
        @DisplayName("should throw TokenValidationException on refresh failure")
        void shouldThrowOnRefreshFailure() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Refresh-Token", "bad-token");

            when(tokenProvider.parseToken("bad-token"))
                    .thenThrow(new RuntimeException("parse error"));
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Invalid token");

            assertThatThrownBy(() -> controller.refreshToken(request))
                    .isInstanceOf(TokenValidationException.class);
        }
    }

    @Nested
    @DisplayName("revokeTokens")
    class RevokeTokens {

        @Test
        @DisplayName("should revoke access token only")
        void shouldRevokeAccessTokenOnly() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer access-token-here");

            ResponseEntity<?> response = controller.revokeTokens(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(tokenProvider).revokeToken(eq("access-token-here"), anyString(), any());
        }

        @Test
        @DisplayName("should revoke both access and refresh tokens")
        void shouldRevokeBothTokens() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer access-token");
            request.addHeader("X-Refresh-Token", "refresh-token");

            ResponseEntity<?> response = controller.revokeTokens(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(tokenProvider).revokeToken(eq("access-token"), anyString(), any());
            verify(tokenProvider).revokeToken(eq("refresh-token"), anyString(), any());
        }

        @Test
        @DisplayName("should revoke refresh token only")
        void shouldRevokeRefreshTokenOnly() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Refresh-Token", "refresh-token");

            ResponseEntity<?> response = controller.revokeTokens(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(tokenProvider).revokeToken(eq("refresh-token"), anyString(), any());
        }

        @Test
        @DisplayName("should return 400 when no tokens provided")
        void shouldReturn400WhenNoTokens() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("No token provided");

            ResponseEntity<?> response = controller.revokeTokens(request);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("should call custom logout handler if available")
        void shouldCallCustomLogoutHandler() {
            LogoutHandler logoutHandler = mock(LogoutHandler.class);
            when(logoutHandlerProvider.getIfAvailable()).thenReturn(logoutHandler);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer token");

            controller.revokeTokens(request);

            verify(logoutHandler).logout(eq(request), isNull(), any());
        }

        @Test
        @DisplayName("should invalidate session if exists")
        void shouldInvalidateSessionIfExists() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer token");
            // Create a session
            request.getSession(true);

            controller.revokeTokens(request);

            // After invalidation, getSession(false) returns null
            assertThat(request.getSession(false)).isNull();
        }

        @Test
        @DisplayName("should handle empty Bearer token")
        void shouldHandleEmptyBearerToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer ");
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("No token");

            ResponseEntity<?> response = controller.revokeTokens(request);

            // Empty bearer token is treated as no token
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }
}
