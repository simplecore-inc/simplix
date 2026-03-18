package dev.simplecore.simplix.auth.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenAuditEvent")
class TokenAuditEventTest {

    @Nested
    @DisplayName("success factory method")
    class SuccessFactory {

        @Test
        @DisplayName("should create event with NONE failure reason")
        void shouldCreateSuccessEvent() {
            TokenAuditEvent event = TokenAuditEvent.success(
                    "testuser", "jti-123", "127.0.0.1", "TestAgent", "access");

            assertThat(event.username()).isEqualTo("testuser");
            assertThat(event.jti()).isEqualTo("jti-123");
            assertThat(event.failureReason()).isEqualTo(TokenFailureReason.NONE);
            assertThat(event.clientIp()).isEqualTo("127.0.0.1");
            assertThat(event.userAgent()).isEqualTo("TestAgent");
            assertThat(event.tokenType()).isEqualTo("access");
            assertThat(event.additionalDetails()).isNull();
        }

        @Test
        @DisplayName("should report isSuccess as true")
        void shouldReportIsSuccess() {
            TokenAuditEvent event = TokenAuditEvent.success(
                    "user", "jti", "ip", "ua", "access");

            assertThat(event.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("failure factory method")
    class FailureFactory {

        @Test
        @DisplayName("should create event with specified failure reason")
        void shouldCreateFailureEvent() {
            TokenAuditEvent event = TokenAuditEvent.failure(
                    "testuser", "jti-456", TokenFailureReason.TOKEN_EXPIRED,
                    "192.168.1.1", "Chrome", "refresh");

            assertThat(event.username()).isEqualTo("testuser");
            assertThat(event.jti()).isEqualTo("jti-456");
            assertThat(event.failureReason()).isEqualTo(TokenFailureReason.TOKEN_EXPIRED);
            assertThat(event.clientIp()).isEqualTo("192.168.1.1");
            assertThat(event.userAgent()).isEqualTo("Chrome");
            assertThat(event.tokenType()).isEqualTo("refresh");
            assertThat(event.additionalDetails()).isNull();
        }

        @Test
        @DisplayName("should report isSuccess as false")
        void shouldReportIsSuccessFalse() {
            TokenAuditEvent event = TokenAuditEvent.failure(
                    "user", "jti", TokenFailureReason.TOKEN_REVOKED,
                    "ip", "ua", "access");

            assertThat(event.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should allow null username and jti for anonymous failures")
        void shouldAllowNullUsernameAndJti() {
            TokenAuditEvent event = TokenAuditEvent.failure(
                    null, null, TokenFailureReason.MALFORMED_TOKEN,
                    "10.0.0.1", "Curl", "access");

            assertThat(event.username()).isNull();
            assertThat(event.jti()).isNull();
            assertThat(event.failureReason()).isEqualTo(TokenFailureReason.MALFORMED_TOKEN);
        }
    }

    @Nested
    @DisplayName("failure factory method with additional details")
    class FailureFactoryWithDetails {

        @Test
        @DisplayName("should create event with additional details")
        void shouldCreateEventWithAdditionalDetails() {
            Map<String, Object> details = Map.of("oldJti", "old-123", "newJti", "new-456");

            TokenAuditEvent event = TokenAuditEvent.failure(
                    "user", "jti", TokenFailureReason.IP_MISMATCH,
                    "ip", "ua", "access", details);

            assertThat(event.additionalDetails()).isEqualTo(details);
            assertThat(event.additionalDetails()).containsEntry("oldJti", "old-123");
            assertThat(event.additionalDetails()).containsEntry("newJti", "new-456");
        }

        @Test
        @DisplayName("should allow null additional details")
        void shouldAllowNullAdditionalDetails() {
            TokenAuditEvent event = TokenAuditEvent.failure(
                    "user", "jti", TokenFailureReason.UNKNOWN,
                    "ip", "ua", "access", null);

            assertThat(event.additionalDetails()).isNull();
        }
    }

    @Nested
    @DisplayName("record equality and accessors")
    class RecordBehavior {

        @Test
        @DisplayName("should support record equality")
        void shouldSupportEquality() {
            TokenAuditEvent event1 = TokenAuditEvent.success("user", "jti", "ip", "ua", "access");
            TokenAuditEvent event2 = TokenAuditEvent.success("user", "jti", "ip", "ua", "access");

            assertThat(event1).isEqualTo(event2);
            assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
        }

        @Test
        @DisplayName("should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            TokenAuditEvent event = TokenAuditEvent.success("user", "jti", "ip", "ua", "access");

            assertThat(event.toString()).contains("user", "jti", "NONE");
        }
    }
}
