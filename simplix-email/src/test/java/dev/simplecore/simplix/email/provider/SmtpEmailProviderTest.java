package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.EmailStatus;
import dev.simplecore.simplix.email.model.MailPriority;
import dev.simplecore.simplix.email.model.MailProviderType;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailProvider")
class SmtpEmailProviderTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SmtpEmailProvider(mailSender, "noreply@example.com", "Test App");
    }

    @Nested
    @DisplayName("getType")
    class GetType {

        @Test
        @DisplayName("Should return SMTP provider type")
        void shouldReturnSmtpType() {
            assertThat(provider.getType()).isEqualTo(MailProviderType.SMTP);
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Should be available when mailSender is provided")
        void shouldBeAvailableWithMailSender() {
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should not be available when mailSender is null")
        void shouldNotBeAvailableWithNullMailSender() {
            SmtpEmailProvider nullProvider = new SmtpEmailProvider(null, null, null);

            assertThat(nullProvider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriority {

        @Test
        @DisplayName("Should have priority 10")
        void shouldHavePriority10() {
            assertThat(provider.getPriority()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("Should send email successfully via SMTP")
        void shouldSendEmailSuccessfully() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test Subject", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.SMTP);
            assertThat(result.getMessageId()).isNotNull();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should send plain text email")
        void shouldSendPlainTextEmail() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.plainText("user@example.com", "Test", "Hello World");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should send email with both HTML and text body")
        void shouldSendEmailWithBothBodies() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>HTML</p>")
                    .textBody("Text")
                    .build();
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with custom from address")
        void shouldSendWithCustomFromAddress() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .from(EmailAddress.of("Sender", "sender@example.com"))
                    .to(List.of(EmailAddress.of("Recipient", "user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with CC, BCC, and ReplyTo")
        void shouldSendWithCcBccReplyTo() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("to@example.com")))
                    .cc(List.of(EmailAddress.of("CC User", "cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc@example.com")))
                    .replyTo(EmailAddress.of("Reply", "reply@example.com"))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with attachments")
        void shouldSendEmailWithAttachments() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

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
        @DisplayName("Should send email with inline attachment")
        void shouldSendEmailWithInlineAttachment() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailAttachment inlineAttachment = EmailAttachment.inline(
                    "logo-cid", "logo.png", "image/png", new byte[]{1, 2, 3}
            );
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("With Inline")
                    .htmlBody("<p>Logo: <img src=\"cid:logo-cid\"/></p>")
                    .attachments(List.of(inlineAttachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should set priority header for CRITICAL priority")
        void shouldSetPriorityForCritical() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Critical")
                    .htmlBody("<p>Important</p>")
                    .priority(MailPriority.CRITICAL)
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should set priority header for LOW priority")
        void shouldSetPriorityForLow() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Low Priority")
                    .htmlBody("<p>Not urgent</p>")
                    .priority(MailPriority.LOW)
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should use default from address when request from is null")
        void shouldUseDefaultFromAddress() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should use default from address without name when name is null")
        void shouldUseDefaultFromAddressWithoutName() {
            SmtpEmailProvider noNameProvider = new SmtpEmailProvider(mailSender, "noreply@example.com", null);
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = noNameProvider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should use request from address without name when name is null")
        void shouldUseRequestFromWithoutName() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .from(EmailAddress.of("sender@example.com"))
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should use replyTo without name when name is null")
        void shouldUseReplyToWithoutName() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .replyTo(EmailAddress.of("reply@example.com"))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("Should return failure for MailAuthenticationException")
        void shouldReturnFailureForAuthException() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new MailAuthenticationException("Bad credentials"))
                    .when(mailSender).send(any(MimeMessage.class));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("SMTP authentication failed");
            assertThat(result.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("Should return retryable failure for connection error")
        void shouldReturnRetryableForConnectionError() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new MailSendException("Connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("CONNECTION_ERROR");
        }

        @Test
        @DisplayName("Should return retryable failure for timeout error")
        void shouldReturnRetryableForTimeoutError() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new MailSendException("Read timeout"))
                    .when(mailSender).send(any(MimeMessage.class));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should return non-retryable failure for MailSendException without connection keyword")
        void shouldReturnNonRetryableForOtherMailSendException() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new MailSendException("Invalid recipient"))
                    .when(mailSender).send(any(MimeMessage.class));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("SMTP send failed");
            assertThat(result.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("Should return failure for generic exception")
        void shouldReturnFailureForGenericException() {
            MimeMessage mimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new RuntimeException("Unexpected error"))
                    .when(mailSender).send(any(MimeMessage.class));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("SMTP send failed");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("Should fail when request is null")
        void shouldFailWhenRequestIsNull() {
            EmailResult result = provider.send(null);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Validation failed");
        }

        @Test
        @DisplayName("Should fail when subject is blank")
        void shouldFailWhenSubjectIsBlank() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("  ")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Subject is required");
        }

        @Test
        @DisplayName("Should fail when no body is provided")
        void shouldFailWhenNoBody() {
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Email body");
        }
    }

    @Nested
    @DisplayName("supportsBulkSend")
    class SupportsBulkSend {

        @Test
        @DisplayName("Should not support bulk send")
        void shouldNotSupportBulkSend() {
            assertThat(provider.supportsBulkSend()).isFalse();
        }
    }
}
