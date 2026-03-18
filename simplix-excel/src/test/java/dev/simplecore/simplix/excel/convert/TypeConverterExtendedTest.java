package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TypeConverter - Extended Coverage")
class TypeConverterExtendedTest {

    @Nested
    @DisplayName("toString with temporal types")
    class ToStringTemporalTests {

        @Test
        @DisplayName("should format Date value")
        void shouldFormatDate() {
            Date date = new Date(0);
            String result = TypeConverter.toString(date, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Calendar value")
        void shouldFormatCalendar() {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(0);
            String result = TypeConverter.toString(cal, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format LocalDate value")
        void shouldFormatLocalDate() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            String result = TypeConverter.toString(date, null);
            assertThat(result).contains("2024");
        }

        @Test
        @DisplayName("should format LocalDateTime value")
        void shouldFormatLocalDateTime() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
            String result = TypeConverter.toString(dt, null);
            assertThat(result).contains("2024");
        }

        @Test
        @DisplayName("should format LocalTime value")
        void shouldFormatLocalTime() {
            LocalTime time = LocalTime.of(14, 45, 30);
            String result = TypeConverter.toString(time, null);
            assertThat(result).contains("14");
        }

        @Test
        @DisplayName("should format ZonedDateTime value")
        void shouldFormatZonedDateTime() {
            ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            String result = TypeConverter.toString(zdt, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format OffsetDateTime value")
        void shouldFormatOffsetDateTime() {
            OffsetDateTime odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            String result = TypeConverter.toString(odt, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Instant value")
        void shouldFormatInstant() {
            Instant instant = Instant.ofEpochMilli(1705276800000L);
            String result = TypeConverter.toString(instant, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Enum value")
        void shouldFormatEnum() {
            String result = TypeConverter.toString(TestStatus.ACTIVE, null);
            assertThat(result).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("fromString with primitive types")
    class FromStringPrimitiveTests {

        @Test
        @DisplayName("should convert to long primitive")
        void shouldConvertToLongPrimitive() {
            Object result = TypeConverter.fromString("100", long.class);
            assertThat(result).isEqualTo(100L);
        }

        @Test
        @DisplayName("should convert to double primitive")
        void shouldConvertToDoublePrimitive() {
            Object result = TypeConverter.fromString("3.14", double.class);
            assertThat(result).isEqualTo(3.14);
        }

        @Test
        @DisplayName("should convert to float primitive")
        void shouldConvertToFloatPrimitive() {
            Object result = TypeConverter.fromString("2.5", float.class);
            assertThat(result).isEqualTo(2.5f);
        }

        @Test
        @DisplayName("should convert to boolean primitive")
        void shouldConvertToBooleanPrimitive() {
            Object result = TypeConverter.fromString("true", boolean.class);
            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("should convert N to Boolean false")
        void shouldConvertNToFalse() {
            Object result = TypeConverter.fromString("N", Boolean.class);
            assertThat(result).isEqualTo(false);
        }

        @Test
        @DisplayName("should convert false string to Boolean false")
        void shouldConvertFalseStringToFalse() {
            Object result = TypeConverter.fromString("false", Boolean.class);
            assertThat(result).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("fromString with temporal types")
    class FromStringTemporalTests {

        @Test
        @DisplayName("should convert to Calendar")
        void shouldConvertToCalendar() {
            Object result = TypeConverter.fromString("2024-01-15", Calendar.class);
            assertThat(result).isInstanceOf(Calendar.class);
        }

        @Test
        @DisplayName("should convert to Date")
        void shouldConvertToDate() {
            Object result = TypeConverter.fromString("2024-01-15", Date.class);
            assertThat(result).isInstanceOf(Date.class);
        }

        @Test
        @DisplayName("should convert to LocalTime")
        void shouldConvertToLocalTime() {
            Object result = TypeConverter.fromString("10:30:45", LocalTime.class);
            assertThat(result).isEqualTo(LocalTime.of(10, 30, 45));
        }

        @Test
        @DisplayName("should convert to ZonedDateTime from epoch millis")
        void shouldConvertToZonedDateTimeFromEpoch() {
            Object result = TypeConverter.fromString("1705276800000", ZonedDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should convert to OffsetDateTime from epoch millis")
        void shouldConvertToOffsetDateTimeFromEpoch() {
            Object result = TypeConverter.fromString("1705276800000", OffsetDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should convert to Instant")
        void shouldConvertToInstant() {
            Object result = TypeConverter.fromString("1705276800000", Instant.class);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("fromString with enum type")
    class FromStringEnumTests {

        @Test
        @DisplayName("should convert to enum by name")
        void shouldConvertToEnum() {
            Object result = TypeConverter.fromString("ACTIVE", TestStatus.class);
            assertThat(result).isEqualTo(TestStatus.ACTIVE);
        }

        @Test
        @DisplayName("should convert to enum case insensitive")
        void shouldConvertCaseInsensitive() {
            Object result = TypeConverter.fromString("inactive", TestStatus.class);
            assertThat(result).isEqualTo(TestStatus.INACTIVE);
        }
    }

    @Nested
    @DisplayName("parseDate")
    class ParseDateTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(TypeConverter.parseDate(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(TypeConverter.parseDate("")).isNull();
        }

        @Test
        @DisplayName("should parse ISO date format")
        void shouldParseIsoDate() {
            Date result = TypeConverter.parseDate("2024-06-15");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse timestamp format")
        void shouldParseTimestamp() {
            Date result = TypeConverter.parseDate("1705276800000");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for unparseable date")
        void shouldReturnNullForUnparseable() {
            assertThat(TypeConverter.parseDate("not-a-date-xyz")).isNull();
        }

        @Test
        @DisplayName("should parse datetime format")
        void shouldParseDatetime() {
            Date result = TypeConverter.parseDate("2024-01-15 10:30:00");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse ISO datetime format")
        void shouldParseIsoDatetime() {
            Date result = TypeConverter.parseDate("2024-01-15T10:30:00");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("formatNumber edge cases")
    class FormatNumberEdgeCaseTests {

        @Test
        @DisplayName("should format with empty pattern using default")
        void shouldFormatWithEmptyPattern() {
            String result = TypeConverter.formatNumber(42, "");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should handle invalid pattern gracefully")
        void shouldHandleInvalidPattern() {
            // Invalid pattern should return number.toString() as fallback
            String result = TypeConverter.formatNumber(42, "invalid{{pattern}}");
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("formatMap edge cases")
    class FormatMapTests {

        @Test
        @DisplayName("should format empty map")
        void shouldFormatEmptyMap() {
            assertThat(TypeConverter.formatMap(Collections.emptyMap())).isEmpty();
        }

        @Test
        @DisplayName("should format map with multiple entries")
        void shouldFormatMultipleEntries() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key1", "val1");
            map.put("key2", 42);
            String result = TypeConverter.formatMap(map);
            assertThat(result).contains("key1=val1");
            assertThat(result).contains("key2=");
        }
    }

    @Nested
    @DisplayName("formatArray edge cases")
    class FormatArrayTests {

        @Test
        @DisplayName("should format empty object array")
        void shouldFormatEmptyObjectArray() {
            String[] arr = {};
            assertThat(TypeConverter.formatArray(arr)).isEmpty();
        }

        @Test
        @DisplayName("should format long primitive array")
        void shouldFormatLongArray() {
            long[] arr = {10L, 20L, 30L};
            String result = TypeConverter.formatArray(arr);
            assertThat(result).contains("10").contains("20").contains("30");
        }

        @Test
        @DisplayName("should format boolean primitive array")
        void shouldFormatBooleanArray() {
            boolean[] arr = {true, false};
            String result = TypeConverter.formatArray(arr);
            assertThat(result).contains("Y").contains("N");
        }

        @Test
        @DisplayName("should format double primitive array")
        void shouldFormatDoubleArray() {
            double[] arr = {1.5, 2.5};
            String result = TypeConverter.formatArray(arr);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("setDefaultZone")
    class SetDefaultZoneTests {

        @Test
        @DisplayName("should accept non-null zone")
        void shouldAcceptNonNullZone() {
            TypeConverter.setDefaultZone(ZoneId.of("UTC"));
            // No exception should be thrown
            TypeConverter.setDefaultZone(ZoneId.systemDefault());
        }

        @Test
        @DisplayName("should fallback to system default for null zone")
        void shouldFallbackForNull() {
            TypeConverter.setDefaultZone(null);
            // No exception should be thrown; falls back to system default
            TypeConverter.setDefaultZone(ZoneId.systemDefault());
        }
    }

    @Nested
    @DisplayName("parseTemporal")
    class ParseTemporalTests {

        @Test
        @DisplayName("should parse LocalDate")
        void shouldParseLocalDate() {
            LocalDate result = TypeConverter.parseTemporal("2024-01-15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should parse LocalDateTime")
        void shouldParseLocalDateTime() {
            LocalDateTime result = TypeConverter.parseTemporal("2024-01-15 10:30:00", LocalDateTime.class);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }
    }

    @Nested
    @DisplayName("fromString error handling")
    class FromStringErrorTests {

        @Test
        @DisplayName("should handle conversion error gracefully and return original string")
        void shouldHandleConversionError() {
            Object result = TypeConverter.fromString("not-a-number", Integer.class);
            // Returns original string when conversion fails
            assertThat(result).isEqualTo("not-a-number");
        }

        @Test
        @DisplayName("should return null for whitespace-only input")
        void shouldReturnNullForWhitespace() {
            assertThat(TypeConverter.fromString("   ", String.class)).isNull();
        }

        @Test
        @DisplayName("should handle Calendar with null date parse result")
        void shouldHandleCalendarWithNullResult() {
            Object result = TypeConverter.fromString("invalid-date-xyz", Calendar.class);
            // Should return null when date parse fails
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("parseEnum edge cases")
    class ParseEnumEdgeCaseTests {

        @Test
        @DisplayName("should return null for out-of-range ordinal")
        void shouldReturnNullForOutOfRangeOrdinal() {
            assertThat(TypeConverter.parseEnum("99", TestStatus.class)).isNull();
        }

        @Test
        @DisplayName("should return null for negative ordinal")
        void shouldReturnNullForNegativeOrdinal() {
            assertThat(TypeConverter.parseEnum("-1", TestStatus.class)).isNull();
        }

        @Test
        @DisplayName("should handle whitespace in enum value")
        void shouldHandleWhitespace() {
            assertThat(TypeConverter.parseEnum("  ACTIVE  ", TestStatus.class)).isEqualTo(TestStatus.ACTIVE);
        }
    }

    enum TestStatus {
        ACTIVE, INACTIVE
    }
}
