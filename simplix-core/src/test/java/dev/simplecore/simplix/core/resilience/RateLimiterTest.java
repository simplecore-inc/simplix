package dev.simplecore.simplix.core.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    @DisplayName("should allow up to max tokens")
    void shouldAllowUpToMax() {
        RateLimiter limiter = new RateLimiter(3);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("should refill tokens after interval")
    void shouldRefillAfterInterval() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(2);
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertThat(limiter.tryAcquire()).isFalse();
        Thread.sleep(1100);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("should report available tokens")
    void shouldReportAvailableTokens() {
        RateLimiter limiter = new RateLimiter(5);
        assertThat(limiter.getAvailableTokens()).isEqualTo(5);
        limiter.tryAcquire();
        assertThat(limiter.getAvailableTokens()).isEqualTo(4);
    }
}
