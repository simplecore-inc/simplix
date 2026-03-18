package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateFormatter")
class DateFormatterTest {

    private DateFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DateFormatter();
    }

    @Nested
    @DisplayName("format")
    class FormatTests {

        @Test
        @DisplayName("should format date with default pattern")
        void shouldFormatWithDefaultPattern() {
            Date date = new Date(0);
            String result = formatter.format(date, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format date with custom pattern")
        void shouldFormatWithCustomPattern() {
            Date date = new Date(0);
            String result = formatter.format(date, "yyyy/MM/dd");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should return empty for null date")
        void shouldReturnEmptyForNull() {
            assertThat(formatter.format(null, "yyyy-MM-dd")).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse")
    class ParseTests {

        @Test
        @DisplayName("should parse date with default pattern")
        void shouldParseWithDefaultPattern() {
            Date result = formatter.parse("2024-01-15", null);
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
        @DisplayName("should return null for unparseable value")
        void shouldReturnNullForUnparseable() {
            assertThat(formatter.parse("not-a-date", "yyyy-MM-dd")).isNull();
        }
    }

    @Nested
    @DisplayName("getDefaultPattern")
    class GetDefaultPatternTests {

        @Test
        @DisplayName("should return yyyy-MM-dd")
        void shouldReturnDefaultPattern() {
            assertThat(formatter.getDefaultPattern()).isEqualTo("yyyy-MM-dd");
        }
    }
}
