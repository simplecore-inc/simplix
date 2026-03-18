package dev.simplecore.simplix.core.entity.converter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingConverter - Extended Coverage")
class MaskingConverterExtendedTest {

    private MaskingConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MaskingConverter();
        ReflectionTestUtils.setField(converter, "maskingEnabled", true);
        ReflectionTestUtils.setField(converter, "maskingKey", "TestKeyForEncryption12345678901234567890");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("domain.masking.unmask");
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDb {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        }

        @Test
        @DisplayName("should encrypt and prefix the value")
        void shouldEncryptAndPrefix() {
            String result = converter.convertToDatabaseColumn("sensitive-data");
            assertThat(result).startsWith("MASKED:");
        }

        @Test
        @DisplayName("should pass through when masking is disabled")
        void shouldPassThroughWhenDisabled() {
            ReflectionTestUtils.setField(converter, "maskingEnabled", false);
            assertThat(converter.convertToDatabaseColumn("data")).isEqualTo("data");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntity {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("should return non-prefixed data as-is")
        void shouldReturnNonPrefixedAsIs() {
            assertThat(converter.convertToEntityAttribute("plain-text")).isEqualTo("plain-text");
        }

        @Test
        @DisplayName("should return masked representation by default")
        void shouldReturnMaskedByDefault() {
            String encrypted = converter.convertToDatabaseColumn("secret");
            String result = converter.convertToEntityAttribute(encrypted);
            assertThat(result).isEqualTo("****MASKED****");
        }

        @Test
        @DisplayName("should decrypt when unmask mode is enabled")
        void shouldDecryptWhenUnmaskEnabled() {
            System.setProperty("domain.masking.unmask", "true");

            String encrypted = converter.convertToDatabaseColumn("hello-world");
            String result = converter.convertToEntityAttribute(encrypted);
            assertThat(result).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("should pass through when masking is disabled")
        void shouldPassThroughWhenDisabled() {
            ReflectionTestUtils.setField(converter, "maskingEnabled", false);
            assertThat(converter.convertToEntityAttribute("MASKED:xyz")).isEqualTo("MASKED:xyz");
        }

        @Test
        @DisplayName("should return masked representation on decryption error")
        void shouldReturnMaskedOnError() {
            String result = converter.convertToEntityAttribute("MASKED:invalid-base64");
            assertThat(result).isEqualTo("****MASKED****");
        }
    }

    @Nested
    @DisplayName("Key initialization")
    class KeyInit {

        @Test
        @DisplayName("should use default key when maskingKey is null")
        void shouldUseDefaultKey() {
            MaskingConverter conv = new MaskingConverter();
            ReflectionTestUtils.setField(conv, "maskingEnabled", true);
            ReflectionTestUtils.setField(conv, "maskingKey", null);

            String result = conv.convertToDatabaseColumn("test");
            assertThat(result).startsWith("MASKED:");
        }

        @Test
        @DisplayName("should use default key when maskingKey is short")
        void shouldUseDefaultKeyForShortKey() {
            MaskingConverter conv = new MaskingConverter();
            ReflectionTestUtils.setField(conv, "maskingEnabled", true);
            ReflectionTestUtils.setField(conv, "maskingKey", "short");

            String result = conv.convertToDatabaseColumn("test");
            assertThat(result).startsWith("MASKED:");
        }
    }

    @Nested
    @DisplayName("Static helper methods")
    class StaticHelpers {

        @Test
        @DisplayName("should delegate maskEmail")
        void shouldDelegateMaskEmail() {
            assertThat(MaskingConverter.maskEmail("test@example.com")).contains("@example.com");
        }

        @Test
        @DisplayName("should delegate maskPhoneNumber")
        void shouldDelegateMaskPhoneNumber() {
            assertThat(MaskingConverter.maskPhoneNumber("01012345678")).contains("010");
        }

        @Test
        @DisplayName("should delegate maskIdNumber")
        void shouldDelegateMaskIdNumber() {
            assertThat(MaskingConverter.maskIdNumber("900101-1234567")).contains("900101");
        }

        @Test
        @DisplayName("should delegate maskCreditCard")
        void shouldDelegateMaskCreditCard() {
            assertThat(MaskingConverter.maskCreditCard("1234-5678-9012-3456")).contains("3456");
        }

        @Test
        @DisplayName("should delegate maskIpAddress")
        void shouldDelegateMaskIpAddress() {
            assertThat(MaskingConverter.maskIpAddress("192.168.1.100")).isEqualTo("192.168.1.0");
        }
    }
}
