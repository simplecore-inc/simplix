package dev.simplecore.simplix.auth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryTokenBlacklistService")
class InMemoryTokenBlacklistServiceTest {

    private InMemoryTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryTokenBlacklistService();
    }

    @Nested
    @DisplayName("blacklist")
    class Blacklist {

        @Test
        @DisplayName("should add token JTI to blacklist")
        void shouldAddTokenToBlacklist() {
            service.blacklist("jti-123", Duration.ofMinutes(30));

            assertThat(service.isBlacklisted("jti-123")).isTrue();
        }

        @Test
        @DisplayName("should handle multiple tokens")
        void shouldHandleMultipleTokens() {
            service.blacklist("jti-1", Duration.ofMinutes(10));
            service.blacklist("jti-2", Duration.ofMinutes(20));

            assertThat(service.isBlacklisted("jti-1")).isTrue();
            assertThat(service.isBlacklisted("jti-2")).isTrue();
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklisted {

        @Test
        @DisplayName("should return false for unknown JTI")
        void shouldReturnFalseForUnknown() {
            assertThat(service.isBlacklisted("unknown")).isFalse();
        }

        @Test
        @DisplayName("should return false for expired JTI")
        void shouldReturnFalseForExpired() {
            // Add with zero duration (already expired)
            service.blacklist("jti-expired", Duration.ofMillis(-1));

            assertThat(service.isBlacklisted("jti-expired")).isFalse();
        }

        @Test
        @DisplayName("should return true for valid blacklisted JTI")
        void shouldReturnTrueForValid() {
            service.blacklist("jti-valid", Duration.ofHours(1));

            assertThat(service.isBlacklisted("jti-valid")).isTrue();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredTokens")
    class Cleanup {

        @Test
        @DisplayName("should remove expired entries during cleanup")
        void shouldRemoveExpiredEntries() {
            service.blacklist("jti-expired", Duration.ofMillis(-1));
            service.blacklist("jti-valid", Duration.ofHours(1));

            service.cleanupExpiredTokens();

            assertThat(service.isBlacklisted("jti-expired")).isFalse();
            assertThat(service.isBlacklisted("jti-valid")).isTrue();
        }

        @Test
        @DisplayName("should handle empty blacklist")
        void shouldHandleEmptyBlacklist() {
            service.cleanupExpiredTokens();
            // No exception should be thrown
        }
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should initialize without errors")
        void shouldInitWithoutErrors() {
            // init() just logs warnings, should not throw
            service.init();
        }
    }
}
