package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MailProviderType")
class MailProviderTypeTest {

    @Test
    @DisplayName("Should contain all expected provider types")
    void shouldContainAllValues() {
        assertThat(MailProviderType.values()).containsExactly(
                MailProviderType.CONSOLE,
                MailProviderType.SMTP,
                MailProviderType.AWS_SES,
                MailProviderType.SENDGRID,
                MailProviderType.RESEND
        );
    }

    @Test
    @DisplayName("Should resolve from name string")
    void shouldResolveFromName() {
        assertThat(MailProviderType.valueOf("CONSOLE")).isEqualTo(MailProviderType.CONSOLE);
        assertThat(MailProviderType.valueOf("SMTP")).isEqualTo(MailProviderType.SMTP);
        assertThat(MailProviderType.valueOf("AWS_SES")).isEqualTo(MailProviderType.AWS_SES);
        assertThat(MailProviderType.valueOf("SENDGRID")).isEqualTo(MailProviderType.SENDGRID);
        assertThat(MailProviderType.valueOf("RESEND")).isEqualTo(MailProviderType.RESEND);
    }
}
