package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogMasker")
class LogMaskerTest {

    @Nested
    @DisplayName("maskSensitiveData")
    class MaskSensitiveData {

        @Test
        @DisplayName("should mask email in text")
        void shouldMaskEmailInText() {
            String result = LogMasker.maskSensitiveData("Contact us at user@example.com for info");

            assertThat(result).contains("us***@example.com");
            assertThat(result).doesNotContain("user@example.com");
        }

        @Test
        @DisplayName("should mask password in JSON format")
        void shouldMaskPasswordInJson() {
            String result = LogMasker.maskSensitiveData("{\"password\":\"secret123\"}");

            assertThat(result).contains("********");
            assertThat(result).doesNotContain("secret123");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskSensitiveData(null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(LogMasker.maskSensitiveData("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("maskRRN")
    class MaskRRN {

        @Test
        @DisplayName("should mask RRN in text")
        void shouldMaskRrnInText() {
            String result = LogMasker.maskRRN("RRN: 901231-1234567");

            assertThat(result).contains("901231-*******");
            assertThat(result).doesNotContain("1234567");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskRRN(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskCreditCard")
    class MaskCreditCard {

        @Test
        @DisplayName("should mask credit card number in text")
        void shouldMaskCreditCardInText() {
            String result = LogMasker.maskCreditCard("Card: 1234-5678-9012-3456");

            assertThat(result).doesNotContain("1234-5678-9012-3456");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskCreditCard(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskEmail")
    class MaskEmail {

        @Test
        @DisplayName("should mask email in text")
        void shouldMaskEmailInText() {
            String result = LogMasker.maskEmail("Email: test@domain.com");

            assertThat(result).contains("te***@domain.com");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskEmail(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskPassword")
    class MaskPassword {

        @Test
        @DisplayName("should mask password value in key-value format")
        void shouldMaskPasswordValue() {
            String result = LogMasker.maskPassword("password=mySecret123");

            assertThat(result).contains("********");
            assertThat(result).doesNotContain("mySecret123");
        }

        @Test
        @DisplayName("should mask api_key value")
        void shouldMaskApiKey() {
            String result = LogMasker.maskPassword("api_key=abc123xyz");

            assertThat(result).contains("********");
            assertThat(result).doesNotContain("abc123xyz");
        }

        @Test
        @DisplayName("should mask token value")
        void shouldMaskTokenValue() {
            String result = LogMasker.maskPassword("token=bearer-abc123");

            assertThat(result).contains("********");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskPassword(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskIPAddress")
    class MaskIPAddress {

        @Test
        @DisplayName("should mask IP address in text")
        void shouldMaskIpInText() {
            String result = LogMasker.maskIPAddress("Client IP: 192.168.1.100");

            assertThat(result).contains("192.168.1.0");
            assertThat(result).doesNotContain("192.168.1.100");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskIPAddress(null)).isNull();
        }
    }

    @Nested
    @DisplayName("containsSensitiveData")
    class ContainsSensitiveData {

        @Test
        @DisplayName("should detect email in text")
        void shouldDetectEmail() {
            assertThat(LogMasker.containsSensitiveData("contact user@example.com")).isTrue();
        }

        @Test
        @DisplayName("should detect password pattern")
        void shouldDetectPassword() {
            assertThat(LogMasker.containsSensitiveData("password=secret")).isTrue();
        }

        @Test
        @DisplayName("should return false for safe text")
        void shouldReturnFalseForSafeText() {
            assertThat(LogMasker.containsSensitiveData("Hello World")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(LogMasker.containsSensitiveData(null)).isFalse();
            assertThat(LogMasker.containsSensitiveData("")).isFalse();
        }
    }

    @Nested
    @DisplayName("maskFieldValue")
    class MaskFieldValue {

        @Test
        @DisplayName("should mask password field")
        void shouldMaskPasswordField() {
            assertThat(LogMasker.maskFieldValue("password", "secret123")).isEqualTo("********");
        }

        @Test
        @DisplayName("should mask email field")
        void shouldMaskEmailField() {
            assertThat(LogMasker.maskFieldValue("email", "user@example.com")).isEqualTo("us***@example.com");
        }

        @Test
        @DisplayName("should mask phone field")
        void shouldMaskPhoneField() {
            String result = LogMasker.maskFieldValue("phone", "010-1234-5678");

            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should mask token field")
        void shouldMaskTokenField() {
            assertThat(LogMasker.maskFieldValue("apiToken", "abc123")).isEqualTo("********");
        }

        @Test
        @DisplayName("should return null for null value")
        void shouldReturnNullForNullValue() {
            assertThat(LogMasker.maskFieldValue("password", null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty value")
        void shouldReturnEmptyForEmptyValue() {
            assertThat(LogMasker.maskFieldValue("password", "")).isEmpty();
        }
    }
}
