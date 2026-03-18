package dev.simplecore.simplix.stream.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SchedulerState enum.
 */
@DisplayName("SchedulerState")
class SchedulerStateTest {

    @Test
    @DisplayName("should have CREATED, RUNNING, ERROR, and STOPPED values")
    void shouldHaveAllValues() {
        SchedulerState[] values = SchedulerState.values();

        assertThat(values).hasSize(4);
        assertThat(values).containsExactly(
                SchedulerState.CREATED,
                SchedulerState.RUNNING,
                SchedulerState.ERROR,
                SchedulerState.STOPPED
        );
    }

    @Test
    @DisplayName("should resolve each state from name")
    void shouldResolveFromName() {
        assertThat(SchedulerState.valueOf("CREATED")).isEqualTo(SchedulerState.CREATED);
        assertThat(SchedulerState.valueOf("RUNNING")).isEqualTo(SchedulerState.RUNNING);
        assertThat(SchedulerState.valueOf("ERROR")).isEqualTo(SchedulerState.ERROR);
        assertThat(SchedulerState.valueOf("STOPPED")).isEqualTo(SchedulerState.STOPPED);
    }

    @Test
    @DisplayName("should have correct ordinal values")
    void shouldHaveCorrectOrdinalValues() {
        assertThat(SchedulerState.CREATED.ordinal()).isZero();
        assertThat(SchedulerState.RUNNING.ordinal()).isEqualTo(1);
        assertThat(SchedulerState.ERROR.ordinal()).isEqualTo(2);
        assertThat(SchedulerState.STOPPED.ordinal()).isEqualTo(3);
    }
}
