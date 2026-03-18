package dev.simplecore.simplix.scheduler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionStatus - Enum values and transitions")
class ExecutionStatusTest {

    @Test
    @DisplayName("Should have exactly four status values")
    void shouldHaveFourStatusValues() {
        ExecutionStatus[] values = ExecutionStatus.values();
        assertThat(values).hasSize(4);
    }

    @Test
    @DisplayName("Should contain RUNNING status")
    void shouldContainRunningStatus() {
        assertThat(ExecutionStatus.valueOf("RUNNING")).isEqualTo(ExecutionStatus.RUNNING);
    }

    @Test
    @DisplayName("Should contain SUCCESS status")
    void shouldContainSuccessStatus() {
        assertThat(ExecutionStatus.valueOf("SUCCESS")).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should contain FAILED status")
    void shouldContainFailedStatus() {
        assertThat(ExecutionStatus.valueOf("FAILED")).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    @DisplayName("Should contain TIMEOUT status")
    void shouldContainTimeoutStatus() {
        assertThat(ExecutionStatus.valueOf("TIMEOUT")).isEqualTo(ExecutionStatus.TIMEOUT);
    }

    @Test
    @DisplayName("Should have correct ordinal ordering")
    void shouldHaveCorrectOrdinalOrdering() {
        assertThat(ExecutionStatus.RUNNING.ordinal()).isLessThan(ExecutionStatus.SUCCESS.ordinal());
        assertThat(ExecutionStatus.SUCCESS.ordinal()).isLessThan(ExecutionStatus.FAILED.ordinal());
        assertThat(ExecutionStatus.FAILED.ordinal()).isLessThan(ExecutionStatus.TIMEOUT.ordinal());
    }

    @Test
    @DisplayName("Should return correct name from toString")
    void shouldReturnCorrectNameFromToString() {
        assertThat(ExecutionStatus.RUNNING.name()).isEqualTo("RUNNING");
        assertThat(ExecutionStatus.SUCCESS.name()).isEqualTo("SUCCESS");
        assertThat(ExecutionStatus.FAILED.name()).isEqualTo("FAILED");
        assertThat(ExecutionStatus.TIMEOUT.name()).isEqualTo("TIMEOUT");
    }
}
