package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemporalConverter")
class TemporalConverterTest {

    private TemporalConverter converter;

    @BeforeEach
    void setUp() {
        converter = new TemporalConverter();
    }

    @Nested
    @DisplayName("isTemporal")
    class IsTemporalTests {

        @Test
        @DisplayName("should return true for LocalDate")
        void shouldReturnTrueForLocalDate() {
            assertThat(TemporalConverter.isTemporal(LocalDate.class)).isTrue();
        }

        @Test
        @DisplayName("should return true for LocalDateTime")
        void shouldReturnTrueForLocalDateTime() {
            assertThat(TemporalConverter.isTemporal(LocalDateTime.class)).isTrue();
        }

        @Test
        @DisplayName("should return true for LocalTime")
        void shouldReturnTrueForLocalTime() {
            assertThat(TemporalConverter.isTemporal(LocalTime.class)).isTrue();
        }

        @Test
        @DisplayName("should return true for ZonedDateTime")
        void shouldReturnTrueForZonedDateTime() {
            assertThat(TemporalConverter.isTemporal(ZonedDateTime.class)).isTrue();
        }

        @Test
        @DisplayName("should return true for OffsetDateTime")
        void shouldReturnTrueForOffsetDateTime() {
            assertThat(TemporalConverter.isTemporal(OffsetDateTime.class)).isTrue();
        }

        @Test
        @DisplayName("should return true for Instant")
        void shouldReturnTrueForInstant() {
            assertThat(TemporalConverter.isTemporal(Instant.class)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-temporal types")
        void shouldReturnFalseForNonTemporal() {
            assertThat(TemporalConverter.isTemporal(String.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("formatTemporal")
    class FormatTemporalTests {

        @Test
        @DisplayName("should return empty for null temporal")
        void shouldReturnEmptyForNull() {
            assertThat(TemporalConverter.formatTemporal(null, "yyyy-MM-dd")).isEmpty();
        }

        @Test
        @DisplayName("should format LocalDate with default pattern")
        void shouldFormatLocalDate() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            String result = TemporalConverter.formatTemporal(date, null);
            assertThat(result).isEqualTo("2024-01-15");
        }

        @Test
        @DisplayName("should format LocalDateTime with default pattern")
        void shouldFormatLocalDateTime() {
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
            String result = TemporalConverter.formatTemporal(dateTime, null);
            assertThat(result).isEqualTo("2024-01-15 10:30:00");
        }

        @Test
        @DisplayName("should format LocalTime with default pattern")
        void shouldFormatLocalTime() {
            LocalTime time = LocalTime.of(10, 30, 45);
            String result = TemporalConverter.formatTemporal(time, null);
            assertThat(result).isEqualTo("10:30:45");
        }

        @Test
        @DisplayName("should format LocalDate with custom pattern")
        void shouldFormatWithCustomPattern() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            String result = TemporalConverter.formatTemporal(date, "dd/MM/yyyy");
            assertThat(result).isEqualTo("15/01/2024");
        }
    }

    @Nested
    @DisplayName("parseTemporalFromString")
    class ParseTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(TemporalConverter.parseTemporalFromString(null, LocalDate.class)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(TemporalConverter.parseTemporalFromString("", LocalDate.class)).isNull();
        }

        @Test
        @DisplayName("should parse ISO date string to LocalDate")
        void shouldParseLocalDate() {
            LocalDate result = TemporalConverter.parseTemporalFromString("2024-01-15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse slash date string to LocalDate")
        void shouldParseSlashDate() {
            LocalDate result = TemporalConverter.parseTemporalFromString("2024/01/15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse compact date string to LocalDate")
        void shouldParseCompactDate() {
            LocalDate result = TemporalConverter.parseTemporalFromString("20240115", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse datetime string to LocalDateTime")
        void shouldParseLocalDateTime() {
            LocalDateTime result = TemporalConverter.parseTemporalFromString(
                    "2024-01-15 10:30:00", LocalDateTime.class);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should parse time string to LocalTime")
        void shouldParseLocalTime() {
            LocalTime result = TemporalConverter.parseTemporalFromString("10:30:45", LocalTime.class);
            assertThat(result).isEqualTo(LocalTime.of(10, 30, 45));
        }

        @Test
        @DisplayName("should parse epoch millis to Instant")
        void shouldParseEpochMillisToInstant() {
            Instant result = TemporalConverter.parseTemporalFromString("1705276800000", Instant.class);
            assertThat(result).isNotNull();
            assertThat(result.toEpochMilli()).isEqualTo(1705276800000L);
        }

        @Test
        @DisplayName("should return null for unparseable value")
        void shouldReturnNullForUnparseable() {
            LocalDate result = TemporalConverter.parseTemporalFromString("not-a-date", LocalDate.class);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Converter interface methods")
    class ConverterInterfaceTests {

        @Test
        @DisplayName("fromString should parse temporal types")
        void shouldParseTemporalFromString() {
            Object result = converter.fromString("2024-01-15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("fromString should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.fromString(null, LocalDate.class)).isNull();
        }

        @Test
        @DisplayName("fromString should return null for non-temporal type")
        void shouldReturnNullForNonTemporal() {
            assertThat(converter.fromString("hello", String.class)).isNull();
        }

        @Test
        @DisplayName("toString should format temporal value")
        void shouldFormatTemporal() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            String result = converter.toString(date, "yyyy-MM-dd");
            assertThat(result).isEqualTo("2024-01-15");
        }

        @Test
        @DisplayName("toString should return empty for null value")
        void shouldReturnEmptyForNull() {
            assertThat(converter.toString(null, null)).isEmpty();
        }

        @Test
        @DisplayName("toString should use Object.toString for non-temporal")
        void shouldUseToStringForNonTemporal() {
            String result = converter.toString("plainString", null);
            assertThat(result).isEqualTo("plainString");
        }
    }

    @Nested
    @DisplayName("Convenience parse methods")
    class ConvenienceParseTests {

        @Test
        @DisplayName("parseLocalDate should parse valid string")
        void shouldParseLocalDate() {
            assertThat(TemporalConverter.parseLocalDate("2024-01-15"))
                    .isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("parseLocalTime should parse valid string")
        void shouldParseLocalTime() {
            assertThat(TemporalConverter.parseLocalTime("10:30:45"))
                    .isEqualTo(LocalTime.of(10, 30, 45));
        }

        @Test
        @DisplayName("parseLocalDateTime should parse valid string")
        void shouldParseLocalDateTime() {
            assertThat(TemporalConverter.parseLocalDateTime("2024-01-15 10:30:00"))
                    .isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("parseInstant should parse epoch millis")
        void shouldParseInstant() {
            Instant result = TemporalConverter.parseInstant("1705276800000");
            assertThat(result).isNotNull();
        }
    }
}
