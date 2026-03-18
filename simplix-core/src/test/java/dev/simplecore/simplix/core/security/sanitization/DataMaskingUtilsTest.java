package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataMaskingUtils")
class DataMaskingUtilsTest {

    @Nested
    @DisplayName("maskEmail")
    class MaskEmail {

        @Test
        @DisplayName("should mask email showing first 2 chars and domain")
        void shouldMaskEmail() {
            assertThat(DataMaskingUtils.maskEmail("user@example.com")).isEqualTo("us***@example.com");
        }

        @Test
        @DisplayName("should mask short local part with **")
        void shouldMaskShortLocalPart() {
            assertThat(DataMaskingUtils.maskEmail("ab@test.com")).isEqualTo("**@test.com");
        }

        @Test
        @DisplayName("should mask single char local part with **")
        void shouldMaskSingleCharLocalPart() {
            assertThat(DataMaskingUtils.maskEmail("a@test.com")).isEqualTo("**@test.com");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskEmail(null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(DataMaskingUtils.maskEmail("")).isEmpty();
        }

        @Test
        @DisplayName("should return original for invalid email format")
        void shouldReturnOriginalForInvalid() {
            assertThat(DataMaskingUtils.maskEmail("notanemail")).isEqualTo("notanemail");
        }
    }

    @Nested
    @DisplayName("maskPhoneNumber")
    class MaskPhoneNumber {

        @Test
        @DisplayName("should mask Korean mobile number")
        void shouldMaskKoreanMobile() {
            assertThat(DataMaskingUtils.maskPhoneNumber("010-1234-5678")).isEqualTo("010-****-****");
        }

        @Test
        @DisplayName("should mask Seoul area code number")
        void shouldMaskSeoulNumber() {
            assertThat(DataMaskingUtils.maskPhoneNumber("02-1234-5678")).isEqualTo("02-****-****");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskPhoneNumber(null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(DataMaskingUtils.maskPhoneNumber("")).isEmpty();
        }

        @Test
        @DisplayName("should return original for too short number")
        void shouldReturnOriginalForShort() {
            assertThat(DataMaskingUtils.maskPhoneNumber("123")).isEqualTo("123");
        }
    }

    @Nested
    @DisplayName("maskCreditCard")
    class MaskCreditCard {

        @Test
        @DisplayName("should mask card with dashes showing last 4 digits")
        void shouldMaskCardWithDashes() {
            assertThat(DataMaskingUtils.maskCreditCard("1234-5678-9012-3456"))
                .isEqualTo("****-****-****-3456");
        }

        @Test
        @DisplayName("should mask card with spaces showing last 4 digits")
        void shouldMaskCardWithSpaces() {
            assertThat(DataMaskingUtils.maskCreditCard("1234 5678 9012 3456"))
                .isEqualTo("**** **** **** 3456");
        }

        @Test
        @DisplayName("should mask card without separators showing last 4 digits")
        void shouldMaskCardWithoutSeparators() {
            assertThat(DataMaskingUtils.maskCreditCard("1234567890123456"))
                .endsWith("3456")
                .contains("*");
        }

        @Test
        @DisplayName("should return default mask for too short number")
        void shouldReturnDefaultMaskForShort() {
            assertThat(DataMaskingUtils.maskCreditCard("12345")).isEqualTo("****");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskCreditCard(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskRRN")
    class MaskRRN {

        @Test
        @DisplayName("should mask RRN showing birth date only")
        void shouldMaskRRN() {
            assertThat(DataMaskingUtils.maskRRN("901231-1234567")).isEqualTo("901231-*******");
        }

        @Test
        @DisplayName("should return original for invalid format")
        void shouldReturnOriginalForInvalid() {
            assertThat(DataMaskingUtils.maskRRN("12345")).isEqualTo("12345");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskRRN(null)).isNull();
        }
    }

    @Nested
    @DisplayName("maskIpAddress")
    class MaskIpAddress {

        @Test
        @DisplayName("should mask IPv4 last octet")
        void shouldMaskIpv4() {
            assertThat(DataMaskingUtils.maskIpAddress("192.168.1.123")).isEqualTo("192.168.1.0");
        }
    }

    @Nested
    @DisplayName("maskPaymentToken")
    class MaskPaymentToken {

        @Test
        @DisplayName("should mask token with prefix format")
        void shouldMaskTokenWithPrefix() {
            String result = DataMaskingUtils.maskPaymentToken("pm_1234567890abcdef");

            assertThat(result).startsWith("pm_");
            assertThat(result).contains("****");
            assertThat(result).endsWith("cdef");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskPaymentToken(null)).isNull();
        }

        @Test
        @DisplayName("should return short token as-is")
        void shouldReturnShortTokenAsIs() {
            assertThat(DataMaskingUtils.maskPaymentToken("short")).isEqualTo("short");
        }
    }

    @Nested
    @DisplayName("maskGeneric")
    class MaskGeneric {

        @Test
        @DisplayName("should mask middle portion keeping first and last chars")
        void shouldMaskMiddle() {
            String result = DataMaskingUtils.maskGeneric("1234567890", 2, 2);

            assertThat(result).startsWith("12");
            assertThat(result).endsWith("90");
            assertThat(result).contains("*");
        }

        @Test
        @DisplayName("should return original if too short to mask")
        void shouldReturnOriginalIfTooShort() {
            assertThat(DataMaskingUtils.maskGeneric("ab", 2, 2)).isEqualTo("ab");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskGeneric(null, 2, 2)).isNull();
        }
    }

    @Nested
    @DisplayName("maskFull")
    class MaskFull {

        @Test
        @DisplayName("should fully mask value with asterisks")
        void shouldFullyMask() {
            String result = DataMaskingUtils.maskFull("secret", 50);

            assertThat(result).isEqualTo("******");
        }

        @Test
        @DisplayName("should respect maxLength")
        void shouldRespectMaxLength() {
            String result = DataMaskingUtils.maskFull("a".repeat(100), 10);

            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DataMaskingUtils.maskFull(null, 50)).isNull();
        }
    }

    @Nested
    @DisplayName("isMasked")
    class IsMasked {

        @Test
        @DisplayName("should return true for masked value")
        void shouldReturnTrueForMasked() {
            assertThat(DataMaskingUtils.isMasked("us***@example.com")).isTrue();
            assertThat(DataMaskingUtils.isMasked("****-****-****-3456")).isTrue();
        }

        @Test
        @DisplayName("should return false for unmasked value")
        void shouldReturnFalseForUnmasked() {
            assertThat(DataMaskingUtils.isMasked("user@example.com")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(DataMaskingUtils.isMasked(null)).isFalse();
            assertThat(DataMaskingUtils.isMasked("")).isFalse();
        }
    }
}
