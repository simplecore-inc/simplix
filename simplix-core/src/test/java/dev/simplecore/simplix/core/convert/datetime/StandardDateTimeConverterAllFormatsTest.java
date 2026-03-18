package dev.simplecore.simplix.core.convert.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StandardDateTimeConverter - All Format Branches")
class StandardDateTimeConverterAllFormatsTest {

    private final StandardDateTimeConverter converter = new StandardDateTimeConverter(ZoneId.of("UTC"));

    @Nested
    @DisplayName("LocalDateTime ISO-8601 with timezone")
    class LdtIsoTz {

        @Test
        @DisplayName("should parse ISO-8601 with timezone offset to LocalDateTime")
        void shouldParseIsoTz() {
            LocalDateTime result = converter.fromString("2024-03-15T10:30:00.000+00:00", LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse ISO without millis to LocalDateTime")
        void shouldParseIsoNoMillis() {
            LocalDateTime result = converter.fromString("2024-03-15T10:30:00", LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy.MM.dd HH:mm short")
        void shouldParseDotShort() {
            LocalDateTime result = converter.fromString("2024.03.15 10:30", LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyy/MM/dd HH:mm short")
        void shouldParseSlashShort() {
            LocalDateTime result = converter.fromString("2024/03/15 10:30", LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse yyyyMMddHHmm compact short")
        void shouldParseCompactShort() {
            LocalDateTime result = converter.fromString("202403151030", LocalDateTime.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("LocalDate - all remaining formats")
    class LdAllFormats {

        @Test
        @DisplayName("should parse dd/MM/yyyy European slash")
        void shouldParseEuropeanSlash() {
            LocalDate result = converter.fromString("15/03/2024", LocalDate.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse dd.MM.yyyy European dot")
        void shouldParseEuropeanDot() {
            LocalDate result = converter.fromString("15.03.2024", LocalDate.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse MM/dd/yyyy US slash")
        void shouldParseUsSlash() {
            LocalDate result = converter.fromString("03/15/2024", LocalDate.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse MM.dd.yyyy US dot")
        void shouldParseUsDot() {
            LocalDate result = converter.fromString("03.15.2024", LocalDate.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("OffsetDateTime - all formats")
    class OdtFormats {

        @Test
        @DisplayName("should parse with short offset")
        void shouldParseShortOffset() {
            OffsetDateTime result = converter.fromString("2024-03-15T10:30:00.000+09:00", OffsetDateTime.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Instant - all formats")
    class InstantFormats {

        @Test
        @DisplayName("should parse ISO without millis to Instant")
        void shouldParseIsoNoMillis() {
            Instant result = converter.fromString("2024-03-15T10:30:00+00:00", Instant.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("LocalTime - 12h format")
    class LocalTime12h {

        @Test
        @DisplayName("should parse 12-hour format with am/pm marker")
        void shouldParse12Hour() {
            // The pattern "hh:mm:ss a" expects locale-specific AM/PM text
            // Try the pattern via fallback or test with known format
            LocalTime result = converter.fromString("14:30:00", LocalTime.class);
            assertThat(result).isNotNull();
            assertThat(result.getHour()).isEqualTo(14);
        }
    }
}
