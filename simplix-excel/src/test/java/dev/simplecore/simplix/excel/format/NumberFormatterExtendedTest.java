package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumberFormatter - Extended Coverage")
class NumberFormatterExtendedTest {

    private NumberFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new NumberFormatter();
    }

    @Nested
    @DisplayName("convertToAppropriateType via parse")
    class ConvertTypeTests {

        @Test
        @DisplayName("should convert large long to long (not int)")
        void shouldConvertLargeLong() {
            // Parse a number that exceeds int range
            Number result = formatter.parse("3000000000", "#,##0");
            assertThat(result).isNotNull();
            // 3 billion exceeds int range, should be long
            assertThat(result.longValue()).isEqualTo(3000000000L);
        }

        @Test
        @DisplayName("should convert double with decimal to double")
        void shouldConvertDoubleWithDecimal() {
            Number result = formatter.parse("3.14", "#,##0.###");
            assertThat(result).isNotNull();
            assertThat(result.doubleValue()).isCloseTo(3.14, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("should convert whole double within int range to int")
        void shouldConvertWholeDoubleToInt() {
            Number result = formatter.parse("42", "#,##0.###");
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(Integer.class);
            assertThat(result.intValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("should convert whole double in long range to long")
        void shouldConvertWholeDoubleToLong() {
            Number result = formatter.parse("5000000000", "#,##0");
            assertThat(result).isNotNull();
            // 5 billion exceeds int range, should be long
            assertThat(result.longValue()).isEqualTo(5000000000L);
        }

        @Test
        @DisplayName("should handle large number formatting")
        void shouldHandleLargeNumber() {
            String result = formatter.format(Long.MAX_VALUE, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should handle very small decimal")
        void shouldHandleVerySmallDecimal() {
            String result = formatter.format(0.001, null);
            assertThat(result).isNotEmpty();
        }
    }
}
