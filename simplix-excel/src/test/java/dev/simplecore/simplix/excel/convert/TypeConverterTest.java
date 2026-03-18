package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TypeConverter")
class TypeConverterTest {

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should return empty for null value")
        void shouldReturnEmptyForNull() {
            assertThat(TypeConverter.toString(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should return string value as-is")
        void shouldReturnStringAsIs() {
            assertThat(TypeConverter.toString("hello", null)).isEqualTo("hello");
        }

        @Test
        @DisplayName("should format number with default pattern")
        void shouldFormatNumber() {
            String result = TypeConverter.toString(1234.567, null);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format boolean true as Y")
        void shouldFormatBooleanTrue() {
            assertThat(TypeConverter.toString(Boolean.TRUE, null)).isEqualTo("Y");
        }

        @Test
        @DisplayName("should format boolean false as N")
        void shouldFormatBooleanFalse() {
            assertThat(TypeConverter.toString(Boolean.FALSE, null)).isEqualTo("N");
        }

        @Test
        @DisplayName("should format collection")
        void shouldFormatCollection() {
            List<String> list = List.of("a", "b", "c");
            String result = TypeConverter.toString(list, null);
            assertThat(result).isEqualTo("a, b, c");
        }

        @Test
        @DisplayName("should format empty collection")
        void shouldFormatEmptyCollection() {
            assertThat(TypeConverter.toString(Collections.emptyList(), null)).isEmpty();
        }

        @Test
        @DisplayName("should format map")
        void shouldFormatMap() {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("a", 1);
            String result = TypeConverter.toString(map, null);
            assertThat(result).contains("a=");
        }

        @Test
        @DisplayName("should format array")
        void shouldFormatArray() {
            String[] arr = {"x", "y"};
            String result = TypeConverter.toString(arr, null);
            assertThat(result).isEqualTo("x, y");
        }

        @Test
        @DisplayName("should format primitive array")
        void shouldFormatPrimitiveArray() {
            int[] arr = {1, 2, 3};
            String result = TypeConverter.toString(arr, null);
            assertThat(result).contains("1").contains("2").contains("3");
        }

        @Test
        @DisplayName("should format LocalDate with null pattern")
        void shouldFormatLocalDateNull() {
            assertThat(TypeConverter.formatLocalDate(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format LocalDateTime with null pattern")
        void shouldFormatLocalDateTimeNull() {
            assertThat(TypeConverter.formatLocalDateTime(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format LocalTime with null pattern")
        void shouldFormatLocalTimeNull() {
            assertThat(TypeConverter.formatLocalTime(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format ZonedDateTime with null pattern")
        void shouldFormatZonedDateTimeNull() {
            assertThat(TypeConverter.formatZonedDateTime(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format OffsetDateTime with null pattern")
        void shouldFormatOffsetDateTimeNull() {
            assertThat(TypeConverter.formatOffsetDateTime(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format Instant with null pattern")
        void shouldFormatInstantNull() {
            assertThat(TypeConverter.formatInstant(null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format Date with null pattern")
        void shouldFormatDateNull() {
            assertThat(TypeConverter.formatDate(null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("formatNumber")
    class FormatNumberTests {

        @Test
        @DisplayName("should format with default pattern")
        void shouldFormatWithDefaultPattern() {
            String result = TypeConverter.formatNumber(1234.5, null);
            assertThat(result).contains("1,234.5");
        }

        @Test
        @DisplayName("should format with custom pattern")
        void shouldFormatWithCustomPattern() {
            String result = TypeConverter.formatNumber(1234.5, "#,##0.00");
            assertThat(result).isEqualTo("1,234.50");
        }
    }

    @Nested
    @DisplayName("formatEnum")
    class FormatEnumTests {

        @Test
        @DisplayName("should return empty for null enum")
        void shouldReturnEmptyForNull() {
            assertThat(TypeConverter.formatEnum(null)).isEmpty();
        }

        @Test
        @DisplayName("should return name when no extractor registered")
        void shouldReturnNameByDefault() {
            assertThat(TypeConverter.formatEnum(TestEnum.ACTIVE)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should use registered extractor")
        void shouldUseRegisteredExtractor() {
            TypeConverter.registerEnumValueExtractor(TestEnum.class, e -> e.name().toLowerCase());
            assertThat(TypeConverter.formatEnum(TestEnum.ACTIVE)).isEqualTo("active");
        }
    }

    @Nested
    @DisplayName("fromString")
    class FromStringTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(TypeConverter.fromString(null, String.class)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmpty() {
            assertThat(TypeConverter.fromString("", Integer.class)).isNull();
        }

        @Test
        @DisplayName("should convert to String")
        void shouldConvertToString() {
            assertThat(TypeConverter.fromString("hello", String.class)).isEqualTo("hello");
        }

        @Test
        @DisplayName("should convert to Integer")
        void shouldConvertToInteger() {
            assertThat(TypeConverter.fromString("42", Integer.class)).isEqualTo(42);
        }

        @Test
        @DisplayName("should convert to int primitive")
        void shouldConvertToIntPrimitive() {
            assertThat(TypeConverter.fromString("42", int.class)).isEqualTo(42);
        }

        @Test
        @DisplayName("should convert to Long")
        void shouldConvertToLong() {
            assertThat(TypeConverter.fromString("100000", Long.class)).isEqualTo(100000L);
        }

        @Test
        @DisplayName("should convert to Double")
        void shouldConvertToDouble() {
            assertThat(TypeConverter.fromString("3.14", Double.class)).isEqualTo(3.14);
        }

        @Test
        @DisplayName("should convert to Float")
        void shouldConvertToFloat() {
            assertThat(TypeConverter.fromString("3.14", Float.class)).isEqualTo(3.14f);
        }

        @Test
        @DisplayName("should convert to BigDecimal")
        void shouldConvertToBigDecimal() {
            Object result = TypeConverter.fromString("123.45", BigDecimal.class);
            assertThat(result).isInstanceOf(BigDecimal.class);
            assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal("123.45"));
        }

        @Test
        @DisplayName("should convert 'true' to Boolean")
        void shouldConvertTrueToBoolean() {
            assertThat(TypeConverter.fromString("true", Boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("should convert 'Y' to Boolean true")
        void shouldConvertYToBoolean() {
            assertThat(TypeConverter.fromString("Y", Boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("should convert 'Yes' to Boolean true")
        void shouldConvertYesToBoolean() {
            assertThat(TypeConverter.fromString("Yes", Boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("should convert '1' to Boolean true")
        void shouldConvert1ToBoolean() {
            assertThat(TypeConverter.fromString("1", Boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("should convert to LocalDate")
        void shouldConvertToLocalDate() {
            Object result = TypeConverter.fromString("2024-01-15", LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("should convert to LocalDateTime")
        void shouldConvertToLocalDateTime() {
            Object result = TypeConverter.fromString("2024-01-15 10:30:00", LocalDateTime.class);
            assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should convert to enum by exact name")
        void shouldConvertToEnumByName() {
            Object result = TypeConverter.fromString("ACTIVE", TestEnum.class);
            assertThat(result).isEqualTo(TestEnum.ACTIVE);
        }

        @Test
        @DisplayName("should return original string for unknown type")
        void shouldReturnOriginalForUnknownType() {
            Object result = TypeConverter.fromString("unparseable", TypeConverterTest.class);
            assertThat(result).isEqualTo("unparseable");
        }
    }

    @Nested
    @DisplayName("parseEnum")
    class ParseEnumTests {

        @Test
        @DisplayName("should return null for null value")
        void shouldReturnNullForNull() {
            assertThat(TypeConverter.parseEnum(null, TestEnum.class)).isNull();
        }

        @Test
        @DisplayName("should return null for empty value")
        void shouldReturnNullForEmpty() {
            assertThat(TypeConverter.parseEnum("", TestEnum.class)).isNull();
        }

        @Test
        @DisplayName("should parse by exact name")
        void shouldParseByExactName() {
            assertThat(TypeConverter.parseEnum("ACTIVE", TestEnum.class)).isEqualTo(TestEnum.ACTIVE);
        }

        @Test
        @DisplayName("should parse case-insensitively")
        void shouldParseCaseInsensitive() {
            assertThat(TypeConverter.parseEnum("active", TestEnum.class)).isEqualTo(TestEnum.ACTIVE);
        }

        @Test
        @DisplayName("should parse by ordinal")
        void shouldParseByOrdinal() {
            assertThat(TypeConverter.parseEnum("0", TestEnum.class)).isEqualTo(TestEnum.ACTIVE);
            assertThat(TypeConverter.parseEnum("1", TestEnum.class)).isEqualTo(TestEnum.INACTIVE);
        }

        @Test
        @DisplayName("should return null for invalid value")
        void shouldReturnNullForInvalid() {
            assertThat(TypeConverter.parseEnum("UNKNOWN", TestEnum.class)).isNull();
        }
    }

    enum TestEnum {
        ACTIVE, INACTIVE
    }
}
