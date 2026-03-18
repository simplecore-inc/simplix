package dev.simplecore.simplix.stream.admin.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AdminCommandType enum.
 */
@DisplayName("AdminCommandType")
class AdminCommandTypeTest {

    @Test
    @DisplayName("should have all expected command types")
    void shouldHaveAllExpectedCommandTypes() {
        AdminCommandType[] values = AdminCommandType.values();

        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                AdminCommandType.TERMINATE_SESSION,
                AdminCommandType.STOP_SCHEDULER,
                AdminCommandType.TRIGGER_SCHEDULER
        );
    }

    @Test
    @DisplayName("should resolve each type from name")
    void shouldResolveFromName() {
        assertThat(AdminCommandType.valueOf("TERMINATE_SESSION"))
                .isEqualTo(AdminCommandType.TERMINATE_SESSION);
        assertThat(AdminCommandType.valueOf("STOP_SCHEDULER"))
                .isEqualTo(AdminCommandType.STOP_SCHEDULER);
        assertThat(AdminCommandType.valueOf("TRIGGER_SCHEDULER"))
                .isEqualTo(AdminCommandType.TRIGGER_SCHEDULER);
    }
}
