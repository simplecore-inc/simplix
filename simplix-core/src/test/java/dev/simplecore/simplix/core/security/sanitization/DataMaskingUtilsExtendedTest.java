package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataMaskingUtils - Extended Coverage")
class DataMaskingUtilsExtendedTest {

    @Nested
    @DisplayName("maskPhoneNumber")
    class MaskPhoneNumber {

        @Test
        @DisplayName("should mask Korean 02 area code numbers")
        void shouldMask02Numbers() {
            String result = DataMaskingUtils.maskPhoneNumber("0212345678");
            assertThat(result).startsWith("02-");
        }

        @Test
        @DisplayName("should mask international format with space")
        void shouldMaskInternational() {
            String result = DataMaskingUtils.maskPhoneNumber("+82 10-1234-5678");
            assertThat(result).startsWith("+82");
        }

        @Test
        @DisplayName("should return short numbers as-is")
        void shouldReturnShortAsIs() {
            assertThat(DataMaskingUtils.maskPhoneNumber("12345")).isEqualTo("12345");
        }
    }

    @Nested
    @DisplayName("maskCreditCard")
    class MaskCreditCard {

        @Test
        @DisplayName("should mask with space separator")
        void shouldMaskWithSpaces() {
            String result = DataMaskingUtils.maskCreditCard("1234 5678 9012 3456");
            assertThat(result).isEqualTo("**** **** **** 3456");
        }

        @Test
        @DisplayName("should mask without separator")
        void shouldMaskWithoutSeparator() {
            String result = DataMaskingUtils.maskCreditCard("1234567890123456");
            assertThat(result).endsWith("3456");
            assertThat(result).startsWith("*");
        }

        @Test
        @DisplayName("should return default mask for short numbers")
        void shouldReturnDefaultForShort() {
            assertThat(DataMaskingUtils.maskCreditCard("12345")).isEqualTo("****");
        }
    }

    @Nested
    @DisplayName("maskRRN")
    class MaskRRN {

        @Test
        @DisplayName("should return invalid format as-is")
        void shouldReturnInvalidAsIs() {
            assertThat(DataMaskingUtils.maskRRN("12345")).isEqualTo("12345");
        }
    }

    @Nested
    @DisplayName("maskPaymentToken")
    class MaskPaymentToken {

        @Test
        @DisplayName("should mask Stripe-style tokens")
        void shouldMaskStripeTokens() {
            String result = DataMaskingUtils.maskPaymentToken("pm_1234567890abcdef");
            assertThat(result).startsWith("pm_");
            assertThat(result).endsWith("cdef");
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should mask long tokens without standard separator")
        void shouldMaskLongTokens() {
            String result = DataMaskingUtils.maskPaymentToken("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should return short tokens as-is")
        void shouldReturnShortAsIs() {
            assertThat(DataMaskingUtils.maskPaymentToken("short")).isEqualTo("short");
        }

        @Test
        @DisplayName("should return null/empty as-is")
        void shouldReturnNullEmpty() {
            assertThat(DataMaskingUtils.maskPaymentToken(null)).isNull();
            assertThat(DataMaskingUtils.maskPaymentToken("")).isEmpty();
        }

        @Test
        @DisplayName("should handle token with short part after separator")
        void shouldHandleShortPart() {
            String result = DataMaskingUtils.maskPaymentToken("pm_12345678");
            // tokenPart length is 8, which is not > 8, so falls through
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("maskGeneric")
    class MaskGeneric {

        @Test
        @DisplayName("should keep first and last characters")
        void shouldKeepFirstAndLast() {
            String result = DataMaskingUtils.maskGeneric("ABCDEFGHIJ", 3, 3);
            assertThat(result).startsWith("ABC").endsWith("HIJ");
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("should return short values as-is")
        void shouldReturnShortAsIs() {
            assertThat(DataMaskingUtils.maskGeneric("AB", 3, 3)).isEqualTo("AB");
        }

        @Test
        @DisplayName("should return null/empty as-is")
        void shouldReturnNullEmptyAsIs() {
            assertThat(DataMaskingUtils.maskGeneric(null, 3, 3)).isNull();
            assertThat(DataMaskingUtils.maskGeneric("", 3, 3)).isEmpty();
        }
    }

    @Nested
    @DisplayName("maskFull")
    class MaskFull {

        @Test
        @DisplayName("should mask entire value")
        void shouldMaskEntire() {
            String result = DataMaskingUtils.maskFull("secret", 50);
            assertThat(result).isEqualTo("******");
        }

        @Test
        @DisplayName("should limit mask length")
        void shouldLimitLength() {
            String longValue = "A".repeat(100);
            String result = DataMaskingUtils.maskFull(longValue, 10);
            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("should return null/empty as-is")
        void shouldReturnNullEmpty() {
            assertThat(DataMaskingUtils.maskFull(null, 50)).isNull();
            assertThat(DataMaskingUtils.maskFull("", 50)).isEmpty();
        }
    }

    @Nested
    @DisplayName("isMasked")
    class IsMasked {

        @Test
        @DisplayName("should return false for null/empty")
        void shouldReturnFalseForNullEmpty() {
            assertThat(DataMaskingUtils.isMasked(null)).isFalse();
            assertThat(DataMaskingUtils.isMasked("")).isFalse();
        }

        @Test
        @DisplayName("should detect masked values")
        void shouldDetectMasked() {
            assertThat(DataMaskingUtils.isMasked("****MASKED****")).isTrue();
            assertThat(DataMaskingUtils.isMasked("us***@example.com")).isTrue();
        }

        @Test
        @DisplayName("should return false for unmasked values")
        void shouldReturnFalseForUnmasked() {
            assertThat(DataMaskingUtils.isMasked("normal text")).isFalse();
        }
    }

    @Nested
    @DisplayName("maskEmail edge cases")
    class MaskEmailEdge {

        @Test
        @DisplayName("should handle short local part")
        void shouldHandleShortLocalPart() {
            assertThat(DataMaskingUtils.maskEmail("ab@test.com")).isEqualTo("**@test.com");
        }

        @Test
        @DisplayName("should handle no @ sign")
        void shouldHandleNoAtSign() {
            assertThat(DataMaskingUtils.maskEmail("notanemail")).isEqualTo("notanemail");
        }

        @Test
        @DisplayName("should handle @ at start")
        void shouldHandleAtAtStart() {
            assertThat(DataMaskingUtils.maskEmail("@domain.com")).isEqualTo("@domain.com");
        }
    }
}
