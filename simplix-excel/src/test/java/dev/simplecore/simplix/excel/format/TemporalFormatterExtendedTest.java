package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemporalFormatter - Extended Coverage")
class TemporalFormatterExtendedTest {

    @Nested
    @DisplayName("ZonedDateTime formatter")
    class ZonedDateTimeFormatterTests {

        private final TemporalFormatter<ZonedDateTime> formatter =
                new TemporalFormatter<>(ZonedDateTime.class, "yyyy-MM-dd'T'HH:mm:ssXXX");

        @Test
        @DisplayName("should format ZonedDateTime")
        void shouldFormat() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = formatter.format(zdt, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should parse ZonedDateTime string")
        void shouldParse() {
            // Try with common datetime pattern from fallback patterns
            ZonedDateTime result = formatter.parse("2024-01-15 10:30:00", null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for unparseable ZonedDateTime")
        void shouldReturnNullForUnparseable() {
            assertThat(formatter.parse("not-a-date", null)).isNull();
        }
    }

    @Nested
    @DisplayName("OffsetDateTime formatter")
    class OffsetDateTimeFormatterTests {

        private final TemporalFormatter<OffsetDateTime> formatter =
                new TemporalFormatter<>(OffsetDateTime.class, "yyyy-MM-dd'T'HH:mm:ssXXX");

        @Test
        @DisplayName("should format OffsetDateTime")
        void shouldFormat() {
            OffsetDateTime odt = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            String result = formatter.format(odt, null);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Instant formatter - timestamp parsing")
    class InstantTimestampTests {

        private final TemporalFormatter<Instant> formatter =
                new TemporalFormatter<>(Instant.class, "yyyy-MM-dd'T'HH:mm:ss'Z'");

        @Test
        @DisplayName("should parse epoch millis for Instant type")
        void shouldParseEpochMillis() {
            Instant result = formatter.parse("1705276800000", null);
            assertThat(result).isNotNull();
            assertThat(result.toEpochMilli()).isEqualTo(1705276800000L);
        }

        @Test
        @DisplayName("should return null for non-numeric non-parseable Instant")
        void shouldReturnNullForUnparseable() {
            assertThat(formatter.parse("invalid-instant", null)).isNull();
        }
    }

    @Nested
    @DisplayName("LocalDate formatter - compact formats")
    class LocalDateCompactTests {

        private final TemporalFormatter<LocalDate> formatter =
                new TemporalFormatter<>(LocalDate.class, "yyyy-MM-dd");

        @Test
        @DisplayName("should parse compact date format (yyyyMMdd)")
        void shouldParseCompactDate() {
            LocalDate result = formatter.parse("20240115", null);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse dot-separated date format")
        void shouldParseDotFormat() {
            LocalDate result = formatter.parse("2024.01.15", null);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }
    }

    @Nested
    @DisplayName("LocalTime formatter - various formats")
    class LocalTimeVariousTests {

        private final TemporalFormatter<LocalTime> formatter =
                new TemporalFormatter<>(LocalTime.class, "HH:mm:ss");

        @Test
        @DisplayName("should parse short time format (HH:mm)")
        void shouldParseShortTime() {
            LocalTime result = formatter.parse("10:30", null);
            assertThat(result).isEqualTo(LocalTime.of(10, 30));
        }

        @Test
        @DisplayName("should parse time with millis")
        void shouldParseTimeWithMillis() {
            LocalTime result = formatter.parse("10:30:45.123", null);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("LocalDateTime formatter - various formats")
    class LocalDateTimeVariousTests {

        private final TemporalFormatter<LocalDateTime> formatter =
                new TemporalFormatter<>(LocalDateTime.class, "yyyy-MM-dd HH:mm:ss");

        @Test
        @DisplayName("should parse standard datetime format")
        void shouldParseStandardDatetime() {
            LocalDateTime result = formatter.parse("2024-01-15 10:30:00", null);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should parse short datetime format (no seconds)")
        void shouldParseShortDateTime() {
            LocalDateTime result = formatter.parse("2024-01-15 10:30", null);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }
    }
}
