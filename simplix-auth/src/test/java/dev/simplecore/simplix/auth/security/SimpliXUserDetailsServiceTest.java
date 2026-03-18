package dev.simplecore.simplix.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SimpliXUserDetailsService")
@ExtendWith(MockitoExtension.class)
class SimpliXUserDetailsServiceTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private Authentication authentication;

    private TestUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new TestUserDetailsService(messageSource);
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("should return true when user exists")
        void shouldReturnTrueWhenUserExists() {
            boolean result = service.exists("admin");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenNotFound() {
            boolean result = service.exists("nonexistent");
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("loadCurrentUser")
    class LoadCurrentUser {

        @Test
        @DisplayName("should load user from authentication")
        void shouldLoadUserFromAuthentication() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("admin");

            UserDetails result = service.loadCurrentUser(authentication);

            assertThat(result.getUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should throw when authentication is null")
        void shouldThrowWhenAuthNull() {
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("No authenticated user");

            assertThatThrownBy(() -> service.loadCurrentUser(null))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when not authenticated")
        void shouldThrowWhenNotAuthenticated() {
            when(authentication.isAuthenticated()).thenReturn(false);
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("No authenticated user");

            assertThatThrownBy(() -> service.loadCurrentUser(authentication))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("isAccountLocked")
    class IsAccountLocked {

        @Test
        @DisplayName("should return true when account is locked")
        void shouldReturnTrueWhenLocked() {
            service.setLocked(true);
            assertThat(service.isAccountLocked("admin")).isTrue();
        }

        @Test
        @DisplayName("should return false when account is not locked")
        void shouldReturnFalseWhenNotLocked() {
            assertThat(service.isAccountLocked("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenUserNotFound() {
            assertThat(service.isAccountLocked("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("isAccountExpired")
    class IsAccountExpired {

        @Test
        @DisplayName("should return true when account is expired")
        void shouldReturnTrueWhenExpired() {
            service.setExpired(true);
            assertThat(service.isAccountExpired("admin")).isTrue();
        }

        @Test
        @DisplayName("should return false when account is not expired")
        void shouldReturnFalseWhenNotExpired() {
            assertThat(service.isAccountExpired("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenNotFound() {
            assertThat(service.isAccountExpired("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("isCredentialsExpired")
    class IsCredentialsExpired {

        @Test
        @DisplayName("should return true when credentials expired")
        void shouldReturnTrueWhenCredentialsExpired() {
            service.setCredentialsExpired(true);
            assertThat(service.isCredentialsExpired("admin")).isTrue();
        }

        @Test
        @DisplayName("should return false when credentials not expired")
        void shouldReturnFalseWhenNotExpired() {
            assertThat(service.isCredentialsExpired("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenNotFound() {
            assertThat(service.isCredentialsExpired("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserAuthorities")
    class GetUserAuthorities {

        @Test
        @DisplayName("should return user authorities")
        void shouldReturnAuthorities() {
            Collection<String> authorities = service.getUserAuthorities("admin");
            assertThat(authorities).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("should return empty list when user not found")
        void shouldReturnEmptyWhenNotFound() {
            Collection<String> authorities = service.getUserAuthorities("nonexistent");
            assertThat(authorities).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasAuthority")
    class HasAuthority {

        @Test
        @DisplayName("should return true when user has authority")
        void shouldReturnTrueWhenHasAuthority() {
            assertThat(service.hasAuthority("admin", "ROLE_ADMIN")).isTrue();
        }

        @Test
        @DisplayName("should return false when user lacks authority")
        void shouldReturnFalseWhenLacksAuthority() {
            assertThat(service.hasAuthority("admin", "ROLE_SUPERADMIN")).isFalse();
        }
    }

    @Nested
    @DisplayName("hasRole")
    class HasRole {

        @Test
        @DisplayName("should return true with ROLE_ prefix")
        void shouldReturnTrueWithPrefix() {
            assertThat(service.hasRole("admin", "ROLE_ADMIN")).isTrue();
        }

        @Test
        @DisplayName("should return true without ROLE_ prefix")
        void shouldReturnTrueWithoutPrefix() {
            assertThat(service.hasRole("admin", "ADMIN")).isTrue();
        }

        @Test
        @DisplayName("should return false for missing role")
        void shouldReturnFalseForMissingRole() {
            assertThat(service.hasRole("admin", "SUPERADMIN")).isFalse();
        }
    }

    @Nested
    @DisplayName("isActive")
    class IsActive {

        @Test
        @DisplayName("should return true when all checks pass")
        void shouldReturnTrueWhenAllPass() {
            assertThat(service.isActive("admin")).isTrue();
        }

        @Test
        @DisplayName("should return false when account locked")
        void shouldReturnFalseWhenLocked() {
            service.setLocked(true);
            assertThat(service.isActive("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when account expired")
        void shouldReturnFalseWhenExpired() {
            service.setExpired(true);
            assertThat(service.isActive("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when credentials expired")
        void shouldReturnFalseWhenCredentialsExpired() {
            service.setCredentialsExpired(true);
            assertThat(service.isActive("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when disabled")
        void shouldReturnFalseWhenDisabled() {
            service.setEnabled(false);
            assertThat(service.isActive("admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenNotFound() {
            assertThat(service.isActive("nonexistent")).isFalse();
        }
    }

    /**
     * Test implementation of SimpliXUserDetailsService for unit testing.
     */
    private static class TestUserDetailsService implements SimpliXUserDetailsService {

        private final MessageSource messageSource;
        private boolean locked = false;
        private boolean expired = false;
        private boolean credentialsExpired = false;
        private boolean enabled = true;

        TestUserDetailsService(MessageSource messageSource) {
            this.messageSource = messageSource;
        }

        void setLocked(boolean locked) { this.locked = locked; }
        void setExpired(boolean expired) { this.expired = expired; }
        void setCredentialsExpired(boolean credentialsExpired) { this.credentialsExpired = credentialsExpired; }
        void setEnabled(boolean enabled) { this.enabled = enabled; }

        @Override
        public MessageSource getMessageSource() {
            return messageSource;
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            if ("nonexistent".equals(username)) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            return User.builder()
                    .username(username)
                    .password("encoded-password")
                    .authorities(
                            new SimpleGrantedAuthority("ROLE_USER"),
                            new SimpleGrantedAuthority("ROLE_ADMIN"))
                    .accountLocked(locked)
                    .accountExpired(expired)
                    .credentialsExpired(credentialsExpired)
                    .disabled(!enabled)
                    .build();
        }
    }
}
