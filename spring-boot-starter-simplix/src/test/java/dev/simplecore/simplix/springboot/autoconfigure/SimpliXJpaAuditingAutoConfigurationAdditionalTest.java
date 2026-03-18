package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXJpaAuditingAutoConfiguration - additional auditor provider coverage")
class SimpliXJpaAuditingAutoConfigurationAdditionalTest {

    private SimpliXJpaAuditingAutoConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SimpliXJpaAuditingAutoConfiguration();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("auditorProvider - additional branch tests")
    class AuditorProviderBranches {

        @Test
        @DisplayName("Should return SYSTEM when authentication is not authenticated")
        void unauthenticatedUser() {
            // Create authentication that returns false for isAuthenticated
            TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass");
            auth.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(auth);

            AuditorAware<String> auditorAware = config.auditorProvider();
            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("Should return user name for authenticated user with authorities")
        void authenticatedUserWithAuthorities() {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            "admin@example.com", "password",
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            AuditorAware<String> auditorAware = config.auditorProvider();
            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("admin@example.com");
        }

        @Test
        @DisplayName("Should return anonymous identity from AnonymousAuthenticationToken")
        void anonymousAuthentication() {
            AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                    "key", "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            AuditorAware<String> auditorAware = config.auditorProvider();
            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("anonymousUser");
        }

        @Test
        @DisplayName("Should handle custom principal object")
        void customPrincipal() {
            Object customPrincipal = new Object() {
                @Override
                public String toString() {
                    return "CustomUser123";
                }
            };
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            customPrincipal, "password",
                            Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            AuditorAware<String> auditorAware = config.auditorProvider();
            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            // Authentication.getName() returns the principal's toString()
            assertThat(auditor.get()).isEqualTo("CustomUser123");
        }

        @Test
        @DisplayName("Should return SYSTEM when authentication principal throws exception")
        void principalThrowsException() {
            // Create an authentication that throws when getPrincipal is called
            org.springframework.security.core.Authentication badAuth =
                    new org.springframework.security.core.Authentication() {
                        @Override
                        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
                            return Collections.emptyList();
                        }
                        @Override
                        public Object getCredentials() { return null; }
                        @Override
                        public Object getDetails() { return null; }
                        @Override
                        public Object getPrincipal() { throw new RuntimeException("Test exception"); }
                        @Override
                        public boolean isAuthenticated() { return true; }
                        @Override
                        public void setAuthenticated(boolean isAuthenticated) {}
                        @Override
                        public String getName() { throw new RuntimeException("Test exception"); }
                    };
            SecurityContextHolder.getContext().setAuthentication(badAuth);

            AuditorAware<String> auditorAware = config.auditorProvider();
            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("SYSTEM");
        }
    }
}
