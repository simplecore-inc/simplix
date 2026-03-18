package dev.simplecore.simplix.excel.format;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FormatterCache")
class FormatterCacheTest {

    @AfterEach
    void tearDown() {
        FormatterCache.clearAll();
    }

    @Nested
    @DisplayName("getTemporalFormatter")
    class GetTemporalFormatterTests {

        @Test
        @DisplayName("should return DateTimeFormatter for pattern")
        void shouldReturnFormatter() {
            DateTimeFormatter formatter = FormatterCache.getTemporalFormatter("yyyy-MM-dd");
            assertThat(formatter).isNotNull();
        }

        @Test
        @DisplayName("should cache and return same instance")
        void shouldReturnCachedInstance() {
            DateTimeFormatter first = FormatterCache.getTemporalFormatter("yyyy-MM-dd");
            DateTimeFormatter second = FormatterCache.getTemporalFormatter("yyyy-MM-dd");
            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("should return different instances for different patterns")
        void shouldReturnDifferentForDifferentPatterns() {
            DateTimeFormatter date = FormatterCache.getTemporalFormatter("yyyy-MM-dd");
            DateTimeFormatter dateTime = FormatterCache.getTemporalFormatter("yyyy-MM-dd HH:mm:ss");
            assertThat(date).isNotSameAs(dateTime);
        }
    }

    @Nested
    @DisplayName("getLegacyDateFormatter")
    class GetLegacyDateFormatterTests {

        @Test
        @DisplayName("should return SimpleDateFormat for pattern")
        void shouldReturnFormatter() {
            SimpleDateFormat formatter = FormatterCache.getLegacyDateFormatter("yyyy-MM-dd");
            assertThat(formatter).isNotNull();
        }

        @Test
        @DisplayName("should cache and return same instance")
        void shouldReturnCachedInstance() {
            SimpleDateFormat first = FormatterCache.getLegacyDateFormatter("yyyy-MM-dd");
            SimpleDateFormat second = FormatterCache.getLegacyDateFormatter("yyyy-MM-dd");
            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("getNumberFormatter")
    class GetNumberFormatterTests {

        @Test
        @DisplayName("should return DecimalFormat for pattern")
        void shouldReturnFormatter() {
            DecimalFormat formatter = FormatterCache.getNumberFormatter("#,##0.00");
            assertThat(formatter).isNotNull();
        }

        @Test
        @DisplayName("should cache and return same instance")
        void shouldReturnCachedInstance() {
            DecimalFormat first = FormatterCache.getNumberFormatter("#,##0.00");
            DecimalFormat second = FormatterCache.getNumberFormatter("#,##0.00");
            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("Enum value extractors")
    class EnumValueExtractorTests {

        @Test
        @DisplayName("should register and retrieve enum value extractor")
        void shouldRegisterAndRetrieve() {
            FormatterCache.registerEnumValueExtractor(TestEnum.class, Enum::name);

            Function<TestEnum, String> extractor = FormatterCache.getEnumValueExtractor(TestEnum.class);
            assertThat(extractor).isNotNull();
            assertThat(extractor.apply(TestEnum.VALUE_A)).isEqualTo("VALUE_A");
        }

        @Test
        @DisplayName("should return null for unregistered enum type")
        void shouldReturnNullForUnregistered() {
            assertThat(FormatterCache.getEnumValueExtractor(TestEnum.class)).isNull();
        }
    }

    @Nested
    @DisplayName("getDefaultZone")
    class GetDefaultZoneTests {

        @Test
        @DisplayName("should return system default zone")
        void shouldReturnSystemDefaultZone() {
            assertThat(FormatterCache.getDefaultZone()).isEqualTo(ZoneId.systemDefault());
        }
    }

    @Nested
    @DisplayName("clearAll")
    class ClearAllTests {

        @Test
        @DisplayName("should clear all cached formatters")
        void shouldClearAll() {
            FormatterCache.getTemporalFormatter("yyyy-MM-dd");
            FormatterCache.getLegacyDateFormatter("yyyy-MM-dd");
            FormatterCache.getNumberFormatter("#,##0");
            FormatterCache.registerEnumValueExtractor(TestEnum.class, Enum::name);

            FormatterCache.clearAll();

            // After clear, new instances should be created
            assertThat(FormatterCache.getEnumValueExtractor(TestEnum.class)).isNull();
        }
    }

    enum TestEnum {
        VALUE_A, VALUE_B
    }
}
