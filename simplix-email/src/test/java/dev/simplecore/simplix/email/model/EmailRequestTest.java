package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailRequest")
class EmailRequestTest {

    @Nested
    @DisplayName("simple factory method")
    class SimpleFactory {

        @Test
        @DisplayName("Should create simple email request with HTML body")
        void shouldCreateSimpleRequest() {
            EmailRequest request = EmailRequest.simple(
                    "user@example.com", "Hello", "<p>World</p>"
            );

            assertThat(request.getTo()).hasSize(1);
            assertThat(request.getTo().get(0).getAddress()).isEqualTo("user@example.com");
            assertThat(request.getSubject()).isEqualTo("Hello");
            assertThat(request.getHtmlBody()).isEqualTo("<p>World</p>");
            assertThat(request.getTextBody()).isNull();
        }
    }

    @Nested
    @DisplayName("plainText factory method")
    class PlainTextFactory {

        @Test
        @DisplayName("Should create plain text email request")
        void shouldCreatePlainTextRequest() {
            EmailRequest request = EmailRequest.plainText(
                    "user@example.com", "Hello", "World"
            );

            assertThat(request.getTo()).hasSize(1);
            assertThat(request.getTo().get(0).getAddress()).isEqualTo("user@example.com");
            assertThat(request.getSubject()).isEqualTo("Hello");
            assertThat(request.getTextBody()).isEqualTo("World");
            assertThat(request.getHtmlBody()).isNull();
        }
    }

    @Nested
    @DisplayName("hasHtmlBody")
    class HasHtmlBody {

        @Test
        @DisplayName("Should return true when HTML body is present")
        void shouldReturnTrueWhenPresent() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>content</p>")
                    .build();

            assertThat(request.hasHtmlBody()).isTrue();
        }

        @Test
        @DisplayName("Should return false when HTML body is null")
        void shouldReturnFalseWhenNull() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .build();

            assertThat(request.hasHtmlBody()).isFalse();
        }

        @Test
        @DisplayName("Should return false when HTML body is blank")
        void shouldReturnFalseWhenBlank() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("   ")
                    .build();

            assertThat(request.hasHtmlBody()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasTextBody")
    class HasTextBody {

        @Test
        @DisplayName("Should return true when text body is present")
        void shouldReturnTrueWhenPresent() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .textBody("hello")
                    .build();

            assertThat(request.hasTextBody()).isTrue();
        }

        @Test
        @DisplayName("Should return false when text body is null")
        void shouldReturnFalseWhenNull() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .build();

            assertThat(request.hasTextBody()).isFalse();
        }

        @Test
        @DisplayName("Should return false when text body is blank")
        void shouldReturnFalseWhenBlank() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .textBody("")
                    .build();

            assertThat(request.hasTextBody()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasAttachments")
    class HasAttachments {

        @Test
        @DisplayName("Should return true when attachments are present")
        void shouldReturnTrueWhenPresent() {
            EmailAttachment attachment = EmailAttachment.of("file.pdf", "application/pdf", new byte[]{1});
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .attachments(List.of(attachment))
                    .build();

            assertThat(request.hasAttachments()).isTrue();
        }

        @Test
        @DisplayName("Should return false when attachments list is empty")
        void shouldReturnFalseWhenEmpty() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .build();

            assertThat(request.hasAttachments()).isFalse();
        }

        @Test
        @DisplayName("Should return false when attachments is null")
        void shouldReturnFalseWhenNull() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .attachments(null)
                    .build();

            assertThat(request.hasAttachments()).isFalse();
        }
    }

    @Nested
    @DisplayName("getTotalRecipientCount")
    class GetTotalRecipientCount {

        @Test
        @DisplayName("Should count all To, CC, and BCC recipients")
        void shouldCountAllRecipients() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("to1@example.com"), EmailAddress.of("to2@example.com")))
                    .cc(List.of(EmailAddress.of("cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc1@example.com"), EmailAddress.of("bcc2@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .build();

            assertThat(request.getTotalRecipientCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should count only To recipients when CC and BCC are empty")
        void shouldCountOnlyToRecipients() {
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>test</p>");

            assertThat(request.getTotalRecipientCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 0 when To is null")
        void shouldReturnZeroWhenToIsNull() {
            EmailRequest request = EmailRequest.builder()
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .build();

            assertThat(request.getTotalRecipientCount()).isZero();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("Should use NORMAL priority by default")
        void shouldUseNormalPriorityByDefault() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .build();

            assertThat(request.getPriority()).isEqualTo(MailPriority.NORMAL);
        }

        @Test
        @DisplayName("Should use empty list defaults for CC, BCC, attachments, tags, and headers")
        void shouldUseEmptyListDefaults() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .build();

            assertThat(request.getCc()).isNotNull().isEmpty();
            assertThat(request.getBcc()).isNotNull().isEmpty();
            assertThat(request.getAttachments()).isNotNull().isEmpty();
            assertThat(request.getTags()).isNotNull().isEmpty();
            assertThat(request.getHeaders()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("custom headers and tags")
    class HeadersAndTags {

        @Test
        @DisplayName("Should set custom headers")
        void shouldSetCustomHeaders() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .headers(Map.of("X-Custom", "value"))
                    .build();

            assertThat(request.getHeaders()).containsEntry("X-Custom", "value");
        }

        @Test
        @DisplayName("Should set tags for analytics")
        void shouldSetTags() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>test</p>")
                    .tags(List.of("marketing", "welcome"))
                    .build();

            assertThat(request.getTags()).containsExactly("marketing", "welcome");
        }
    }
}
