package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemplateEmailRequest")
class TemplateEmailRequestTest {

    @Nested
    @DisplayName("of factory method")
    class OfFactory {

        @Test
        @DisplayName("Should create request with template code, recipient, and variables")
        void shouldCreateRequest() {
            Map<String, Object> variables = Map.of("name", "John");
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "welcome", "user@example.com", variables
            );

            assertThat(request.getTemplateCode()).isEqualTo("welcome");
            assertThat(request.getTo()).hasSize(1);
            assertThat(request.getTo().get(0).getAddress()).isEqualTo("user@example.com");
            assertThat(request.getVariables()).containsEntry("name", "John");
        }

        @Test
        @DisplayName("Should use empty map when variables are null")
        void shouldUseEmptyMapWhenVariablesAreNull() {
            TemplateEmailRequest request = TemplateEmailRequest.of(
                    "welcome", "user@example.com", null
            );

            assertThat(request.getVariables()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("Should use default locale")
        void shouldUseDefaultLocale() {
            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("test")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .build();

            assertThat(request.getLocale()).isEqualTo(Locale.getDefault());
        }

        @Test
        @DisplayName("Should use NORMAL priority by default")
        void shouldUseNormalPriority() {
            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("test")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .build();

            assertThat(request.getPriority()).isEqualTo(MailPriority.NORMAL);
        }

        @Test
        @DisplayName("Should default collections to empty")
        void shouldDefaultCollectionsToEmpty() {
            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("test")
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .build();

            assertThat(request.getCc()).isNotNull().isEmpty();
            assertThat(request.getBcc()).isNotNull().isEmpty();
            assertThat(request.getAttachments()).isNotNull().isEmpty();
            assertThat(request.getVariables()).isNotNull().isEmpty();
            assertThat(request.getTags()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("builder with all fields")
    class BuilderAllFields {

        @Test
        @DisplayName("Should set all fields via builder")
        void shouldSetAllFields() {
            TemplateEmailRequest request = TemplateEmailRequest.builder()
                    .templateCode("order-confirm")
                    .from(EmailAddress.of("noreply@example.com"))
                    .to(List.of(EmailAddress.of("user@example.com")))
                    .cc(List.of(EmailAddress.of("cc@example.com")))
                    .bcc(List.of(EmailAddress.of("bcc@example.com")))
                    .replyTo(EmailAddress.of("support@example.com"))
                    .subject("Custom Subject")
                    .variables(Map.of("orderId", "ORD-123"))
                    .locale(Locale.KOREAN)
                    .priority(MailPriority.HIGH)
                    .tenantId("tenant-1")
                    .correlationId("corr-123")
                    .tags(List.of("order", "notification"))
                    .build();

            assertThat(request.getTemplateCode()).isEqualTo("order-confirm");
            assertThat(request.getFrom().getAddress()).isEqualTo("noreply@example.com");
            assertThat(request.getTo()).hasSize(1);
            assertThat(request.getCc()).hasSize(1);
            assertThat(request.getBcc()).hasSize(1);
            assertThat(request.getReplyTo().getAddress()).isEqualTo("support@example.com");
            assertThat(request.getSubject()).isEqualTo("Custom Subject");
            assertThat(request.getVariables()).containsEntry("orderId", "ORD-123");
            assertThat(request.getLocale()).isEqualTo(Locale.KOREAN);
            assertThat(request.getPriority()).isEqualTo(MailPriority.HIGH);
            assertThat(request.getTenantId()).isEqualTo("tenant-1");
            assertThat(request.getCorrelationId()).isEqualTo("corr-123");
            assertThat(request.getTags()).containsExactly("order", "notification");
        }
    }
}
