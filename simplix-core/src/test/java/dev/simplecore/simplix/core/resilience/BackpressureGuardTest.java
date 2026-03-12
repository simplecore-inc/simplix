package dev.simplecore.simplix.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackpressureGuardTest {

    private BackpressureGuard guard;

    @BeforeEach
    void setUp() {
        guard = new BackpressureGuard(5, 50);
    }

    @Test
    @DisplayName("should accept connections below limit")
    void shouldAcceptBelowLimit() {
        assertThat(guard.tryRegister()).isTrue();
        assertThat(guard.getActiveConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject connections at limit")
    void shouldRejectAtLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(guard.tryRegister()).isTrue();
        }
        assertThat(guard.tryRegister()).isFalse();
        assertThat(guard.getActiveConnections()).isEqualTo(5);
    }

    @Test
    @DisplayName("should allow after unregister")
    void shouldAllowAfterUnregister() {
        for (int i = 0; i < 5; i++) {
            guard.tryRegister();
        }
        guard.unregister();
        assertThat(guard.tryRegister()).isTrue();
    }

    @Test
    @DisplayName("should not go below zero on unregister")
    void shouldNotGoBelowZero() {
        guard.unregister();
        assertThat(guard.getActiveConnections()).isEqualTo(0);
    }

    @Test
    @DisplayName("should signal throttle when above 80% capacity")
    void shouldSignalThrottle() {
        // 4 of 5 = 80%
        for (int i = 0; i < 4; i++) {
            guard.tryRegister();
        }
        assertThat(guard.shouldProceed()).isFalse();
    }

    @Test
    @DisplayName("should not throttle below 80% capacity")
    void shouldNotThrottleBelowThreshold() {
        for (int i = 0; i < 3; i++) {
            guard.tryRegister();
        }
        assertThat(guard.shouldProceed()).isTrue();
    }

    @Test
    @DisplayName("should return configured properties")
    void shouldReturnProperties() {
        assertThat(guard.getMaxConnections()).isEqualTo(5);
        assertThat(guard.getMaxEventsPerSecond()).isEqualTo(50);
    }
}
