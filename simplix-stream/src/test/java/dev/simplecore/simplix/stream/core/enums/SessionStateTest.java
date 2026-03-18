package dev.simplecore.simplix.stream.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionState enum.
 */
@DisplayName("SessionState")
class SessionStateTest {

    @Test
    @DisplayName("should have CONNECTED, DISCONNECTED, and TERMINATED values")
    void shouldHaveAllValues() {
        SessionState[] values = SessionState.values();

        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                SessionState.CONNECTED,
                SessionState.DISCONNECTED,
                SessionState.TERMINATED
        );
    }

    @Test
    @DisplayName("should resolve each state from name")
    void shouldResolveFromName() {
        assertThat(SessionState.valueOf("CONNECTED")).isEqualTo(SessionState.CONNECTED);
        assertThat(SessionState.valueOf("DISCONNECTED")).isEqualTo(SessionState.DISCONNECTED);
        assertThat(SessionState.valueOf("TERMINATED")).isEqualTo(SessionState.TERMINATED);
    }

    @Test
    @DisplayName("should have correct ordinal values")
    void shouldHaveCorrectOrdinalValues() {
        assertThat(SessionState.CONNECTED.ordinal()).isZero();
        assertThat(SessionState.DISCONNECTED.ordinal()).isEqualTo(1);
        assertThat(SessionState.TERMINATED.ordinal()).isEqualTo(2);
    }
}
