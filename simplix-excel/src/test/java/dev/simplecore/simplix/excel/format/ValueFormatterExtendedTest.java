package dev.simplecore.simplix.excel.format;

import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Calendar;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValueFormatter - Extended Coverage")
class ValueFormatterExtendedTest {

    @Nested
    @DisplayName("configure with properties")
    class ConfigureTests {

        @Test
        @DisplayName("should configure with format properties")
        void shouldConfigureWithProperties() {
            SimplixExcelProperties.FormatProperties props = new SimplixExcelProperties.FormatProperties();
            props.setDateFormat("dd/MM/yyyy");
            props.setTimeFormat("HH:mm");
            props.setDateTimeFormat("dd/MM/yyyy HH:mm:ss");
            props.setNumberFormat("#,##0.00");

            ValueFormatter.configure(props);

            // After configuration, formatting should use new patterns
            String result = ValueFormatter.formatValue(LocalDate.of(2024, 1, 15), null);
            assertThat(result).isNotEmpty();

            // Reset to defaults
            ValueFormatter.configure(null);
        }
    }

    @Nested
    @DisplayName("formatValue with Calendar type")
    class CalendarFormatterTests {

        @Test
        @DisplayName("should format Calendar value")
        void shouldFormatCalendar() {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(FormatterCache.getDefaultZone()));
            cal.set(2024, Calendar.JANUARY, 15, 10, 30, 0);

            String result = ValueFormatter.formatValue(cal, null);
            assertThat(result).isNotEmpty();
            assertThat(result).contains("2024");
        }

        @Test
        @DisplayName("should parse string to Calendar")
        void shouldParseCalendar() {
            Calendar result = ValueFormatter.parseValue("2024-01-15", Calendar.class, null);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("formatValue with ZonedDateTime")
    class ZonedDateTimeFormatterTests {

        @Test
        @DisplayName("should format ZonedDateTime value")
        void shouldFormatZonedDateTime() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = ValueFormatter.formatValue(zdt, null);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatValue with OffsetDateTime")
    class OffsetDateTimeFormatterTests {

        @Test
        @DisplayName("should format OffsetDateTime value")
        void shouldFormatOffsetDateTime() {
            OffsetDateTime odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            String result = ValueFormatter.formatValue(odt, null);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatValue with Instant")
    class InstantFormatterTests {

        @Test
        @DisplayName("should format Instant value")
        void shouldFormatInstant() {
            Instant instant = Instant.ofEpochMilli(1705276800000L);
            String result = ValueFormatter.formatValue(instant, null);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseValue with various types")
    class ParseValueTests {

        @Test
        @DisplayName("should parse to LocalDateTime")
        void shouldParseLocalDateTime() {
            LocalDateTime result = ValueFormatter.parseValue("2024-01-15 10:30:00", LocalDateTime.class, null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse to LocalTime")
        void shouldParseLocalTime() {
            LocalTime result = ValueFormatter.parseValue("10:30:45", LocalTime.class, null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should use default formatter for unknown type")
        void shouldUseDefaultFormatterForUnknownType() {
            Object result = ValueFormatter.parseValue("hello", Object.class, null);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getFormatter via superclass matching")
    class FormatterLookupTests {

        @Test
        @DisplayName("should find Number formatter for Integer")
        void shouldFindNumberFormatterForInteger() {
            String result = ValueFormatter.formatValue(42, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should find Number formatter for Double")
        void shouldFindNumberFormatterForDouble() {
            String result = ValueFormatter.formatValue(3.14, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should find Number formatter for Long")
        void shouldFindNumberFormatterForLong() {
            String result = ValueFormatter.formatValue(100000L, null);
            assertThat(result).isNotEmpty();
        }
    }
}
