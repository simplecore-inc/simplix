package dev.simplecore.simplix.core.convert.datetime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StandardDateTimeConverter")
class StandardDateTimeConverterTest {

    private StandardDateTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StandardDateTimeConverter(ZoneId.of("UTC"));
    }

    @Nested
    @DisplayName("fromString to LocalDateTime")
    class FromStringToLocalDateTime {

        @Test
        @DisplayName("should parse ISO datetime without millis")
        void shouldParseIsoDatetime() {
            LocalDateTime result = converter.fromString("2024-01-15T10:30:00", LocalDateTime.class);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should parse common datetime format")
        void shouldParseCommonDatetime() {
            LocalDateTime result = converter.fromString("2024-01-15 10:30:00", LocalDateTime.class);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should parse compact datetime format")
        void shouldParseCompactDatetime() {
            LocalDateTime result = converter.fromString("20240115103000", LocalDateTime.class);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            LocalDateTime result = converter.fromString(null, LocalDateTime.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            LocalDateTime result = converter.fromString("", LocalDateTime.class);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("fromString to LocalDate")
    class FromStringToLocalDate {

        @Test
        @DisplayName("should parse ISO date format")
        void shouldParseIsoDate() {
            LocalDate result = converter.fromString("2024-01-15", LocalDate.class);

            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse compact date format")
        void shouldParseCompactDate() {
            LocalDate result = converter.fromString("20240115", LocalDate.class);

            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse slash-separated date")
        void shouldParseSlashDate() {
            LocalDate result = converter.fromString("2024/01/15", LocalDate.class);

            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse dot-separated date")
        void shouldParseDotDate() {
            LocalDate result = converter.fromString("2024.01.15", LocalDate.class);

            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }
    }

    @Nested
    @DisplayName("fromString to LocalTime")
    class FromStringToLocalTime {

        @Test
        @DisplayName("should parse full time format")
        void shouldParseFullTime() {
            LocalTime result = converter.fromString("10:30:00", LocalTime.class);

            assertThat(result).isEqualTo(LocalTime.of(10, 30, 0));
        }

        @Test
        @DisplayName("should parse short time format")
        void shouldParseShortTime() {
            LocalTime result = converter.fromString("10:30", LocalTime.class);

            assertThat(result).isEqualTo(LocalTime.of(10, 30));
        }
    }

    @Nested
    @DisplayName("fromString with unsupported type")
    class FromStringUnsupportedType {

        @Test
        @DisplayName("should throw for unparseable value")
        void shouldThrowForUnparseableValue() {
            assertThatThrownBy(() -> converter.fromString("not-a-date", LocalDateTime.class))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should format LocalDateTime to ISO string")
        void shouldFormatLocalDateTime() {
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

            String result = converter.toString(dateTime);

            assertThat(result).contains("2024-01-15");
            assertThat(result).contains("10:30:00");
        }

        @Test
        @DisplayName("should format LocalDate to ISO string")
        void shouldFormatLocalDate() {
            LocalDate date = LocalDate.of(2024, 1, 15);

            String result = converter.toString(date);

            assertThat(result).contains("2024-01-15");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            String result = converter.toString(null);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create converter with default timezone")
        void shouldCreateWithDefaultTimezone() {
            StandardDateTimeConverter defaultConverter = new StandardDateTimeConverter();

            // Should not throw and should work for basic parsing
            LocalDate result = defaultConverter.fromString("2024-01-15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should create converter with specific timezone")
        void shouldCreateWithSpecificTimezone() {
            StandardDateTimeConverter seoulConverter = new StandardDateTimeConverter(ZoneId.of("Asia/Seoul"));

            LocalDate result = seoulConverter.fromString("2024-01-15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }
    }
}
