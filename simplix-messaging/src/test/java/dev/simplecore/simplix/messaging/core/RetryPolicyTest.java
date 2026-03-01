package dev.simplecore.simplix.messaging.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryPolicy")
class RetryPolicyTest {

    @Test
    @DisplayName("defaults() should return standard policy values")
    void defaultsShouldReturnStandardValues() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.initialBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
        assertThat(policy.maxBackoff()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("backoffFor() should calculate exponential backoff")
    void backoffForShouldCalculateExponential() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(1), 2.0, Duration.ofMinutes(1));

        assertThat(policy.backoffFor(0)).isEqualTo(Duration.ofSeconds(1));   // 1 * 2^0 = 1
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));   // 1 * 2^1 = 2
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));   // 1 * 2^2 = 4
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofSeconds(8));   // 1 * 2^3 = 8
        assertThat(policy.backoffFor(4)).isEqualTo(Duration.ofSeconds(16));  // 1 * 2^4 = 16
    }

    @Test
    @DisplayName("backoffFor() should cap at maxBackoff")
    void backoffForShouldCapAtMax() {
        RetryPolicy policy = new RetryPolicy(10, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10));

        assertThat(policy.backoffFor(5)).isEqualTo(Duration.ofSeconds(10));  // 1 * 2^5 = 32, capped at 10
        assertThat(policy.backoffFor(10)).isEqualTo(Duration.ofSeconds(10)); // well beyond cap
    }

    @Test
    @DisplayName("backoffFor() should work with non-2x multiplier")
    void backoffForShouldWorkWithCustomMultiplier() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100), 3.0, Duration.ofSeconds(30));

        assertThat(policy.backoffFor(0)).isEqualTo(Duration.ofMillis(100));  // 100 * 3^0 = 100
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofMillis(300));  // 100 * 3^1 = 300
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofMillis(900));  // 100 * 3^2 = 900
    }
}
