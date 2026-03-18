package dev.simplecore.simplix.auth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CaffeineTokenBlacklistService")
class CaffeineTokenBlacklistServiceTest {

    private CaffeineTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new CaffeineTokenBlacklistService();
        service.init();
    }

    @Test
    @DisplayName("should add token to blacklist")
    void shouldAddTokenToBlacklist() {
        service.blacklist("jti-123", Duration.ofMinutes(30));

        assertThat(service.isBlacklisted("jti-123")).isTrue();
    }

    @Test
    @DisplayName("should return false for non-blacklisted token")
    void shouldReturnFalseForNonBlacklistedToken() {
        assertThat(service.isBlacklisted("unknown-jti")).isFalse();
    }

    @Test
    @DisplayName("should handle multiple blacklisted tokens")
    void shouldHandleMultipleTokens() {
        service.blacklist("jti-1", Duration.ofMinutes(10));
        service.blacklist("jti-2", Duration.ofMinutes(20));
        service.blacklist("jti-3", Duration.ofMinutes(30));

        assertThat(service.isBlacklisted("jti-1")).isTrue();
        assertThat(service.isBlacklisted("jti-2")).isTrue();
        assertThat(service.isBlacklisted("jti-3")).isTrue();
        assertThat(service.isBlacklisted("jti-4")).isFalse();
    }

    @Test
    @DisplayName("should overwrite existing blacklist entry")
    void shouldOverwriteExistingEntry() {
        service.blacklist("jti-1", Duration.ofMinutes(5));
        service.blacklist("jti-1", Duration.ofMinutes(30));

        assertThat(service.isBlacklisted("jti-1")).isTrue();
    }
}
