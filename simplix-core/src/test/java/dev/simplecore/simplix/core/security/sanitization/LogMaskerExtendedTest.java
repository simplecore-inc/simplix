package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogMasker - Extended Coverage")
class LogMaskerExtendedTest {

    @Nested
    @DisplayName("maskSensitiveData")
    class MaskSensitiveData {

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

        @Test
        @DisplayName("should mask email in text")
        void shouldMaskEmail() {
            String result = LogMasker.maskSensitiveData("Contact user@example.com for details");
            assertThat(result).doesNotContain("user@example.com");
            assertThat(result).contains("@example.com");
        }

        @Test
        @DisplayName("should mask password in JSON")
        void shouldMaskPassword() {
            String result = LogMasker.maskSensitiveData("password: mysecret123");
            assertThat(result).doesNotContain("mysecret123");
            assertThat(result).contains("********");
        }
    }

    @Nested
    @DisplayName("maskIPAddress")
    class MaskIPAddress {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(LogMasker.maskIPAddress(null)).isNull();
        }

        @Test
        @DisplayName("should mask IP addresses in text")
        void shouldMaskIPs() {
            String result = LogMasker.maskIPAddress("Request from 192.168.1.100");
            assertThat(result).contains("192.168.1.0");
        }
    }

    @Nested
    @DisplayName("containsSensitiveData")
    class ContainsSensitiveData {

        @Test
        @DisplayName("should return false for null/empty")
        void shouldReturnFalseForNullEmpty() {
            assertThat(LogMasker.containsSensitiveData(null)).isFalse();
            assertThat(LogMasker.containsSensitiveData("")).isFalse();
        }

        @Test
        @DisplayName("should detect email addresses")
        void shouldDetectEmails() {
            assertThat(LogMasker.containsSensitiveData("user@example.com")).isTrue();
        }

        @Test
        @DisplayName("should detect password patterns")
        void shouldDetectPasswords() {
            assertThat(LogMasker.containsSensitiveData("password: secret123")).isTrue();
        }

        @Test
        @DisplayName("should return false for clean data")
        void shouldReturnFalseForClean() {
            assertThat(LogMasker.containsSensitiveData("Hello World")).isFalse();
        }
    }

    @Nested
    @DisplayName("maskFieldValue")
    class MaskFieldValue {

        @Test
        @DisplayName("should return null/empty as-is")
        void shouldReturnNullEmptyAsIs() {
            assertThat(LogMasker.maskFieldValue("password", null)).isNull();
            assertThat(LogMasker.maskFieldValue("password", "")).isEmpty();
        }

        @Test
        @DisplayName("should mask password fields")
        void shouldMaskPasswordFields() {
            assertThat(LogMasker.maskFieldValue("password", "secret")).isEqualTo("********");
            assertThat(LogMasker.maskFieldValue("userSecret", "data")).isEqualTo("********");
            assertThat(LogMasker.maskFieldValue("authToken", "tok123")).isEqualTo("********");
            assertThat(LogMasker.maskFieldValue("apikey", "key123")).isEqualTo("********");
            assertThat(LogMasker.maskFieldValue("api_key", "key456")).isEqualTo("********");
        }

        @Test
        @DisplayName("should mask email fields")
        void shouldMaskEmailFields() {
            String result = LogMasker.maskFieldValue("userEmail", "test@example.com");
            assertThat(result).isNotEqualTo("test@example.com");
            assertThat(result).contains("@example.com");
        }

        @Test
        @DisplayName("should mask phone fields")
        void shouldMaskPhoneFields() {
            String result = LogMasker.maskFieldValue("phoneNumber", "010-1234-5678");
            assertThat(result).doesNotContain("1234-5678");
        }

        @Test
        @DisplayName("should mask RRN/SSN fields")
        void shouldMaskRrnFields() {
            String result = LogMasker.maskFieldValue("rrn", "900101-1234567");
            assertThat(result).doesNotContain("1234567");
        }

        @Test
        @DisplayName("should mask credit card fields")
        void shouldMaskCreditCardFields() {
            String result = LogMasker.maskFieldValue("creditCard", "1234-5678-9012-3456");
            assertThat(result).contains("3456");
        }

        @Test
        @DisplayName("should apply general masking for unknown sensitive fields")
        void shouldApplyGeneralMasking() {
            String result = LogMasker.maskFieldValue("notes", "Normal text without PII");
            assertThat(result).isEqualTo("Normal text without PII");
        }
    }

    @Nested
    @DisplayName("maskPassword")
    class MaskPassword {

        @Test
        @DisplayName("should return null for null")
        void shouldReturnNull() {
            assertThat(LogMasker.maskPassword(null)).isNull();
        }

        @Test
        @DisplayName("should mask various password patterns")
        void shouldMaskPasswordPatterns() {
            assertThat(LogMasker.maskPassword("pwd=secret")).contains("********");
            assertThat(LogMasker.maskPassword("pass: mypass")).contains("********");
        }
    }

    @Nested
    @DisplayName("Individual maskers null handling")
    class NullHandling {

        @Test
        @DisplayName("should handle null for all individual maskers")
        void shouldHandleNull() {
            assertThat(LogMasker.maskRRN(null)).isNull();
            assertThat(LogMasker.maskCreditCard(null)).isNull();
            assertThat(LogMasker.maskPhoneNumber(null)).isNull();
            assertThat(LogMasker.maskEmail(null)).isNull();
        }
    }
}
