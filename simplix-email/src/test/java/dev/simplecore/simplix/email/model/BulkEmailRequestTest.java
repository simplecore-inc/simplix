package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BulkEmailRequest")
class BulkEmailRequestTest {

    @Nested
    @DisplayName("BulkRecipient")
    class BulkRecipientTest {

        @Test
        @DisplayName("of(email) should create recipient with address only")
        void ofEmailShouldCreateRecipient() {
            BulkEmailRequest.BulkRecipient recipient = BulkEmailRequest.BulkRecipient.of("user@example.com");

            assertThat(recipient.getAddress().getAddress()).isEqualTo("user@example.com");
            assertThat(recipient.getVariables()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("of(email, variables) should create recipient with variables")
        void ofEmailAndVariablesShouldCreateRecipient() {
            Map<String, Object> vars = Map.of("name", "John");
            BulkEmailRequest.BulkRecipient recipient = BulkEmailRequest.BulkRecipient.of(
                    "user@example.com", vars
            );

            assertThat(recipient.getAddress().getAddress()).isEqualTo("user@example.com");
            assertThat(recipient.getVariables()).containsEntry("name", "John");
        }

        @Test
        @DisplayName("of(email, null) should use empty map for variables")
        void ofEmailAndNullVariablesShouldUseEmptyMap() {
            BulkEmailRequest.BulkRecipient recipient = BulkEmailRequest.BulkRecipient.of(
                    "user@example.com", null
            );

            assertThat(recipient.getVariables()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("getRecipientCount")
    class GetRecipientCount {

        @Test
        @DisplayName("Should return count of recipients")
        void shouldReturnCount() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("a@example.com"),
                            BulkEmailRequest.BulkRecipient.of("b@example.com"),
                            BulkEmailRequest.BulkRecipient.of("c@example.com")
                    ))
                    .build();

            assertThat(request.getRecipientCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return 0 when recipients is null")
        void shouldReturnZeroWhenNull() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("welcome")
                    .build();

            assertThat(request.getRecipientCount()).isZero();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("Should use LOW priority by default for bulk email")
        void shouldUseLowPriority() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("test")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("a@example.com")))
                    .build();

            assertThat(request.getPriority()).isEqualTo(MailPriority.LOW);
        }

        @Test
        @DisplayName("Should default continueOnError to true")
        void shouldDefaultContinueOnErrorToTrue() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("test")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("a@example.com")))
                    .build();

            assertThat(request.isContinueOnError()).isTrue();
        }

        @Test
        @DisplayName("Should default collections to empty")
        void shouldDefaultCollectionsToEmpty() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("test")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("a@example.com")))
                    .build();

            assertThat(request.getCommonVariables()).isNotNull().isEmpty();
            assertThat(request.getTags()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Should use default locale")
        void shouldUseDefaultLocale() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("test")
                    .recipients(List.of(BulkEmailRequest.BulkRecipient.of("a@example.com")))
                    .build();

            assertThat(request.getLocale()).isEqualTo(Locale.getDefault());
        }
    }

    @Nested
    @DisplayName("builder with all fields")
    class BuilderAllFields {

        @Test
        @DisplayName("Should set all fields via builder")
        void shouldSetAllFields() {
            BulkEmailRequest request = BulkEmailRequest.builder()
                    .templateCode("newsletter")
                    .from(EmailAddress.of("noreply@example.com"))
                    .recipients(List.of(
                            BulkEmailRequest.BulkRecipient.of("user1@example.com"),
                            BulkEmailRequest.BulkRecipient.of("user2@example.com")
                    ))
                    .subject("Monthly Newsletter")
                    .commonVariables(Map.of("month", "January"))
                    .locale(Locale.ENGLISH)
                    .priority(MailPriority.NORMAL)
                    .tenantId("tenant-1")
                    .batchId("batch-001")
                    .tags(List.of("newsletter", "monthly"))
                    .continueOnError(false)
                    .build();

            assertThat(request.getTemplateCode()).isEqualTo("newsletter");
            assertThat(request.getFrom().getAddress()).isEqualTo("noreply@example.com");
            assertThat(request.getRecipientCount()).isEqualTo(2);
            assertThat(request.getSubject()).isEqualTo("Monthly Newsletter");
            assertThat(request.getCommonVariables()).containsEntry("month", "January");
            assertThat(request.getLocale()).isEqualTo(Locale.ENGLISH);
            assertThat(request.getPriority()).isEqualTo(MailPriority.NORMAL);
            assertThat(request.getTenantId()).isEqualTo("tenant-1");
            assertThat(request.getBatchId()).isEqualTo("batch-001");
            assertThat(request.getTags()).containsExactly("newsletter", "monthly");
            assertThat(request.isContinueOnError()).isFalse();
        }
    }
}
