package dev.simplecore.simplix.core.convert.datetime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.temporal.Temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StandardDateTimeConverter - Extended Coverage")
class StandardDateTimeConverterExtendedTest {

    private StandardDateTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StandardDateTimeConverter(ZoneId.of("UTC"));
    }

    @Nested
    @DisplayName("fromString - ZonedDateTime")
    class FromStringZonedDateTime {

        @Test
        @DisplayName("should parse ISO-8601 with offset to ZonedDateTime")
        void shouldParseIsoWithOffset() {
            ZonedDateTime result = converter.fromString("2024-03-15T10:30:00.000+09:00", ZonedDateTime.class);
            assertThat(result).isNotNull();
            assertThat(result.getZone()).isEqualTo(ZoneId.of("UTC"));
        }

        @Test
        @DisplayName("should parse ISO-8601 with offset no millis to ZonedDateTime")
        void shouldParseIsoNoMillis() {
            ZonedDateTime result = converter.fromString("2024-03-15T10:30:00+09:00", ZonedDateTime.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("fromString - OffsetDateTime")
    class FromStringOffsetDateTime {

        @Test
        @DisplayName("should parse ISO-8601 with offset to OffsetDateTime")
        void shouldParseOffset() {
            OffsetDateTime result = converter.fromString("2024-03-15T10:30:00.000+09:00", OffsetDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse ISO-8601 without millis to OffsetDateTime")
        void shouldParseOffsetNoMillis() {
            OffsetDateTime result = converter.fromString("2024-03-15T10:30:00+09:00", OffsetDateTime.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("fromString - Instant")
    class FromStringInstant {

        @Test
        @DisplayName("should parse ISO-8601 with timezone to Instant")
        void shouldParseToInstant() {
            Instant result = converter.fromString("2024-03-15T10:30:00.000+00:00", Instant.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("fromString - LocalTime")
    class FromStringLocalTime {

        @Test
        @DisplayName("should parse full time format")
        void shouldParseFullTime() {
            LocalTime result = converter.fromString("10:30:45.123", LocalTime.class);
            assertThat(result).isNotNull();
            assertThat(result.getHour()).isEqualTo(10);
            assertThat(result.getMinute()).isEqualTo(30);
        }

        @Test
        @DisplayName("should parse short time format")
        void shouldParseShortTime() {
            LocalTime result = converter.fromString("14:30", LocalTime.class);
            assertThat(result).isNotNull();
            assertThat(result.getHour()).isEqualTo(14);
        }

        @Test
        @DisplayName("should parse HH:mm:ss format")
        void shouldParseHHmmss() {
            LocalTime result = converter.fromString("23:59:59", LocalTime.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("fromString - edge cases")
    class FromStringEdgeCases {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.fromString(null, LocalDateTime.class)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(converter.fromString("", LocalDateTime.class)).isNull();
            assertThat(converter.fromString("   ", LocalDateTime.class)).isNull();
        }

        @Test
        @DisplayName("should throw for unsupported type")
        void shouldThrowForUnsupportedType() {
            assertThatThrownBy(() -> converter.fromString("2024-01-01", Year.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported temporal type");
        }

        @Test
        @DisplayName("should throw for unparseable value")
        void shouldThrowForUnparseable() {
            assertThatThrownBy(() -> converter.fromString("not-a-date", LocalDate.class))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("fromString - fallback conversion")
    class FromStringFallback {

        @Test
        @DisplayName("should use ZonedDateTime fallback for standard ISO format")
        void shouldFallbackViaZonedDateTime() {
            // Standard ISO-8601 ZonedDateTime format parsed by ZonedDateTime.parse()
            LocalDateTime result = converter.fromString("2024-03-15T10:30:00+09:00[Asia/Seoul]", LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should use OffsetDateTime fallback")
        void shouldFallbackViaOffsetDateTime() {
            // This format parses as OffsetDateTime but not as ZonedDateTime
            OffsetDateTime result = converter.fromString("2024-03-15T10:30:00+09:00", OffsetDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should use LocalDateTime fallback")
        void shouldFallbackViaLocalDateTime() {
            // ISO LocalDateTime format (no timezone)
            LocalDate result = converter.fromString("2024-03-15T10:30:00", LocalDate.class);
            assertThat(result).isNotNull();
            assertThat(result.getYear()).isEqualTo(2024);
        }

        @Test
        @DisplayName("should convert ZonedDateTime to all types via fallback")
        void shouldConvertZonedDateTimeToAllTypes() {
            String zdtStr = "2024-06-15T12:00:00+09:00[Asia/Seoul]";

            assertThat(converter.fromString(zdtStr, ZonedDateTime.class)).isNotNull();
            assertThat(converter.fromString(zdtStr, OffsetDateTime.class)).isNotNull();
            assertThat(converter.fromString(zdtStr, LocalDateTime.class)).isNotNull();
            assertThat(converter.fromString(zdtStr, LocalDate.class)).isNotNull();
            assertThat(converter.fromString(zdtStr, LocalTime.class)).isNotNull();
            assertThat(converter.fromString(zdtStr, Instant.class)).isNotNull();
        }

        @Test
        @DisplayName("should convert LocalDateTime to all types via fallback")
        void shouldConvertLocalDateTimeToAllTypes() {
            String ldtStr = "2024-06-15T12:00:00";

            assertThat(converter.fromString(ldtStr, ZonedDateTime.class)).isNotNull();
            assertThat(converter.fromString(ldtStr, OffsetDateTime.class)).isNotNull();
            assertThat(converter.fromString(ldtStr, LocalDate.class)).isNotNull();
            assertThat(converter.fromString(ldtStr, LocalTime.class)).isNotNull();
            assertThat(converter.fromString(ldtStr, Instant.class)).isNotNull();
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.toString(null)).isNull();
        }

        @Test
        @DisplayName("should convert LocalDateTime to string")
        void shouldConvertLocalDateTime() {
            LocalDateTime ldt = LocalDateTime.of(2024, 3, 15, 10, 30, 0);
            String result = converter.toString(ldt);
            assertThat(result).contains("2024-03-15");
        }

        @Test
        @DisplayName("should convert ZonedDateTime to string")
        void shouldConvertZonedDateTime() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 3, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = converter.toString(zdt);
            assertThat(result).contains("2024-03-15");
        }

        @Test
        @DisplayName("should convert OffsetDateTime to string")
        void shouldConvertOffsetDateTime() {
            OffsetDateTime odt = OffsetDateTime.of(2024, 3, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            String result = converter.toString(odt);
            assertThat(result).contains("2024-03-15");
        }

        @Test
        @DisplayName("should convert Instant to string")
        void shouldConvertInstant() {
            Instant instant = Instant.parse("2024-03-15T10:30:00Z");
            String result = converter.toString(instant);
            assertThat(result).contains("2024-03-15");
        }

        @Test
        @DisplayName("should convert LocalDate to string")
        void shouldConvertLocalDate() {
            LocalDate date = LocalDate.of(2024, 3, 15);
            String result = converter.toString(date);
            assertThat(result).contains("2024-03-15");
        }

        @Test
        @DisplayName("should convert LocalTime to string")
        void shouldConvertLocalTime() {
            LocalTime time = LocalTime.of(10, 30, 0);
            String result = converter.toString(time);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should fallback to toString for unsupported temporal")
        void shouldFallbackForUnsupported() {
            // Year is a Temporal but not handled explicitly
            Year year = Year.of(2024);
            String result = converter.toString(year);
            assertThat(result).isEqualTo("2024");
        }
    }

    @Nested
    @DisplayName("DateTimeConverter interface")
    class DateTimeConverterInterface {

        @Test
        @DisplayName("should create default converter via static method")
        void shouldCreateDefaultConverter() {
            DateTimeConverter defaultConverter = DateTimeConverter.getDefault();
            assertThat(defaultConverter).isNotNull();
            assertThat(defaultConverter).isInstanceOf(StandardDateTimeConverter.class);
        }

        @Test
        @DisplayName("should create converter with timezone via static method")
        void shouldCreateConverterWithTimezone() {
            DateTimeConverter utcConverter = DateTimeConverter.of(ZoneId.of("UTC"));
            assertThat(utcConverter).isNotNull();
            assertThat(utcConverter).isInstanceOf(StandardDateTimeConverter.class);
        }
    }
}
