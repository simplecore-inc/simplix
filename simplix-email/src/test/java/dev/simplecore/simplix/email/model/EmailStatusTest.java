package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailStatus")
class EmailStatusTest {

    @Test
    @DisplayName("Should contain all expected status values")
    void shouldContainAllValues() {
        assertThat(EmailStatus.values()).containsExactly(
                EmailStatus.PENDING,
                EmailStatus.SENDING,
                EmailStatus.SENT,
                EmailStatus.DELIVERED,
                EmailStatus.FAILED,
                EmailStatus.BOUNCED,
                EmailStatus.COMPLAINED,
                EmailStatus.SUPPRESSED
        );
    }

    @Test
    @DisplayName("Should resolve from name string")
    void shouldResolveFromName() {
        assertThat(EmailStatus.valueOf("PENDING")).isEqualTo(EmailStatus.PENDING);
        assertThat(EmailStatus.valueOf("SENT")).isEqualTo(EmailStatus.SENT);
        assertThat(EmailStatus.valueOf("FAILED")).isEqualTo(EmailStatus.FAILED);
    }
}
