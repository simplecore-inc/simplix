package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValueFormatter")
class ValueFormatterTest {

    @Nested
    @DisplayName("formatValue")
    class FormatValueTests {

        @Test
        @DisplayName("should return empty for null value")
        void shouldReturnEmptyForNull() {
            assertThat(ValueFormatter.formatValue(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format string value")
        void shouldFormatString() {
            assertThat(ValueFormatter.formatValue("hello", null)).isEqualTo("hello");
        }

        @Test
        @DisplayName("should format number value")
        void shouldFormatNumber() {
            String result = ValueFormatter.formatValue(1234, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Date value")
        void shouldFormatDate() {
            Date date = new Date(0);
            String result = ValueFormatter.formatValue(date, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format LocalDate value")
        void shouldFormatLocalDate() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            String result = ValueFormatter.formatValue(date, null);
            assertThat(result).contains("2024");
        }

        @Test
        @DisplayName("should format LocalDateTime value")
        void shouldFormatLocalDateTime() {
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
            String result = ValueFormatter.formatValue(dateTime, null);
            assertThat(result).contains("2024");
        }

        @Test
        @DisplayName("should format LocalTime value")
        void shouldFormatLocalTime() {
            LocalTime time = LocalTime.of(10, 30, 0);
            String result = ValueFormatter.formatValue(time, null);
            assertThat(result).contains("10");
        }

        @Test
        @DisplayName("should format boolean value using default formatter")
        void shouldFormatBoolean() {
            String result = ValueFormatter.formatValue(true, null);
            // Boolean uses DefaultFormatter (Object.class) which calls toString()
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseValue")
    class ParseValueTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(ValueFormatter.parseValue(null, String.class, null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(ValueFormatter.parseValue("", String.class, null)).isNull();
        }

        @Test
        @DisplayName("should parse string to Number")
        void shouldParseNumber() {
            Number result = ValueFormatter.parseValue("1234", Number.class, null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse string to Date")
        void shouldParseDate() {
            Date result = ValueFormatter.parseValue("2024-01-15", Date.class, null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse string to LocalDate")
        void shouldParseLocalDate() {
            LocalDate result = ValueFormatter.parseValue("2024-01-15", LocalDate.class, null);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }
    }

    @Nested
    @DisplayName("registerFormatter")
    class RegisterFormatterTests {

        @Test
        @DisplayName("should allow registering custom formatter")
        void shouldRegisterCustomFormatter() {
            ValueFormatter.registerFormatter(Boolean.class, new Formatter<Boolean>() {
                @Override
                public String format(Boolean value, String pattern) {
                    return value ? "YES" : "NO";
                }

                @Override
                public Boolean parse(String value, String pattern) {
                    return "YES".equalsIgnoreCase(value);
                }

                @Override
                public String getDefaultPattern() {
                    return "";
                }
            });

            String result = ValueFormatter.formatValue(true, null);
            assertThat(result).isEqualTo("YES");
        }
    }
}
