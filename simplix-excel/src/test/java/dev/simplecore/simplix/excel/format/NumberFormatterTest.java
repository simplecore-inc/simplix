package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumberFormatter")
class NumberFormatterTest {

    private NumberFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new NumberFormatter();
    }

    @Nested
    @DisplayName("format")
    class FormatTests {

        @Test
        @DisplayName("should format integer with default pattern")
        void shouldFormatInteger() {
            String result = formatter.format(1234, null);
            assertThat(result).isEqualTo("1,234");
        }

        @Test
        @DisplayName("should format double with default pattern")
        void shouldFormatDouble() {
            String result = formatter.format(1234.567, null);
            assertThat(result).isEqualTo("1,234.567");
        }

        @Test
        @DisplayName("should format with custom pattern")
        void shouldFormatWithCustomPattern() {
            String result = formatter.format(1234.5, "#,##0.00");
            assertThat(result).isEqualTo("1,234.50");
        }

        @Test
        @DisplayName("should return empty for null value")
        void shouldReturnEmptyForNull() {
            assertThat(formatter.format(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format zero correctly")
        void shouldFormatZero() {
            assertThat(formatter.format(0, null)).isEqualTo("0");
        }

        @Test
        @DisplayName("should format negative number")
        void shouldFormatNegative() {
            String result = formatter.format(-1234, null);
            assertThat(result).isEqualTo("-1,234");
        }
    }

    @Nested
    @DisplayName("parse")
    class ParseTests {

        @Test
        @DisplayName("should parse formatted number")
        void shouldParseFormattedNumber() {
            Number result = formatter.parse("1,234", null);
            assertThat(result).isNotNull();
            assertThat(result.intValue()).isEqualTo(1234);
        }

        @Test
        @DisplayName("should parse decimal number")
        void shouldParseDecimal() {
            Number result = formatter.parse("1,234.567", null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(formatter.parse(null, null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(formatter.parse("", null)).isNull();
        }

        @Test
        @DisplayName("should return null for unparseable input")
        void shouldReturnNullForUnparseable() {
            assertThat(formatter.parse("not-a-number", null)).isNull();
        }

        @Test
        @DisplayName("should convert long within integer range to int")
        void shouldConvertLongToInt() {
            Number result = formatter.parse("42", null);
            assertThat(result).isInstanceOf(Integer.class);
        }
    }

    @Nested
    @DisplayName("getDefaultPattern")
    class GetDefaultPatternTests {

        @Test
        @DisplayName("should return #,##0.###")
        void shouldReturnDefaultPattern() {
            assertThat(formatter.getDefaultPattern()).isEqualTo("#,##0.###");
        }
    }
}
