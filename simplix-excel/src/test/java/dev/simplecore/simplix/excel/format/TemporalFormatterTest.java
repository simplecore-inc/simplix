package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemporalFormatter")
class TemporalFormatterTest {

    @Nested
    @DisplayName("LocalDate formatter")
    class LocalDateFormatterTests {

        private final TemporalFormatter<LocalDate> formatter =
                new TemporalFormatter<>(LocalDate.class, "yyyy-MM-dd");

        @Test
        @DisplayName("should format LocalDate")
        void shouldFormat() {
            String result = formatter.format(LocalDate.of(2024, 1, 15), null);
            assertThat(result).isEqualTo("2024-01-15");
        }

        @Test
        @DisplayName("should format with custom pattern")
        void shouldFormatWithCustomPattern() {
            String result = formatter.format(LocalDate.of(2024, 1, 15), "dd/MM/yyyy");
            assertThat(result).isEqualTo("15/01/2024");
        }

        @Test
        @DisplayName("should return empty for null value")
        void shouldReturnEmptyForNull() {
            assertThat(formatter.format(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should parse ISO date string")
        void shouldParse() {
            LocalDate result = formatter.parse("2024-01-15", null);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse slash date format using common patterns")
        void shouldParseSlashFormat() {
            LocalDate result = formatter.parse("2024/01/15", null);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(formatter.parse(null, null)).isNull();
        }

        @Test
        @DisplayName("should return null for unparseable value")
        void shouldReturnNullForUnparseable() {
            assertThat(formatter.parse("not-a-date", null)).isNull();
        }

        @Test
        @DisplayName("should return default pattern")
        void shouldReturnDefaultPattern() {
            assertThat(formatter.getDefaultPattern()).isEqualTo("yyyy-MM-dd");
        }
    }

    @Nested
    @DisplayName("LocalDateTime formatter")
    class LocalDateTimeFormatterTests {

        private final TemporalFormatter<LocalDateTime> formatter =
                new TemporalFormatter<>(LocalDateTime.class, "yyyy-MM-dd HH:mm:ss");

        @Test
        @DisplayName("should format LocalDateTime")
        void shouldFormat() {
            String result = formatter.format(
                    LocalDateTime.of(2024, 1, 15, 10, 30, 0), null);
            assertThat(result).isEqualTo("2024-01-15 10:30:00");
        }

        @Test
        @DisplayName("should parse datetime string")
        void shouldParse() {
            LocalDateTime result = formatter.parse("2024-01-15 10:30:00", null);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }
    }

    @Nested
    @DisplayName("LocalTime formatter")
    class LocalTimeFormatterTests {

        private final TemporalFormatter<LocalTime> formatter =
                new TemporalFormatter<>(LocalTime.class, "HH:mm:ss");

        @Test
        @DisplayName("should format LocalTime")
        void shouldFormat() {
            String result = formatter.format(LocalTime.of(10, 30, 45), null);
            assertThat(result).isEqualTo("10:30:45");
        }

        @Test
        @DisplayName("should parse time string")
        void shouldParse() {
            LocalTime result = formatter.parse("10:30:45", null);
            assertThat(result).isEqualTo(LocalTime.of(10, 30, 45));
        }
    }

    @Nested
    @DisplayName("Instant formatter")
    class InstantFormatterTests {

        private final TemporalFormatter<Instant> formatter =
                new TemporalFormatter<>(Instant.class, "yyyy-MM-dd'T'HH:mm:ss'Z'");

        @Test
        @DisplayName("should format Instant")
        void shouldFormat() {
            Instant instant = Instant.ofEpochMilli(0);
            String result = formatter.format(instant, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should parse epoch millis for Instant")
        void shouldParseEpochMillis() {
            Instant result = formatter.parse("1705276800000", null);
            assertThat(result).isNotNull();
            assertThat(result.toEpochMilli()).isEqualTo(1705276800000L);
        }
    }
}
