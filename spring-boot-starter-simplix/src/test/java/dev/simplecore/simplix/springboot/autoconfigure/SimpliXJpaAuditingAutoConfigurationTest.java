package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXJpaAuditingAutoConfiguration - JPA auditing auto-configuration")
class SimpliXJpaAuditingAutoConfigurationTest {

    private SimpliXJpaAuditingAutoConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SimpliXJpaAuditingAutoConfiguration();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("auditorProvider")
    class AuditorProvider {

        @Test
        @DisplayName("Should return SYSTEM when no authentication is present")
        void noAuthentication() {
            AuditorAware<String> auditorAware = config.auditorProvider();

            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("Should return authenticated user name")
        void authenticatedUser() {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("admin", "password", Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            AuditorAware<String> auditorAware = config.auditorProvider();

            Optional<String> auditor = auditorAware.getCurrentAuditor();

            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("dateTimeProvider")
    class DateTimeProviderTest {

        @Test
        @DisplayName("Should return current OffsetDateTime")
        void returnCurrentDateTime() {
            DateTimeProvider provider = config.dateTimeProvider();

            Optional<TemporalAccessor> now = provider.getNow();

            assertThat(now).isPresent();
            assertThat(now.get()).isInstanceOf(OffsetDateTime.class);
        }

        @Test
        @DisplayName("Should return time close to current time")
        void timeCloseToNow() {
            DateTimeProvider provider = config.dateTimeProvider();

            OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);
            Optional<TemporalAccessor> now = provider.getNow();
            OffsetDateTime after = OffsetDateTime.now().plusSeconds(1);

            assertThat(now).isPresent();
            OffsetDateTime providedTime = (OffsetDateTime) now.get();
            assertThat(providedTime).isAfterOrEqualTo(before);
            assertThat(providedTime).isBeforeOrEqualTo(after);
        }
    }
}
