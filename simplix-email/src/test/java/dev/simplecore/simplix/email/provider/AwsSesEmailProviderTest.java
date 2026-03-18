package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.EmailStatus;
import dev.simplecore.simplix.email.model.MailProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsSesEmailProvider")
class AwsSesEmailProviderTest {

    @Mock
    private SesV2Client sesClient;

    private AwsSesEmailProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AwsSesEmailProvider(sesClient, "noreply@example.com", "my-config-set");
    }

    @Nested
    @DisplayName("getType")
    class GetType {

        @Test
        @DisplayName("Should return AWS_SES provider type")
        void shouldReturnAwsSesType() {
            assertThat(provider.getType()).isEqualTo(MailProviderType.AWS_SES);
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Should be available when SES client is provided")
        void shouldBeAvailableWithClient() {
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should not be available when SES client is null")
        void shouldNotBeAvailableWithNullClient() {
            AwsSesEmailProvider nullProvider = new AwsSesEmailProvider(null, null, null);

            assertThat(nullProvider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriority {

        @Test
        @DisplayName("Should have priority 100")
        void shouldHavePriority100() {
            assertThat(provider.getPriority()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("supportsBulkSend")
    class SupportsBulkSend {

        @Test
        @DisplayName("Should support bulk send")
        void shouldSupportBulkSend() {
            assertThat(provider.supportsBulkSend()).isTrue();
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("Should send email successfully via AWS SES")
        void shouldSendEmailSuccessfully() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-123")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getMessageId()).isEqualTo("ses-msg-123");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.AWS_SES);
            verify(sesClient).sendEmail(any(SendEmailRequest.class));
        }

        @Test
        @DisplayName("Should send email with both HTML and text body")
        void shouldSendWithBothBodies() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-456")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>HTML</p>")
                    .textBody("Text body")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with plain text only")
        void shouldSendWithPlainTextOnly() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-789")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.plainText("user@example.com", "Test", "Plain text");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with custom from address")
        void shouldSendWithCustomFrom() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-from")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

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
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-cc")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("to@example.com")))
                    .cc(List.of(EmailAddress.of("cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc@example.com")))
                    .replyTo(EmailAddress.of("reply@example.com"))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with attachments")
        void shouldSendWithAttachments() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-attach")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailAttachment attachment = EmailAttachment.of("file.pdf", "application/pdf", "content".getBytes());
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
        void shouldSendWithInlineAttachment() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-inline")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailAttachment inlineAttachment = EmailAttachment.inline(
                    "logo-cid", "logo.png", "image/png", new byte[]{1, 2}
            );
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("With Inline")
                    .htmlBody("<p><img src=\"cid:logo-cid\"/></p>")
                    .attachments(List.of(inlineAttachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with attachments and both HTML and text body")
        void shouldSendWithAttachmentsAndBothBodies() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-mixed")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailAttachment attachment = EmailAttachment.of("file.pdf", "application/pdf", "pdf".getBytes());
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Multi")
                    .htmlBody("<p>HTML</p>")
                    .textBody("Text")
                    .attachments(List.of(attachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with attachments and text only body")
        void shouldSendWithAttachmentsAndTextOnly() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-text-att")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailAttachment attachment = EmailAttachment.of("file.txt", "text/plain", "data".getBytes());
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Text Attach")
                    .textBody("Plain text content")
                    .attachments(List.of(attachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with custom headers")
        void shouldSendWithHeaders() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-headers")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .headers(Map.of("X-Custom-Header", "value"))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with tags as SES header")
        void shouldSendWithTags() {
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-tags")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .tags(List.of("marketing", "welcome"))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email without configuration set when not set")
        void shouldSendWithoutConfigurationSet() {
            AwsSesEmailProvider noConfigProvider = new AwsSesEmailProvider(sesClient, "noreply@example.com", null);
            SendEmailResponse response = SendEmailResponse.builder()
                    .messageId("ses-msg-noconfig")
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = noConfigProvider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("Should return retryable failure for Throttling error")
        void shouldReturnRetryableForThrottling() {
            SesV2Exception sesException = (SesV2Exception) SesV2Exception.builder()
                    .message("Rate exceeded")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("Throttling")
                            .errorMessage("Rate exceeded")
                            .build())
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("Throttling");
        }

        @Test
        @DisplayName("Should return retryable failure for ServiceUnavailable error")
        void shouldReturnRetryableForServiceUnavailable() {
            SesV2Exception sesException = (SesV2Exception) SesV2Exception.builder()
                    .message("Service unavailable")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("ServiceUnavailable")
                            .errorMessage("Service unavailable")
                            .build())
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should return non-retryable failure for other SES errors")
        void shouldReturnNonRetryableForOtherSesErrors() {
            SesV2Exception sesException = (SesV2Exception) SesV2Exception.builder()
                    .message("Invalid address")
                    .awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("MessageRejected")
                            .errorMessage("Invalid address")
                            .build())
                    .build();
            when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(sesException);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isFalse();
            assertThat(result.getErrorMessage()).contains("MessageRejected");
        }

        @Test
        @DisplayName("Should return failure for generic exception")
        void shouldReturnFailureForGenericException() {
            when(sesClient.sendEmail(any(SendEmailRequest.class)))
                    .thenThrow(new RuntimeException("Network error"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("AWS SES send failed");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("Should fail when provider is not available")
        void shouldFailWhenNotAvailable() {
            AwsSesEmailProvider unavailable = new AwsSesEmailProvider(null, null, null);
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = unavailable.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Provider not available");
        }
    }
}
