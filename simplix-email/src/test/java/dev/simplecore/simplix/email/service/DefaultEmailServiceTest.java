package dev.simplecore.simplix.email.service;

import dev.simplecore.simplix.email.model.BulkEmailRequest;
import dev.simplecore.simplix.email.model.BulkEmailResult;
import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.TemplateEmailRequest;
import dev.simplecore.simplix.email.provider.EmailProvider;
import dev.simplecore.simplix.email.template.EmailTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultEmailService")
class DefaultEmailServiceTest {

    @Mock
    private EmailTemplateService templateService;

    private Executor asyncExecutor;
    private EmailAddress defaultFrom;

    @BeforeEach
    void setUp() {
        asyncExecutor = Executors.newSingleThreadExecutor();
        defaultFrom = EmailAddress.of("Test App", "noreply@example.com");
    }

    private DefaultEmailService createService(List<EmailProvider> providers) {
        return new DefaultEmailService(providers, templateService, asyncExecutor, defaultFrom);
    }

    private EmailProvider mockProvider(MailProviderType type, int priority, boolean available) {
        EmailProvider provider = mock(EmailProvider.class);
        lenient().when(provider.getType()).thenReturn(type);
        lenient().when(provider.getPriority()).thenReturn(priority);
        lenient().when(provider.isAvailable()).thenReturn(available);
        return provider;
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("Should send email via the first available provider")
        void shouldSendViaFirstAvailableProvider() {
            EmailProvider provider = mockProvider(MailProviderType.AWS_SES, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-123", MailProviderType.AWS_SES)
            );

            DefaultEmailService service = createService(List.of(provider));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = service.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessageId()).isEqualTo("msg-123");
            verify(provider).send(any());
        }

        @Test
        @DisplayName("Should set default from address when request from is null")
        void shouldSetDefaultFromAddress() {
            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            service.send(request);

            assertThat(request.getFrom()).isNotNull();
            assertThat(request.getFrom().getAddress()).isEqualTo("noreply@example.com");
        }

        @Test
        @DisplayName("Should not override from address when already set in request")
        void shouldNotOverrideFromAddress() {
            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            EmailRequest request = EmailRequest.builder()
                    .from(EmailAddress.of("custom@example.com"))
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            service.send(request);

            assertThat(request.getFrom().getAddress()).isEqualTo("custom@example.com");
        }

        @Test
        @DisplayName("Should skip unavailable providers")
        void shouldSkipUnavailableProviders() {
            EmailProvider unavailable = mockProvider(MailProviderType.AWS_SES, 100, false);
            EmailProvider available = mockProvider(MailProviderType.SMTP, 10, true);
            when(available.send(any())).thenReturn(
                    EmailResult.success("msg-2", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(unavailable, available));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = service.send(request);

            assertThat(result.isSuccess()).isTrue();
            verify(unavailable, never()).send(any());
            verify(available).send(any());
        }

        @Test
        @DisplayName("Should try next provider when current provider returns retryable failure")
        void shouldTryNextProviderOnRetryableFailure() {
            EmailProvider primary = mockProvider(MailProviderType.AWS_SES, 100, true);
            when(primary.send(any())).thenReturn(
                    EmailResult.retryableFailure("Throttled", "Throttling", MailProviderType.AWS_SES)
            );

            EmailProvider secondary = mockProvider(MailProviderType.SMTP, 10, true);
            when(secondary.send(any())).thenReturn(
                    EmailResult.success("msg-fallback", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(primary, secondary));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = service.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.SMTP);
            verify(primary).send(any());
            verify(secondary).send(any());
        }

        @Test
        @DisplayName("Should NOT try next provider on non-retryable failure")
        void shouldNotTryNextProviderOnNonRetryableFailure() {
            EmailProvider primary = mockProvider(MailProviderType.AWS_SES, 100, true);
            when(primary.send(any())).thenReturn(
                    EmailResult.failure("Invalid email", MailProviderType.AWS_SES)
            );

            EmailProvider secondary = mockProvider(MailProviderType.SMTP, 10, true);

            DefaultEmailService service = createService(List.of(primary, secondary));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = service.send(request);

            assertThat(result.isSuccess()).isFalse();
            verify(primary).send(any());
            verify(secondary, never()).send(any());
        }

        @Test
        @DisplayName("Should return failure when all providers fail or are unavailable")
        void shouldReturnFailureWhenAllProvidersFail() {
            EmailProvider p1 = mockProvider(MailProviderType.AWS_SES, 100, false);
            EmailProvider p2 = mockProvider(MailProviderType.SMTP, 10, false);

            DefaultEmailService service = createService(List.of(p1, p2));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = service.send(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("All email providers failed");
        }

        @Test
        @DisplayName("Should sort providers by priority (highest first)")
        void shouldSortProvidersByPriority() {
            EmailProvider lowPriority = mockProvider(MailProviderType.SMTP, 10, true);
            EmailProvider highPriority = mockProvider(MailProviderType.AWS_SES, 100, true);
            when(highPriority.send(any())).thenReturn(
                    EmailResult.success("msg-ses", MailProviderType.AWS_SES)
            );

            // Pass low priority first, but high priority should be tried first
            DefaultEmailService service = createService(List.of(lowPriority, highPriority));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            EmailResult result = service.send(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProviderType()).isEqualTo(MailProviderType.AWS_SES);
            verify(highPriority).send(any());
            verify(lowPriority, never()).send(any());
        }

        @Test
        @DisplayName("Should not set default from when defaultFrom is null")
        void shouldNotSetDefaultFromWhenNull() {
            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = new DefaultEmailService(
                    List.of(provider), templateService, asyncExecutor, null
            );
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            service.send(request);

            assertThat(request.getFrom()).isNull();
        }
    }

    @Nested
    @DisplayName("sendTemplate")
    class SendTemplate {

        @Test
        @DisplayName("Should process template and send email")
        void shouldProcessTemplateAndSend() {
            EmailRequest processedRequest = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Welcome!")
                    .htmlBody("<p>Hello, John!</p>")
                    .build();

            when(templateService.processTemplate(any(TemplateEmailRequest.class)))
                    .thenReturn(processedRequest);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-tmpl", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "welcome", "user@example.com", Map.of("name", "John")
            );

            EmailResult result = service.sendTemplate(request);

            assertThat(result.isSuccess()).isTrue();
            verify(templateService).processTemplate(any(TemplateEmailRequest.class));
        }

        @Test
        @DisplayName("Should return failure when template is not found")
        void shouldReturnFailureWhenTemplateNotFound() {
            when(templateService.processTemplate(any(TemplateEmailRequest.class)))
                    .thenThrow(new EmailTemplateService.TemplateNotFoundException("Template not found: missing"));

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);

            DefaultEmailService service = createService(List.of(provider));
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "missing", "user@example.com", Map.of()
            );

            EmailResult result = service.sendTemplate(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Template not found");
        }

        @Test
        @DisplayName("Should return failure when template processing throws exception")
        void shouldReturnFailureWhenProcessingFails() {
            when(templateService.processTemplate(any(TemplateEmailRequest.class)))
                    .thenThrow(new RuntimeException("Processing error"));

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);

            DefaultEmailService service = createService(List.of(provider));
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "broken", "user@example.com", Map.of()
            );

            EmailResult result = service.sendTemplate(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Template processing failed");
        }

        @Test
        @DisplayName("Should set default from on processed template request")
        void shouldSetDefaultFromOnProcessedRequest() {
            EmailRequest processedRequest = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Welcome!")
                    .htmlBody("<p>Hello!</p>")
                    .build();

            when(templateService.processTemplate(any(TemplateEmailRequest.class)))
                    .thenReturn(processedRequest);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-from", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "welcome", "user@example.com", Map.of()
            );

            service.sendTemplate(request);

            assertThat(processedRequest.getFrom()).isNotNull();
            assertThat(processedRequest.getFrom().getAddress()).isEqualTo("noreply@example.com");
        }
    }

    @Nested
    @DisplayName("sendTemplate with shortcut parameters")
    class SendTemplateShortcut {

        @Test
        @DisplayName("Should delegate to sendTemplate with TemplateEmailRequest")
        void shouldDelegateToSendTemplate() {
            EmailRequest processedRequest = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Welcome!")
                    .htmlBody("<p>Hello!</p>")
                    .build();

            when(templateService.processTemplate(any(TemplateEmailRequest.class)))
                    .thenReturn(processedRequest);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-short", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));

            EmailResult result = service.sendTemplate("welcome", "user@example.com", Map.of("name", "John"));

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("sendBulk")
    class SendBulk {

        @Test
        @DisplayName("Should send bulk emails to all recipients")
        void shouldSendToAllRecipients() {
            EmailRequest pr1 = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user1@example.com")))
                    .subject("Welcome")
                    .htmlBody("<p>Hello User1</p>")
                    .build();
            EmailRequest pr2 = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user2@example.com")))
                    .subject("Welcome")
                    .htmlBody("<p>Hello User2</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr1, pr2);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP),
                    EmailResult.success("msg-2", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user1@example.com"),
                            BulkEmailRequest.BulkRecipient.of("user2@example.com")
                    ))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getTotalCount()).isEqualTo(2);
            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getFailureCount()).isZero();
            assertThat(result.getResults()).hasSize(2);
            assertThat(result.isAllSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should use provided batch ID")
        void shouldUseProvidedBatchId() {
            EmailRequest pr = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .batchId("my-batch-123")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("user@example.com")))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getBatchId()).isEqualTo("my-batch-123");
        }

        @Test
        @DisplayName("Should generate batch ID when not provided")
        void shouldGenerateBatchId() {
            EmailRequest pr = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("user@example.com")))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getBatchId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("Should continue on error when continueOnError is true")
        void shouldContinueOnError() {
            EmailRequest pr1 = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user1@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();
            EmailRequest pr2 = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user2@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr1, pr2);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.failure("Failed for user1", MailProviderType.SMTP),
                    EmailResult.success("msg-2", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .continueOnError(true)
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user1@example.com"),
                            BulkEmailRequest.BulkRecipient.of("user2@example.com")
                    ))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.getResults()).hasSize(2);
        }

        @Test
        @DisplayName("Should stop on error when continueOnError is false")
        void shouldStopOnError() {
            EmailRequest pr1 = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user1@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr1);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.failure("Failed", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .continueOnError(false)
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user1@example.com"),
                            BulkEmailRequest.BulkRecipient.of("user2@example.com")
                    ))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.getPendingCount()).isEqualTo(1);
            assertThat(result.getResults()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle template processing exception in bulk send")
        void shouldHandleTemplateExceptionInBulk() {
            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenThrow(new RuntimeException("Template error"));

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("broken")
                    .continueOnError(true)
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user1@example.com"),
                            BulkEmailRequest.BulkRecipient.of("user2@example.com")
                    ))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getFailureCount()).isEqualTo(2);
            assertThat(result.getSuccessCount()).isZero();
        }

        @Test
        @DisplayName("Should stop on template exception when continueOnError is false")
        void shouldStopOnTemplateExceptionWhenNotContinueOnError() {
            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenThrow(new RuntimeException("Template error"));

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("broken")
                    .continueOnError(false)
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user1@example.com"),
                            BulkEmailRequest.BulkRecipient.of("user2@example.com")
                    ))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getFailureCount()).isEqualTo(1);
            assertThat(result.getPendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should merge common variables with recipient variables")
        void shouldMergeVariables() {
            EmailRequest pr = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .commonVariables(Map.of("appName", "MyApp", "year", "2026"))
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user@example.com", Map.of("name", "John"))
                    ))
                    .build();

            service.sendBulk(request);

            verify(templateService).processTemplate(
                    eq("welcome"),
                    any(EmailAddress.class),
                    anyMap(),
                    any()
            );
        }

        @Test
        @DisplayName("Should set from, subject, priority, tenantId, and tags from bulk request")
        void shouldSetFieldsFromBulkRequest() {
            EmailRequest pr = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("TemplateSubject")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .from(EmailAddress.of("bulk@example.com"))
                    .subject("Override Subject")
                    .tenantId("tenant-1")
                    .tags(List.of("bulk", "welcome"))
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("user@example.com")))
                    .build();

            service.sendBulk(request);

            assertThat(pr.getFrom().getAddress()).isEqualTo("bulk@example.com");
            assertThat(pr.getSubject()).isEqualTo("Override Subject");
            assertThat(pr.getTenantId()).isEqualTo("tenant-1");
            assertThat(pr.getTags()).containsExactly("bulk", "welcome");
        }

        @Test
        @DisplayName("Should set timestamps on result")
        void shouldSetTimestamps() {
            EmailRequest pr = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("user@example.com")))
                    .build();

            BulkEmailResult result = service.sendBulk(request);

            assertThat(result.getStartTime()).isNotNull();
            assertThat(result.getEndTime()).isNotNull();
            assertThat(result.getEndTime()).isAfterOrEqualTo(result.getStartTime());
        }
    }

    @Nested
    @DisplayName("sendAsync")
    class SendAsync {

        @Test
        @DisplayName("Should send email asynchronously")
        void shouldSendAsync() throws Exception {
            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-async", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            EmailRequest request = EmailRequest.simple("user@example.com", "Test", "<p>Hello</p>");

            CompletableFuture<EmailResult> future = service.sendAsync(request);
            EmailResult result = future.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessageId()).isEqualTo("msg-async");
        }
    }

    @Nested
    @DisplayName("sendTemplateAsync")
    class SendTemplateAsync {

        @Test
        @DisplayName("Should send template email asynchronously")
        void shouldSendTemplateAsync() throws Exception {
            EmailRequest processedRequest = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Welcome!")
                    .htmlBody("<p>Hello!</p>")
                    .build();

            when(templateService.processTemplate(any(TemplateEmailRequest.class)))
                    .thenReturn(processedRequest);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-tmpl-async", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "welcome", "user@example.com", Map.of()
            );

            CompletableFuture<EmailResult> future = service.sendTemplateAsync(request);
            EmailResult result = future.get();

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("sendBulkAsync")
    class SendBulkAsync {

        @Test
        @DisplayName("Should send bulk emails asynchronously")
        void shouldSendBulkAsync() throws Exception {
            EmailRequest pr = EmailRequest.builder()
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Test")
                    .htmlBody("<p>Hello</p>")
                    .build();

            when(templateService.processTemplate(anyString(), any(EmailAddress.class), anyMap(), any()))
                    .thenReturn(pr);

            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);
            when(provider.send(any())).thenReturn(
                    EmailResult.success("msg-1", MailProviderType.SMTP)
            );

            DefaultEmailService service = createService(List.of(provider));
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("user@example.com")))
                    .build();

            CompletableFuture<BulkEmailResult> future = service.sendBulkAsync(request);
            BulkEmailResult result = future.get();

            assertThat(result.getSuccessCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Should return true when at least one provider is available")
        void shouldReturnTrueWhenProviderAvailable() {
            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, true);

            DefaultEmailService service = createService(List.of(provider));

            assertThat(service.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should return false when no providers are available")
        void shouldReturnFalseWhenNoProviders() {
            EmailProvider provider = mockProvider(MailProviderType.SMTP, 100, false);

            DefaultEmailService service = createService(List.of(provider));

            assertThat(service.isAvailable()).isFalse();
        }
    }
}
