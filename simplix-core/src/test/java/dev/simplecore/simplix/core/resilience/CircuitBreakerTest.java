package dev.simplecore.simplix.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker(3, 500);
    }

    @Nested
    @DisplayName("CLOSED state")
    class ClosedState {

        @Test
        @DisplayName("should allow requests by default")
        void shouldAllowByDefault() {
            assertThat(circuitBreaker.allowRequest("key-1")).isTrue();
            assertThat(circuitBreaker.getStatus("key-1")).isEqualTo(CircuitBreaker.Status.CLOSED);
        }

        @Test
        @DisplayName("should allow requests after failures below threshold")
        void shouldAllowBelowThreshold() {
            circuitBreaker.recordFailure("key-1");
            circuitBreaker.recordFailure("key-1");
            assertThat(circuitBreaker.allowRequest("key-1")).isTrue();
        }
    }

    @Nested
    @DisplayName("OPEN state")
    class OpenState {

        @Test
        @DisplayName("should open after reaching failure threshold")
        void shouldOpenAfterThreshold() {
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("key-1");
            }
            assertThat(circuitBreaker.getStatus("key-1")).isEqualTo(CircuitBreaker.Status.OPEN);
            assertThat(circuitBreaker.allowRequest("key-1")).isFalse();
        }

        @Test
        @DisplayName("should reject requests when open")
        void shouldRejectWhenOpen() {
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("key-1");
            }
            assertThat(circuitBreaker.allowRequest("key-1")).isFalse();
            assertThat(circuitBreaker.allowRequest("key-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("HALF_OPEN state")
    class HalfOpenState {

        @Test
        @DisplayName("should transition to half-open after timeout")
        void shouldTransitionAfterTimeout() throws InterruptedException {
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("key-1");
            }
            Thread.sleep(600);
            assertThat(circuitBreaker.allowRequest("key-1")).isTrue();
        }

        @Test
        @DisplayName("should close on success in half-open state")
        void shouldCloseOnSuccess() throws InterruptedException {
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("key-1");
            }
            Thread.sleep(600);
            circuitBreaker.allowRequest("key-1"); // transition to HALF_OPEN
            circuitBreaker.recordSuccess("key-1");
            assertThat(circuitBreaker.getStatus("key-1")).isEqualTo(CircuitBreaker.Status.CLOSED);
            assertThat(circuitBreaker.allowRequest("key-1")).isTrue();
        }

        @Test
        @DisplayName("should reopen on failure in half-open state")
        void shouldReopenOnFailure() throws InterruptedException {
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("key-1");
            }
            Thread.sleep(600);
            circuitBreaker.allowRequest("key-1"); // transition to HALF_OPEN
            circuitBreaker.recordFailure("key-1");
            assertThat(circuitBreaker.getStatus("key-1")).isEqualTo(CircuitBreaker.Status.OPEN);
        }
    }

    @Nested
    @DisplayName("Key isolation")
    class KeyIsolation {

        @Test
        @DisplayName("should maintain independent state per key")
        void shouldIsolateKeys() {
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure("key-1");
            }
            assertThat(circuitBreaker.allowRequest("key-1")).isFalse();
            assertThat(circuitBreaker.allowRequest("key-2")).isTrue();
        }
    }

    @Test
    @DisplayName("should reset circuit to CLOSED")
    void shouldResetCircuit() {
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure("key-1");
        }
        assertThat(circuitBreaker.getStatus("key-1")).isEqualTo(CircuitBreaker.Status.OPEN);
        circuitBreaker.reset("key-1");
        assertThat(circuitBreaker.getStatus("key-1")).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
}
