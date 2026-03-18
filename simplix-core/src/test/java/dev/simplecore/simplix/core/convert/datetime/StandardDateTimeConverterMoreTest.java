package dev.simplecore.simplix.core.convert.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StandardDateTimeConverter - More Formats")
class StandardDateTimeConverterMoreTest {

    private final StandardDateTimeConverter converter = new StandardDateTimeConverter(ZoneId.of("UTC"));

    @Nested
    @DisplayName("LocalDateTime - more formats")
    class LocalDateTimeFormats {

        @Test
        @DisplayName("should parse yyyy-MM-dd HH:mm:ss.SSS")
        void shouldParseCommonWithMillis() {
            assertThat(converter.fromString("2024-03-15 10:30:45.123", LocalDateTime.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy-MM-dd HH:mm:ss")
        void shouldParseCommon() {
            assertThat(converter.fromString("2024-03-15 10:30:45", LocalDateTime.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy-MM-dd HH:mm")
        void shouldParseShort() {
            assertThat(converter.fromString("2024-03-15 10:30", LocalDateTime.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy.MM.dd HH:mm:ss")
        void shouldParseDotFormat() {
            assertThat(converter.fromString("2024.03.15 10:30:45", LocalDateTime.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy/MM/dd HH:mm:ss")
        void shouldParseSlashFormat() {
            assertThat(converter.fromString("2024/03/15 10:30:45", LocalDateTime.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyyMMddHHmmss compact format")
        void shouldParseCompact() {
            assertThat(converter.fromString("20240315103045", LocalDateTime.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("LocalDate - more formats")
    class LocalDateFormats {

        @Test
        @DisplayName("should parse yyyy/MM/dd")
        void shouldParseSlash() {
            assertThat(converter.fromString("2024/03/15", LocalDate.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyyMMdd compact")
        void shouldParseCompact() {
            assertThat(converter.fromString("20240315", LocalDate.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy.MM.dd")
        void shouldParseDot() {
            assertThat(converter.fromString("2024.03.15", LocalDate.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse dd-MM-yyyy European")
        void shouldParseEuropean() {
            assertThat(converter.fromString("15-03-2024", LocalDate.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse MM-dd-yyyy US format")
        void shouldParseUs() {
            assertThat(converter.fromString("03-15-2024", LocalDate.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("LocalTime - more formats")
    class LocalTimeFormats {

        @Test
        @DisplayName("should parse HHmmss compact")
        void shouldParseCompact() {
            assertThat(converter.fromString("103045", LocalTime.class)).isNotNull();
        }

        @Test
        @DisplayName("should parse HHmm short compact")
        void shouldParseShortCompact() {
            assertThat(converter.fromString("1030", LocalTime.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Fallback conversion from OffsetDateTime")
    class OffsetFallback {

        @Test
        @DisplayName("should convert OffsetDateTime to LocalDate via fallback")
        void shouldConvertToLocalDate() {
            LocalDate result = converter.fromString("2024-06-15T12:00:00+05:30", LocalDate.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should convert OffsetDateTime to LocalTime via fallback")
        void shouldConvertToLocalTime() {
            LocalTime result = converter.fromString("2024-06-15T12:00:00+05:30", LocalTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should convert OffsetDateTime to Instant via fallback")
        void shouldConvertToInstant() {
            Instant result = converter.fromString("2024-06-15T12:00:00+05:30", Instant.class);
            assertThat(result).isNotNull();
        }
    }
}
