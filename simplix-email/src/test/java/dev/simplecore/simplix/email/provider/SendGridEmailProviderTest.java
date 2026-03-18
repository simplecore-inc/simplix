package dev.simplecore.simplix.email.provider;

import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendGridEmailProvider")
class SendGridEmailProviderTest {

    @Mock
    private SendGrid sendGrid;

    private SendGridEmailProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SendGridEmailProvider(sendGrid, "noreply@example.com", "Test App");
    }

    @Nested
    @DisplayName("getType")
    class GetType {

        @Test
        @DisplayName("Should return SENDGRID provider type")
        void shouldReturnSendGridType() {
            assertThat(provider.getType()).isEqualTo(MailProviderType.SENDGRID);
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Should be available when SendGrid client is provided")
        void shouldBeAvailableWithClient() {
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should not be available when SendGrid client is null")
        void shouldNotBeAvailableWithNullClient() {
            SendGridEmailProvider nullProvider = new SendGridEmailProvider(null, null, null);

            assertThat(nullProvider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriority {

        @Test
        @DisplayName("Should have priority 50")
        void shouldHavePriority50() {
            assertThat(provider.getPriority()).isEqualTo(50);
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
        @DisplayName("Should send email successfully via SendGrid")
        void shouldSendEmailSuccessfully() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-msg-123"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(result.getMessageId()).isEqualTo("sg-msg-123");
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.SENDGRID);
            verify(sendGrid).api(any(Request.class));
        }

        @Test
        @DisplayName("Should generate message ID when header is absent")
        void shouldGenerateMessageIdWhenHeaderAbsent() throws IOException {
            Response response = new Response();
            response.setStatusCode(200);
            response.setHeaders(Map.of());
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessageId()).startsWith("sendgrid-");
        }

        @Test
        @DisplayName("Should send email with plain text content")
        void shouldSendWithPlainText() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-txt"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.plainText("user@example.com", "Test", "Plain text");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with both HTML and text body")
        void shouldSendWithBothBodies() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-both"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

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
        void shouldSendWithCustomFrom() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-from"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

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
        @DisplayName("Should send email with CC, BCC, and ReplyTo")
        void shouldSendWithCcBccReplyTo() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-cc"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("to@example.com")))
                    .cc(List.of(EmailAddress.of("cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc@example.com")))
                    .replyTo(EmailAddress.of("Reply", "reply@example.com"))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with regular attachments")
        void shouldSendWithAttachments() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-attach"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

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
        @DisplayName("Should send email with inline attachment")
        void shouldSendWithInlineAttachment() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-inline"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailAttachment inlineAttachment = EmailAttachment.inline(
                    "logo-cid", "logo.png", "image/png", new byte[]{1, 2, 3}
            );
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Inline")
                    .htmlBody("<img src=\"cid:logo-cid\"/>")
                    .attachments(List.of(inlineAttachment))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with tags as categories")
        void shouldSendWithTags() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-tags"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Tagged")
                    .htmlBody("<p>Content</p>")
                    .tags(List.of("marketing", "welcome"))
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should send email with custom headers")
        void shouldSendWithHeaders() throws IOException {
            Response response = new Response();
            response.setStatusCode(202);
            response.setHeaders(Map.of("X-Message-Id", "sg-headers"));
            when(sendGrid.api(any(Request.class))).thenReturn(response);

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
    @DisplayName("handleSendGridError")
    class HandleSendGridError {

        @Test
        @DisplayName("Should return retryable failure for 429 rate limit")
        void shouldReturnRetryableFor429() throws IOException {
            Response response = new Response();
            response.setStatusCode(429);
            response.setBody("Rate limit exceeded");
            response.setHeaders(Map.of());
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("429");
        }

        @Test
        @DisplayName("Should return retryable failure for 500 server error")
        void shouldReturnRetryableFor500() throws IOException {
            Response response = new Response();
            response.setStatusCode(500);
            response.setBody("Internal server error");
            response.setHeaders(Map.of());
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("Should return non-retryable failure for 400 bad request")
        void shouldReturnNonRetryableFor400() throws IOException {
            Response response = new Response();
            response.setStatusCode(400);
            response.setBody("Bad request - invalid email");
            response.setHeaders(Map.of());
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetryable()).isFalse();
            assertThat(result.getErrorMessage()).contains("400");
        }

        @Test
        @DisplayName("Should include body in error message when available")
        void shouldIncludeBodyInError() throws IOException {
            Response response = new Response();
            response.setStatusCode(403);
            response.setBody("Forbidden");
            response.setHeaders(Map.of());
            when(sendGrid.api(any(Request.class))).thenReturn(response);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Forbidden");
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("Should return failure for IOException")
        void shouldReturnFailureForIOException() throws IOException {
            when(sendGrid.api(any(Request.class))).thenThrow(new IOException("Network error"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("SendGrid send failed");
        }
    }
}
