package dev.simplecore.simplix.email.template;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.MailPriority;
import dev.simplecore.simplix.email.model.TemplateEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTemplateService")
class EmailTemplateServiceTest {

    @Mock
    private EmailTemplateResolver highPriorityResolver;

    @Mock
    private EmailTemplateResolver lowPriorityResolver;

    @Mock
    private EmailTemplateEngine templateEngine;

    private EmailTemplateService service;

    @BeforeEach
    void setUp() {
        lenient().when(highPriorityResolver.getPriority()).thenReturn(100);
        lenient().when(lowPriorityResolver.getPriority()).thenReturn(10);

        service = new EmailTemplateService(
                List.of(lowPriorityResolver, highPriorityResolver),
                templateEngine
        );
    }

    @Nested
    @DisplayName("processTemplate with TemplateEmailRequest")
    class ProcessTemplateRequest {

        @Test
        @DisplayName("Should resolve template and process it")
        void shouldResolveAndProcess() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome", "Welcome!", "<p>Hello</p>", "Hello", Locale.ENGLISH
            );
            EmailTemplateEngine.ProcessedTemplate processed = new EmailTemplateEngine.ProcessedTemplate(
                    "Welcome!", "<p>Hello, John!</p>", "Hello, John!"
            );

            when(highPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.of(resolved));
            when(templateEngine.process(eq(resolved), anyMap()))
                    .thenReturn(processed);

            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("welcome")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .cc(List.of(EmailAddress.of("cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc@example.com")))
                    .replyTo(EmailAddress.of("reply@example.com"))
                    .from(EmailAddress.of("sender@example.com"))
                    .variables(Map.of("name", "John"))
                    .locale(Locale.ENGLISH)
                    .priority(MailPriority.HIGH)
                    .tenantId("tenant-1")
                    .correlationId("corr-123")
                    .tags(List.of("welcome"))
                    .build();

            EmailRequest result = service.processTemplate(request);

            assertThat(result.getSubject()).isEqualTo("Welcome!");
            assertThat(result.getHtmlBody()).isEqualTo("<p>Hello, John!</p>");
            assertThat(result.getTextBody()).isEqualTo("Hello, John!");
            assertThat(result.getTo()).hasSize(1);
            assertThat(result.getCc()).hasSize(1);
            assertThat(result.getBcc()).hasSize(1);
            assertThat(result.getFrom().getAddress()).isEqualTo("sender@example.com");
            assertThat(result.getReplyTo().getAddress()).isEqualTo("reply@example.com");
            assertThat(result.getPriority()).isEqualTo(MailPriority.HIGH);
            assertThat(result.getTenantId()).isEqualTo("tenant-1");
            assertThat(result.getCorrelationId()).isEqualTo("corr-123");
            assertThat(result.getTags()).containsExactly("welcome");
        }

        @Test
        @DisplayName("Should use subject override when provided")
        void shouldUseSubjectOverride() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome", "Default Subject", "<p>Hello</p>", null, Locale.ENGLISH
            );
            EmailTemplateEngine.ProcessedTemplate processed = new EmailTemplateEngine.ProcessedTemplate(
                    "Default Subject", "<p>Hello!</p>", null
            );

            when(highPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.of(resolved));
            when(templateEngine.process(eq(resolved), anyMap()))
                    .thenReturn(processed);

            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("welcome")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .subject("Custom Subject")
                    .locale(Locale.ENGLISH)
                    .build();

            EmailRequest result = service.processTemplate(request);

            assertThat(result.getSubject()).isEqualTo("Custom Subject");
        }

        @Test
        @DisplayName("Should use template subject when no override provided")
        void shouldUseTemplateSubjectWhenNoOverride() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome", "Template Subject", "<p>Hello</p>", null, Locale.ENGLISH
            );
            EmailTemplateEngine.ProcessedTemplate processed = new EmailTemplateEngine.ProcessedTemplate(
                    "Template Subject", "<p>Hello!</p>", null
            );

            when(highPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.of(resolved));
            when(templateEngine.process(eq(resolved), anyMap()))
                    .thenReturn(processed);

            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("welcome")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .locale(Locale.ENGLISH)
                    .build();

            EmailRequest result = service.processTemplate(request);

            assertThat(result.getSubject()).isEqualTo("Template Subject");
        }

        @Test
        @DisplayName("Should throw TemplateNotFoundException when template is not found")
        void shouldThrowWhenTemplateNotFound() {
            when(highPriorityResolver.resolve("missing", Locale.ENGLISH))
                    .thenReturn(Optional.empty());
            when(lowPriorityResolver.resolve("missing", Locale.ENGLISH))
                    .thenReturn(Optional.empty());

            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("missing")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .locale(Locale.ENGLISH)
                    .build();

            assertThatThrownBy(() -> service.processTemplate(request))
                    .isInstanceOf(EmailTemplateService.TemplateNotFoundException.class)
                    .hasMessageContaining("missing");
        }
    }

    @Nested
    @DisplayName("processTemplate with individual parameters")
    class ProcessTemplateParams {

        @Test
        @DisplayName("Should resolve and process template for a single recipient")
        void shouldProcessForSingleRecipient() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome", "Welcome!", "<p>Hello</p>", "Hello", Locale.ENGLISH
            );
            EmailTemplateEngine.ProcessedTemplate processed = new EmailTemplateEngine.ProcessedTemplate(
                    "Welcome!", "<p>Hello, Alice!</p>", "Hello, Alice!"
            );

            when(highPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.of(resolved));
            when(templateEngine.process(eq(resolved), anyMap()))
                    .thenReturn(processed);

            EmailAddress to = EmailAddress.of("alice@example.com");
            Map<String, Object> variables = Map.of("name", "Alice");

            EmailRequest result = service.processTemplate("welcome", to, variables, Locale.ENGLISH);

            assertThat(result.getTo()).hasSize(1);
            assertThat(result.getTo().get(0).getAddress()).isEqualTo("alice@example.com");
            assertThat(result.getSubject()).isEqualTo("Welcome!");
            assertThat(result.getHtmlBody()).isEqualTo("<p>Hello, Alice!</p>");
            assertThat(result.getTextBody()).isEqualTo("Hello, Alice!");
        }
    }

    @Nested
    @DisplayName("render")
    class Render {

        @Test
        @DisplayName("Should render text template with variables")
        void shouldRenderTextTemplate() {
            when(templateEngine.processText("Hello [(${name})]", Map.of("name", "Bob"), Locale.ENGLISH))
                    .thenReturn("Hello Bob");

            String result = service.render("Hello [(${name})]", Map.of("name", "Bob"), Locale.ENGLISH);

            assertThat(result).isEqualTo("Hello Bob");
            verify(templateEngine).processText(any(), anyMap(), any());
        }
    }

    @Nested
    @DisplayName("templateExists")
    class TemplateExists {

        @Test
        @DisplayName("Should return true when any resolver has the template")
        void shouldReturnTrueWhenExists() {
            when(highPriorityResolver.exists("welcome")).thenReturn(false);
            when(lowPriorityResolver.exists("welcome")).thenReturn(true);

            assertThat(service.templateExists("welcome")).isTrue();
        }

        @Test
        @DisplayName("Should return false when no resolver has the template")
        void shouldReturnFalseWhenNotExists() {
            when(highPriorityResolver.exists("missing")).thenReturn(false);
            when(lowPriorityResolver.exists("missing")).thenReturn(false);

            assertThat(service.templateExists("missing")).isFalse();
        }
    }

    @Nested
    @DisplayName("resolver priority ordering")
    class ResolverOrdering {

        @Test
        @DisplayName("Should try higher priority resolver first")
        void shouldTryHigherPriorityFirst() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome", "DB Subject", "<p>DB</p>", null, Locale.ENGLISH
            );
            EmailTemplateEngine.ProcessedTemplate processed = new EmailTemplateEngine.ProcessedTemplate(
                    "DB Subject", "<p>DB Content</p>", null
            );

            when(highPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.of(resolved));
            when(templateEngine.process(eq(resolved), anyMap()))
                    .thenReturn(processed);

            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("welcome")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .locale(Locale.ENGLISH)
                    .build();

            service.processTemplate(request);

            verify(highPriorityResolver).resolve("welcome", Locale.ENGLISH);
        }

        @Test
        @DisplayName("Should fall back to lower priority resolver when higher returns empty")
        void shouldFallbackToLowerPriority() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome", "FS Subject", "<p>FS</p>", null, Locale.ENGLISH
            );
            EmailTemplateEngine.ProcessedTemplate processed = new EmailTemplateEngine.ProcessedTemplate(
                    "FS Subject", "<p>FS Content</p>", null
            );

            when(highPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.empty());
            when(lowPriorityResolver.resolve("welcome", Locale.ENGLISH))
                    .thenReturn(Optional.of(resolved));
            when(templateEngine.process(eq(resolved), anyMap()))
                    .thenReturn(processed);

            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("welcome")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .locale(Locale.ENGLISH)
                    .build();

            service.processTemplate(request);

            verify(highPriorityResolver).resolve("welcome", Locale.ENGLISH);
            verify(lowPriorityResolver).resolve("welcome", Locale.ENGLISH);
        }
    }

    @Nested
    @DisplayName("TemplateNotFoundException")
    class TemplateNotFoundExceptionTest {

        @Test
        @DisplayName("Should preserve message")
        void shouldPreserveMessage() {
            EmailTemplateService.TemplateNotFoundException ex =
                    new EmailTemplateService.TemplateNotFoundException("Template not found: test");

            assertThat(ex.getMessage()).isEqualTo("Template not found: test");
        }
    }
}
