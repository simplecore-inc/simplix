package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.EmailStatus;
import dev.simplecore.simplix.email.model.MailPriority;
import dev.simplecore.simplix.email.model.MailProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsoleEmailProvider")
class ConsoleEmailProviderTest {

    @Nested
    @DisplayName("getType")
    class GetType {

        @Test
        @DisplayName("Should return CONSOLE provider type")
        void shouldReturnConsoleType() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();

            assertThat(provider.getType()).isEqualTo(MailProviderType.CONSOLE);
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Should be available when enabled by default")
        void shouldBeAvailableByDefault() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();

            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should be available when explicitly enabled")
        void shouldBeAvailableWhenEnabled() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider(true);

            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should not be available when disabled")
        void shouldNotBeAvailableWhenDisabled() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider(false);

            assertThat(provider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriority {

        @Test
        @DisplayName("Should have lowest priority (-100)")
        void shouldHaveLowestPriority() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();

            assertThat(provider.getPriority()).isEqualTo(-100);
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("Should return success result for valid email request")
        void shouldReturnSuccessForValidRequest() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailRequest request = EmailRequest.simple("user@example.com", "Test Subject", "<p>Hello</p>");

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getMessageId()).startsWith("console-");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.CONSOLE);
        }

        @Test
        @DisplayName("Should return success for email with plain text body")
        void shouldReturnSuccessForPlainTextEmail() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailRequest request = EmailRequest.plainText("user@example.com", "Test", "Plain text content");

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return success for email with attachments")
        void shouldReturnSuccessForEmailWithAttachments() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailAttachment attachment = EmailAttachment.of("doc.pdf", "application/pdf", "content".getBytes());
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("With Attachment")
                    .htmlBody("<p>See attached</p>")
                    .attachments(List.of(attachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return success for email with CC and BCC")
        void shouldReturnSuccessForEmailWithCcAndBcc() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .from(EmailAddress.of("Admin", "admin@example.com"))
                    .to(List.of(EmailAddress.of("to@example.com")))
                    .cc(List.of(EmailAddress.of("cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc@example.com")))
                    .subject("Test CC/BCC")
                    .htmlBody("<p>Content</p>")
                    .priority(MailPriority.HIGH)
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return failure when provider is disabled")
        void shouldReturnFailureWhenDisabled() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider(false);
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Provider not available");
        }

        @Test
        @DisplayName("Should return failure when request is null")
        void shouldReturnFailureWhenRequestIsNull() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();

            EmailResult result = provider.send(null);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Validation failed");
        }

        @Test
        @DisplayName("Should return failure when no recipients")
        void shouldReturnFailureWhenNoRecipients() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Validation failed");
        }

        @Test
        @DisplayName("Should return failure when subject is missing")
        void shouldReturnFailureWhenSubjectMissing() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Validation failed");
        }

        @Test
        @DisplayName("Should return failure when no body is provided")
        void shouldReturnFailureWhenNoBody() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Validation failed");
        }
    }

    @Nested
    @DisplayName("supportsBulkSend")
    class SupportsBulkSend {

        @Test
        @DisplayName("Should not support bulk send")
        void shouldNotSupportBulkSend() {
            ConsoleEmailProvider provider = new ConsoleEmailProvider();

            assertThat(provider.supportsBulkSend()).isFalse();
        }
    }
}
