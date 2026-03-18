package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.MailProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AbstractEmailProvider")
class AbstractEmailProviderTest {

    /**
     * Test implementation of AbstractEmailProvider for testing base class behavior.
     */
    static class TestEmailProvider extends AbstractEmailProvider {

        private boolean available = true;
        private EmailResult doSendResult;
        private Exception doSendException;

        @Override
        protected EmailResult doSend(EmailRequest request) throws Exception {
            if (doSendException != null) {
                throw doSendException;
            }
            return doSendResult != null ? doSendResult :
                    EmailResult.success("test-msg-id", getType());
        }

        @Override
        public MailProviderType getType() {
            return MailProviderType.CONSOLE;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        void setAvailable(boolean available) {
            this.available = available;
        }

        void setDoSendResult(EmailResult result) {
            this.doSendResult = result;
        }

        void setDoSendException(Exception exception) {
            this.doSendException = exception;
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("Should return failure when provider is not available")
        void shouldReturnFailureWhenNotAvailable() {
            TestEmailProvider provider = new TestEmailProvider();
            provider.setAvailable(false);

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Provider not available");
        }

        @Test
        @DisplayName("Should return success for valid request")
        void shouldReturnSuccessForValidRequest() {
            TestEmailProvider provider = new TestEmailProvider();

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should return failure when doSend throws exception")
        void shouldReturnFailureWhenDoSendThrows() {
            TestEmailProvider provider = new TestEmailProvider();
            provider.setDoSendException(new RuntimeException("Send failed"));

            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");
            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Send failed");
        }
    }

    @Nested
    @DisplayName("validateRequest")
    class ValidateRequest {

        @Test
        @DisplayName("Should fail when request is null")
        void shouldFailWhenNull() {
            TestEmailProvider provider = new TestEmailProvider();

            EmailResult result = provider.send(null);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Email request cannot be null");
        }

        @Test
        @DisplayName("Should fail when no recipients")
        void shouldFailWhenNoRecipients() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("At least one recipient");
        }

        @Test
        @DisplayName("Should fail when recipients list is empty")
        void shouldFailWhenRecipientsEmpty() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of())
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("At least one recipient");
        }

        @Test
        @DisplayName("Should fail when subject is null")
        void shouldFailWhenSubjectNull() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Subject is required");
        }

        @Test
        @DisplayName("Should fail when subject is blank")
        void shouldFailWhenSubjectBlank() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("   ")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Subject is required");
        }

        @Test
        @DisplayName("Should fail when no body (HTML or text) is provided")
        void shouldFailWhenNoBody() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Email body");
        }

        @Test
        @DisplayName("Should pass validation when only HTML body is provided")
        void shouldPassWithHtmlBodyOnly() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should pass validation when only text body is provided")
        void shouldPassWithTextBodyOnly() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .textBody("Plain text content")
                    .build();

            EmailResult result = provider.send(request);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("maskRecipients")
    class MaskRecipients {

        @Test
        @DisplayName("Should mask recipient addresses")
        void shouldMaskRecipients() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("john@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            String masked = provider.maskRecipients(request);

            assertThat(masked).contains("j***@example.com");
        }

        @Test
        @DisplayName("Should return empty array string when no recipients")
        void shouldReturnEmptyWhenNoRecipients() {
            TestEmailProvider provider = new TestEmailProvider();
            EmailRequest request = EmailRequest.builder()
                    .subject("Test")
                    .htmlBody("<p>Content</p>")
                    .build();

            String masked = provider.maskRecipients(request);

            assertThat(masked).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("default interface methods")
    class DefaultMethods {

        @Test
        @DisplayName("getPriority should return 0 by default")
        void getPriorityShouldReturnZero() {
            EmailProvider provider = new EmailProvider() {
                @Override
                public EmailResult send(EmailRequest request) {
                    return null;
                }

                @Override
                public MailProviderType getType() {
                    return MailProviderType.CONSOLE;
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }
            };

            assertThat(provider.getPriority()).isZero();
        }

        @Test
        @DisplayName("supportsBulkSend should return false by default")
        void supportsBulkSendShouldReturnFalse() {
            EmailProvider provider = new EmailProvider() {
                @Override
                public EmailResult send(EmailRequest request) {
                    return null;
                }

                @Override
                public MailProviderType getType() {
                    return MailProviderType.CONSOLE;
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }
            };

            assertThat(provider.supportsBulkSend()).isFalse();
        }
    }
}
