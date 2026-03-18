package dev.simplecore.simplix.email.provider;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResendEmailProvider")
class ResendEmailProviderTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    private ResendEmailProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ResendEmailProvider(resend, "noreply@example.com", "Test App");
    }

    @Nested
    @DisplayName("getType")
    class GetType {

        @Test
        @DisplayName("Should return RESEND provider type")
        void shouldReturnResendType() {
            assertThat(provider.getType()).isEqualTo(MailProviderType.RESEND);
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Should be available when Resend client is provided")
        void shouldBeAvailableWithClient() {
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should not be available when Resend client is null")
        void shouldNotBeAvailableWithNullClient() {
            ResendEmailProvider nullProvider = new ResendEmailProvider(null, null, null);

            assertThat(nullProvider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriority {

        @Test
        @DisplayName("Should have priority 55")
        void shouldHavePriority55() {
            assertThat(provider.getPriority()).isEqualTo(55);
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
        @DisplayName("Should send email successfully via Resend")
        void shouldSendEmailSuccessfully() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-msg-123");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getMessageId()).isEqualTo("resend-msg-123");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.RESEND);
            verify(emails).send(any(CreateEmailOptions.class));
        }

        @Test
        @DisplayName("Should generate fallback message ID when response ID is null")
        void shouldGenerateFallbackMessageId() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId(null);
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessageId()).startsWith("resend-");
        }

        @Test
        @DisplayName("Should generate fallback message ID when response ID is empty")
        void shouldGenerateFallbackMessageIdWhenEmpty() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessageId()).startsWith("resend-");
        }

        @Test
        @DisplayName("Should send email with custom from address")
        void shouldSendWithCustomFrom() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-from");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .from(EmailAddress.of("Sender", "sender@example.com"))
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with from address without name")
        void shouldSendWithFromWithoutName() throws ResendException {
            ResendEmailProvider noNameProvider = new ResendEmailProvider(resend, "noreply@example.com", null);
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-noname");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = noNameProvider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with CC and BCC")
        void shouldSendWithCcAndBcc() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-cc");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

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
        @DisplayName("Should send email with plain text content")
        void shouldSendWithPlainText() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-text");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.plainText("user@example.com", "Test", "Plain text");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with attachments")
        void shouldSendWithAttachments() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-attach");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailAttachment attachment = EmailAttachment.of("file.pdf", "application/pdf", "content".getBytes());
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Attached")
                    .htmlBody("<p>See attached</p>")
                    .attachments(List.of(attachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with tags")
        void shouldSendWithTags() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-tags");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Tagged")
                    .htmlBody("<p>Content</p>")
                    .tags(List.of("marketing"))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with custom headers")
        void shouldSendWithHeaders() throws ResendException {
            CreateEmailResponse response = new CreateEmailResponse();
            response.setId("resend-headers");
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .headers(Map.of("X-Custom", "value"))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("handleResendError")
    class HandleResendError {

        @Test
        @DisplayName("Should return retryable failure for rate limit error")
        void shouldReturnRetryableForRateLimit() throws ResendException {
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class)))
                    .thenThrow(new ResendException("rate limit exceeded"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("RESEND_ERROR");
        }

        @Test
        @DisplayName("Should return retryable failure for 429 error")
        void shouldReturnRetryableFor429() throws ResendException {
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class)))
                    .thenThrow(new ResendException("HTTP 429"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should return retryable failure for 500 error")
        void shouldReturnRetryableFor500() throws ResendException {
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class)))
                    .thenThrow(new ResendException("HTTP 500"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should return retryable failure for 503 error")
        void shouldReturnRetryableFor503() throws ResendException {
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class)))
                    .thenThrow(new ResendException("503 Service Unavailable"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should return non-retryable failure for other errors")
        void shouldReturnNonRetryableForOtherErrors() throws ResendException {
            when(resend.emails()).thenReturn(emails);
            when(emails.send(any(CreateEmailOptions.class)))
                    .thenThrow(new ResendException("Invalid API key"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isFalse();
            assertThat(result.getErrorMessage()).contains("Resend error");
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("Should return failure for generic exception")
        void shouldReturnFailureForGenericException() {
            when(resend.emails()).thenThrow(new RuntimeException("Unexpected error"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Resend send failed");
        }
    }
}
