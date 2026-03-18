package dev.simplecore.simplix.core.entity.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingConverter")
class MaskingConverterTest {

    private MaskingConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MaskingConverter();
        ReflectionTestUtils.setField(converter, "maskingEnabled", true);
        ReflectionTestUtils.setField(converter, "maskingKey", "TestMaskingKeyForUnitTest123456");
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("should encrypt value and prefix with MASKED:")
        void shouldEncryptValue() {
            String result = converter.convertToDatabaseColumn("sensitive-data");

            assertThat(result).startsWith("MASKED:");
            assertThat(result).isNotEqualTo("sensitive-data");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            String result = converter.convertToDatabaseColumn(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmpty() {
            String result = converter.convertToDatabaseColumn("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return plaintext when masking is disabled")
        void shouldReturnPlaintextWhenDisabled() {
            ReflectionTestUtils.setField(converter, "maskingEnabled", false);

            String result = converter.convertToDatabaseColumn("sensitive-data");

            assertThat(result).isEqualTo("sensitive-data");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("should return masked representation when unmask mode is off")
        void shouldReturnMaskedRepresentation() {
            String encrypted = converter.convertToDatabaseColumn("original-value");

            String result = converter.convertToEntityAttribute(encrypted);

            assertThat(result).isEqualTo("****MASKED****");
        }

        @Test
        @DisplayName("should return original value when unmask mode is on")
        void shouldDecryptWhenUnmaskEnabled() {
            String encrypted = converter.convertToDatabaseColumn("original-value");

            String previousValue = System.getProperty("domain.masking.unmask", "false");
            try {
                System.setProperty("domain.masking.unmask", "true");
                String result = converter.convertToEntityAttribute(encrypted);
                assertThat(result).isEqualTo("original-value");
            } finally {
                System.setProperty("domain.masking.unmask", previousValue);
            }
        }

        @Test
        @DisplayName("should return null for null dbData")
        void shouldReturnNullForNull() {
            String result = converter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return value as-is when not prefixed with MASKED:")
        void shouldReturnAsIsWhenNotPrefixed() {
            String result = converter.convertToEntityAttribute("plain-text");

            assertThat(result).isEqualTo("plain-text");
        }

        @Test
        @DisplayName("should return value as-is when masking is disabled")
        void shouldReturnAsIsWhenDisabled() {
            ReflectionTestUtils.setField(converter, "maskingEnabled", false);

            String result = converter.convertToEntityAttribute("MASKED:somedata");

            assertThat(result).isEqualTo("MASKED:somedata");
        }
    }

    @Nested
    @DisplayName("roundtrip with unmask")
    class Roundtrip {

        @Test
        @DisplayName("should encrypt and decrypt correctly")
        void shouldEncryptAndDecryptCorrectly() {
            String original = "Hello, World! 123";
            String encrypted = converter.convertToDatabaseColumn(original);

            String previousValue = System.getProperty("domain.masking.unmask", "false");
            try {
                System.setProperty("domain.masking.unmask", "true");
                String decrypted = converter.convertToEntityAttribute(encrypted);
                assertThat(decrypted).isEqualTo(original);
            } finally {
                System.setProperty("domain.masking.unmask", previousValue);
            }
        }
    }

    @Nested
    @DisplayName("static masking methods")
    class StaticMaskingMethods {

        @Test
        @DisplayName("maskEmail should delegate to DataMaskingUtils")
        void shouldMaskEmail() {
            String result = MaskingConverter.maskEmail("user@example.com");

            assertThat(result).isEqualTo("us***@example.com");
        }

        @Test
        @DisplayName("maskPhoneNumber should delegate to DataMaskingUtils")
        void shouldMaskPhoneNumber() {
            String result = MaskingConverter.maskPhoneNumber("010-1234-5678");

            assertThat(result).isEqualTo("010-****-****");
        }

        @Test
        @DisplayName("maskCreditCard should delegate to DataMaskingUtils")
        void shouldMaskCreditCard() {
            String result = MaskingConverter.maskCreditCard("1234-5678-9012-3456");

            assertThat(result).isEqualTo("****-****-****-3456");
        }

        @Test
        @DisplayName("maskIpAddress should delegate to DataMaskingUtils")
        void shouldMaskIpAddress() {
            String result = MaskingConverter.maskIpAddress("192.168.1.123");

            assertThat(result).isEqualTo("192.168.1.0");
        }
    }
}
