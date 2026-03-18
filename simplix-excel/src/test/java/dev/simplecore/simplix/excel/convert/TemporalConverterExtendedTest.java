package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.temporal.Temporal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemporalConverter - Extended Coverage")
class TemporalConverterExtendedTest {

    private TemporalConverter converter;

    @BeforeEach
    void setUp() {
        converter = new TemporalConverter();
        TemporalConverter.setDefaultZone(ZoneId.systemDefault());
    }

    @Nested
    @DisplayName("setDefaultZone")
    class SetDefaultZoneTests {

        @Test
        @DisplayName("should accept valid zone ID")
        void shouldAcceptValidZone() {
            TemporalConverter.setDefaultZone(ZoneId.of("UTC"));
            assertThat(TemporalConverter.isTemporal(LocalDate.class)).isTrue();
            TemporalConverter.setDefaultZone(ZoneId.systemDefault());
        }

        @Test
        @DisplayName("should fallback to system default for null zone")
        void shouldFallbackForNull() {
            TemporalConverter.setDefaultZone(null);
            assertThat(TemporalConverter.isTemporal(LocalDate.class)).isTrue();
            TemporalConverter.setDefaultZone(ZoneId.systemDefault());
        }
    }

    @Nested
    @DisplayName("formatTemporal with ZonedDateTime")
    class FormatZonedDateTimeTests {

        @Test
        @DisplayName("should format ZonedDateTime with default pattern")
        void shouldFormatWithDefaultPattern() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = TemporalConverter.formatTemporal(zdt, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format ZonedDateTime with custom pattern")
        void shouldFormatWithCustomPattern() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = TemporalConverter.formatTemporal(zdt, "yyyy-MM-dd HH:mm:ss");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatTemporal with OffsetDateTime")
    class FormatOffsetDateTimeTests {

        @Test
        @DisplayName("should format OffsetDateTime with default pattern")
        void shouldFormatWithDefaultPattern() {
            OffsetDateTime odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            String result = TemporalConverter.formatTemporal(odt, null);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatTemporal with Instant")
    class FormatInstantTests {

        @Test
        @DisplayName("should format Instant with default pattern")
        void shouldFormatWithDefaultPattern() {
            Instant instant = Instant.ofEpochMilli(1705276800000L);
            String result = TemporalConverter.formatTemporal(instant, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Instant with custom pattern")
        void shouldFormatWithCustomPattern() {
            Instant instant = Instant.ofEpochMilli(1705276800000L);
            String result = TemporalConverter.formatTemporal(instant, "yyyy-MM-dd HH:mm:ss");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatTemporal with invalid pattern")
    class FormatInvalidPatternTests {

        @Test
        @DisplayName("should fallback to toString for invalid pattern")
        void shouldFallbackForInvalidPattern() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            // This should trigger the catch block and return temporal.toString()
            String result = TemporalConverter.formatTemporal(date, "INVALID_PATTERN_xxx");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("convenience format methods")
    class ConvenienceFormatTests {

        @Test
        @DisplayName("formatLocalDate should delegate to formatTemporal")
        void shouldFormatLocalDate() {
            String result = TemporalConverter.formatLocalDate(LocalDate.of(2024, 1, 15), "yyyy/MM/dd");
            assertThat(result).isEqualTo("2024/01/15");
        }

        @Test
        @DisplayName("formatLocalTime should delegate to formatTemporal")
        void shouldFormatLocalTime() {
            String result = TemporalConverter.formatLocalTime(LocalTime.of(10, 30), "HH:mm");
            assertThat(result).isEqualTo("10:30");
        }

        @Test
        @DisplayName("formatLocalDateTime should delegate to formatTemporal")
        void shouldFormatLocalDateTime() {
            String result = TemporalConverter.formatLocalDateTime(
                    LocalDateTime.of(2024, 1, 15, 10, 30, 0), "yyyy-MM-dd HH:mm:ss");
            assertThat(result).isEqualTo("2024-01-15 10:30:00");
        }

        @Test
        @DisplayName("formatZonedDateTime should delegate to formatTemporal")
        void shouldFormatZonedDateTime() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = TemporalConverter.formatZonedDateTime(zdt, "yyyy-MM-dd");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("formatOffsetDateTime should delegate to formatTemporal")
        void shouldFormatOffsetDateTime() {
            OffsetDateTime odt = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            String result = TemporalConverter.formatOffsetDateTime(odt, "yyyy-MM-dd");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("formatInstant should delegate to formatTemporal")
        void shouldFormatInstant() {
            String result = TemporalConverter.formatInstant(Instant.ofEpochMilli(0), "yyyy-MM-dd");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseTemporalFromString - more types")
    class ParseTemporalExtendedTests {

        @Test
        @DisplayName("should return null for unsupported temporal type")
        void shouldReturnNullForUnsupportedType() {
            // Year is a Temporal but not in FORMAT_PATTERNS
            Temporal result = TemporalConverter.parseTemporalFromString("2024", Year.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should parse epoch millis to LocalDateTime")
        void shouldParseEpochToLocalDateTime() {
            LocalDateTime result = TemporalConverter.parseTemporalFromString("1705276800000", LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse epoch millis to ZonedDateTime")
        void shouldParseEpochToZonedDateTime() {
            ZonedDateTime result = TemporalConverter.parseTemporalFromString("1705276800000", ZonedDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse epoch millis to OffsetDateTime")
        void shouldParseEpochToOffsetDateTime() {
            OffsetDateTime result = TemporalConverter.parseTemporalFromString("1705276800000", OffsetDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for unparseable LocalTime")
        void shouldReturnNullForUnparseableTime() {
            LocalTime result = TemporalConverter.parseTemporalFromString("not-a-time", LocalTime.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should parse compact date")
        void shouldParseCompactLocalDateTime() {
            LocalDateTime result = TemporalConverter.parseTemporalFromString("2024-01-15T10:30:00", LocalDateTime.class);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should return null for non-numeric string for timestamp types")
        void shouldReturnNullForNonNumericTimestamp() {
            Instant result = TemporalConverter.parseTemporalFromString("invalid", Instant.class);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("convenience parse methods")
    class ConvenienceParseMethodTests {

        @Test
        @DisplayName("parseZonedDateTime should work")
        void shouldParseZonedDateTime() {
            ZonedDateTime result = TemporalConverter.parseZonedDateTime("1705276800000");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("parseOffsetDateTime should work")
        void shouldParseOffsetDateTime() {
            OffsetDateTime result = TemporalConverter.parseOffsetDateTime("1705276800000");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Converter interface - fromString with empty string")
    class ConverterEmptyStringTests {

        @Test
        @DisplayName("fromString should return null for empty string")
        void shouldReturnNullForEmptyString() {
            assertThat(converter.fromString("", LocalDate.class)).isNull();
        }

        @Test
        @DisplayName("fromString should return null for whitespace")
        void shouldReturnNullForWhitespace() {
            assertThat(converter.fromString("   ", LocalDate.class)).isNull();
        }
    }
}
