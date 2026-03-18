package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultFormatter")
class DefaultFormatterTest {

    private DefaultFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DefaultFormatter();
    }

    @Nested
    @DisplayName("format")
    class FormatTests {

        @Test
        @DisplayName("should return toString of value")
        void shouldReturnToString() {
            assertThat(formatter.format(42, null)).isEqualTo("42");
        }

        @Test
        @DisplayName("should return empty for null value")
        void shouldReturnEmptyForNull() {
            assertThat(formatter.format(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format string value")
        void shouldFormatString() {
            assertThat(formatter.format("hello", null)).isEqualTo("hello");
        }

        @Test
        @DisplayName("should format boolean value")
        void shouldFormatBoolean() {
            assertThat(formatter.format(true, null)).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("parse")
    class ParseTests {

        @Test
        @DisplayName("should return input string")
        void shouldReturnInputString() {
            assertThat(formatter.parse("hello", null)).isEqualTo("hello");
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
    }

    @Nested
    @DisplayName("getDefaultPattern")
    class GetDefaultPatternTests {

        @Test
        @DisplayName("should return empty string")
        void shouldReturnEmpty() {
            assertThat(formatter.getDefaultPattern()).isEmpty();
        }
    }
}
