package dev.simplecore.simplix.auth.security;

import com.nimbusds.jwt.JWTClaimsSet;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("SimpliXTokenAuthenticationFilter")
@ExtendWith(MockitoExtension.class)
class SimpliXTokenAuthenticationFilterTest {

    @Mock
    private SimpliXJweTokenProvider tokenProvider;

    @Mock
    private SimpliXUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private SimpliXAuthProperties properties;
    private SimpliXTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        properties = new SimpliXAuthProperties();
        filter = new SimpliXTokenAuthenticationFilter(tokenProvider, userDetailsService, properties);
    }

    @Nested
    @DisplayName("token extraction")
    class TokenExtraction {

        @Test
        @DisplayName("should extract token from Authorization header")
        void shouldExtractFromAuthorizationHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer test-token");
            request.addHeader("User-Agent", "TestAgent");
            MockHttpServletResponse response = new MockHttpServletResponse();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("test-token")).thenReturn(claims);
            when(tokenProvider.validateToken(eq("test-token"), anyString(), anyString())).thenReturn(true);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("testuser");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass through when no token present")
        void shouldPassThroughWhenNoToken() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("permit-all patterns")
    class PermitAllPatterns {

        @Test
        @DisplayName("should skip authentication for permit-all paths")
        void shouldSkipForPermitAllPaths() throws Exception {
            properties.getSecurity().setPermitAllPatterns(new String[]{"/api/public/**"});

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/public/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(tokenProvider);
        }
    }

    @Nested
    @DisplayName("token validation failure")
    class TokenValidationFailure {

        @Test
        @DisplayName("should clear context on generic exception")
        void shouldClearContextOnException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer bad-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(tokenProvider.parseToken("bad-token"))
                    .thenThrow(new RuntimeException("parse error"));

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should set 403 and throw TokenValidationException on validation error")
        void shouldThrowTokenValidationException() throws Exception {
            properties.getSecurity().setPreferTokenOverSession(true);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer test-token");
            request.addHeader("User-Agent", "TestAgent");
            MockHttpServletResponse response = new MockHttpServletResponse();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("test-token")).thenReturn(claims);
            when(tokenProvider.validateToken(eq("test-token"), anyString(), anyString()))
                    .thenThrow(new dev.simplecore.simplix.auth.exception.TokenValidationException("Token expired", "Details"));

            org.junit.jupiter.api.Assertions.assertThrows(
                    dev.simplecore.simplix.auth.exception.TokenValidationException.class,
                    () -> filter.doFilterInternal(request, response, filterChain));

            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("cookie token extraction")
    class CookieTokenExtraction {

        @Test
        @DisplayName("should extract token from cookie when OAuth2 is enabled")
        void shouldExtractFromCookie() throws Exception {
            // OAuth2 is enabled by default and cookie name is "access_token" by default
            properties.getSecurity().setPreferTokenOverSession(true);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie("access_token", "cookie-token"));
            request.addHeader("User-Agent", "TestAgent");
            MockHttpServletResponse response = new MockHttpServletResponse();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("cookie-token")).thenReturn(claims);
            when(tokenProvider.validateToken(eq("cookie-token"), anyString(), anyString())).thenReturn(true);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("testuser");
        }

        @Test
        @DisplayName("should return null when no cookies present")
        void shouldReturnNullWhenNoCookies() throws Exception {
            properties.getOauth2().setEnabled(true);

            MockHttpServletRequest request = new MockHttpServletRequest();
            // No cookies
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should ignore cookies with wrong name")
        void shouldIgnoreCookiesWithWrongName() throws Exception {
            properties.getOauth2().setEnabled(true);
            properties.getOauth2().getCookie().setAccessTokenName("access_token");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie("session_id", "some-value"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("session vs token preference")
    class SessionTokenPreference {

        @Test
        @DisplayName("should use existing session auth when prefer-token is false and no token")
        void shouldUseSessionAuthWhenPreferTokenFalse() throws Exception {
            properties.getSecurity().setPreferTokenOverSession(false);

            // Set existing session auth
            UserDetails existingUser = User.withUsername("session-user")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken existingAuth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            existingUser, null, existingUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            MockHttpServletRequest request = new MockHttpServletRequest();
            // No bearer token
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("session-user");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should prefer token over session when configured")
        void shouldPreferTokenOverSession() throws Exception {
            properties.getSecurity().setPreferTokenOverSession(true);

            // Set existing session auth
            UserDetails existingUser = User.withUsername("session-user")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken existingAuth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            existingUser, null, existingUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer token-value");
            request.addHeader("User-Agent", "TestAgent");
            MockHttpServletResponse response = new MockHttpServletResponse();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("token-user")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("token-value")).thenReturn(claims);
            when(tokenProvider.validateToken(eq("token-value"), anyString(), anyString())).thenReturn(true);

            UserDetails tokenUser = User.withUsername("token-user")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("token-user")).thenReturn(tokenUser);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("token-user");
        }
    }

    @Nested
    @DisplayName("validation returns false")
    class ValidationReturnsFalse {

        @Test
        @DisplayName("should not set authentication when validation returns false")
        void shouldNotSetAuthWhenValidationReturnsFalse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer test-token");
            request.addHeader("User-Agent", "TestAgent");
            MockHttpServletResponse response = new MockHttpServletResponse();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("test-token")).thenReturn(claims);
            when(tokenProvider.validateToken(eq("test-token"), anyString(), anyString())).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("static factory method")
    class StaticFactory {

        @Test
        @DisplayName("should create filter via static factory method")
        void shouldCreateViaFactory() {
            SimpliXTokenAuthenticationFilter result =
                    SimpliXTokenAuthenticationFilter.tokenAuthenticationFilter(
                            tokenProvider, userDetailsService, properties);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("token auth when prefer-token is false")
    class TokenAuthWhenPreferFalse {

        @Test
        @DisplayName("should use token when no session auth and prefer-token is false")
        void shouldUseTokenWhenNoSessionAndPreferFalse() throws Exception {
            properties.getSecurity().setPreferTokenOverSession(false);

            // No session auth set

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer test-token");
            request.addHeader("User-Agent", "TestAgent");
            MockHttpServletResponse response = new MockHttpServletResponse();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                    .build();
            when(tokenProvider.parseToken("test-token")).thenReturn(claims);
            when(tokenProvider.validateToken(eq("test-token"), anyString(), anyString())).thenReturn(true);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("testuser");
        }
    }

    @Nested
    @DisplayName("permit-all patterns advanced")
    class PermitAllPatternsAdvanced {

        @Test
        @DisplayName("should not skip when path does not match permit-all patterns")
        void shouldNotSkipWhenNoMatch() throws Exception {
            properties.getSecurity().setPermitAllPatterns(new String[]{"/api/public/**"});

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/private/data");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should not skip when permit-all patterns is null")
        void shouldNotSkipWhenPatternsNull() throws Exception {
            properties.getSecurity().setPermitAllPatterns(null);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/something");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
