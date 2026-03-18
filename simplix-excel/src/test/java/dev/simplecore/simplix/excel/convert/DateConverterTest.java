package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateConverter")
class DateConverterTest {

    private DateConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DateConverter();
    }

    @Nested
    @DisplayName("formatDate")
    class FormatDateTests {

        @Test
        @DisplayName("should return empty string for null date")
        void shouldReturnEmptyForNull() {
            assertThat(DateConverter.formatDate(null, "yyyy-MM-dd")).isEmpty();
        }

        @Test
        @DisplayName("should use default pattern when pattern is null")
        void shouldUseDefaultPatternWhenNull() {
            Date date = new Date(0); // epoch
            String result = DateConverter.formatDate(date, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should use default pattern when pattern is empty")
        void shouldUseDefaultPatternWhenEmpty() {
            Date date = new Date(0);
            String result = DateConverter.formatDate(date, "");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatCalendar")
    class FormatCalendarTests {

        @Test
        @DisplayName("should return empty string for null calendar")
        void shouldReturnEmptyForNull() {
            assertThat(DateConverter.formatCalendar(null, "yyyy-MM-dd")).isEmpty();
        }

        @Test
        @DisplayName("should format calendar value")
        void shouldFormatCalendar() {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(0);
            String result = DateConverter.formatCalendar(cal, "yyyy-MM-dd");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseDate")
    class ParseDateTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DateConverter.parseDate(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(DateConverter.parseDate("")).isNull();
        }

        @Test
        @DisplayName("should return null for whitespace input")
        void shouldReturnNullForWhitespace() {
            assertThat(DateConverter.parseDate("   ")).isNull();
        }

        @Test
        @DisplayName("should parse ISO date format")
        void shouldParseIsoDate() {
            Date result = DateConverter.parseDate("2024-01-15");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse slash date format")
        void shouldParseSlashDate() {
            Date result = DateConverter.parseDate("2024/01/15");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse compact date format")
        void shouldParseCompactDate() {
            Date result = DateConverter.parseDate("20240115");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse epoch milliseconds")
        void shouldParseEpochMillis() {
            Date result = DateConverter.parseDate("1705276800000");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for unparseable value")
        void shouldReturnNullForUnparseable() {
            assertThat(DateConverter.parseDate("not-a-date")).isNull();
        }
    }

    @Nested
    @DisplayName("parseCalendar")
    class ParseCalendarTests {

        @Test
        @DisplayName("should parse valid date string to Calendar")
        void shouldParseToCalendar() {
            Calendar result = DateConverter.parseCalendar("2024-01-15");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(DateConverter.parseCalendar(null)).isNull();
        }

        @Test
        @DisplayName("should return null for unparseable value")
        void shouldReturnNullForUnparseable() {
            assertThat(DateConverter.parseCalendar("not-a-date")).isNull();
        }
    }

    @Nested
    @DisplayName("Converter interface methods")
    class ConverterInterfaceTests {

        @Test
        @DisplayName("fromString should return Date for Date target type")
        void shouldReturnDateFromString() {
            Object result = converter.fromString("2024-01-15", Date.class);
            assertThat(result).isInstanceOf(Date.class);
        }

        @Test
        @DisplayName("fromString should return Calendar for Calendar target type")
        void shouldReturnCalendarFromString() {
            Object result = converter.fromString("2024-01-15", Calendar.class);
            assertThat(result).isInstanceOf(Calendar.class);
        }

        @Test
        @DisplayName("fromString should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.fromString(null, Date.class)).isNull();
        }

        @Test
        @DisplayName("fromString should return null for unsupported type")
        void shouldReturnNullForUnsupportedType() {
            assertThat(converter.fromString("2024-01-15", String.class)).isNull();
        }

        @Test
        @DisplayName("toString should format Date")
        void shouldFormatDate() {
            Date date = new Date(0);
            String result = converter.toString(date, "yyyy-MM-dd");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("toString should return empty for null value")
        void shouldReturnEmptyForNullValue() {
            assertThat(converter.toString(null, null)).isEmpty();
        }

        @Test
        @DisplayName("toString should use Object.toString for unsupported type")
        void shouldUseToStringForUnsupported() {
            String result = converter.toString("not-a-date", null);
            assertThat(result).isEqualTo("not-a-date");
        }
    }
}
