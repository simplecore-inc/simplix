package dev.simplecore.simplix.stream.admin.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AdminCommandStatus enum.
 */
@DisplayName("AdminCommandStatus")
class AdminCommandStatusTest {

    @Test
    @DisplayName("should have all expected statuses")
    void shouldHaveAllExpectedStatuses() {
        AdminCommandStatus[] values = AdminCommandStatus.values();

        assertThat(values).hasSize(5);
        assertThat(values).containsExactly(
                AdminCommandStatus.PENDING,
                AdminCommandStatus.EXECUTED,
                AdminCommandStatus.FAILED,
                AdminCommandStatus.EXPIRED,
                AdminCommandStatus.NOT_FOUND
        );
    }

    @Test
    @DisplayName("should resolve each status from name")
    void shouldResolveFromName() {
        assertThat(AdminCommandStatus.valueOf("PENDING")).isEqualTo(AdminCommandStatus.PENDING);
        assertThat(AdminCommandStatus.valueOf("EXECUTED")).isEqualTo(AdminCommandStatus.EXECUTED);
        assertThat(AdminCommandStatus.valueOf("FAILED")).isEqualTo(AdminCommandStatus.FAILED);
        assertThat(AdminCommandStatus.valueOf("EXPIRED")).isEqualTo(AdminCommandStatus.EXPIRED);
        assertThat(AdminCommandStatus.valueOf("NOT_FOUND")).isEqualTo(AdminCommandStatus.NOT_FOUND);
    }
}
