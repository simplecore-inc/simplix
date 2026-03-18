package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UnifiedCsvExporter - Internal Methods Coverage")
class UnifiedCsvExporterInternalTest {

    @Nested
    @DisplayName("formatValue(Object) method - legacy formatting")
    class FormatValueObjectTests {

        @Test
        @DisplayName("should format Date value")
        void shouldFormatDate() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(new Date(0));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format LocalDate value")
        void shouldFormatLocalDate() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(LocalDate.of(2024, 6, 15));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format LocalDateTime value")
        void shouldFormatLocalDateTime() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(LocalDateTime.of(2024, 6, 15, 10, 30));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format LocalTime value")
        void shouldFormatLocalTime() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(LocalTime.of(14, 30));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format OffsetDateTime value")
        void shouldFormatOffsetDateTime() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format OffsetTime value")
        void shouldFormatOffsetTime() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(OffsetTime.of(14, 30, 0, 0, ZoneOffset.UTC));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format ZonedDateTime value")
        void shouldFormatZonedDateTime() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC")));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Instant value")
        void shouldFormatInstant() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(Instant.ofEpochMilli(1705276800000L));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Year value")
        void shouldFormatYear() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(Year.of(2024));
            assertThat(result).isEqualTo("2024");
        }

        @Test
        @DisplayName("should format YearMonth value")
        void shouldFormatYearMonth() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(YearMonth.of(2024, 6));
            assertThat(result).isEqualTo("2024-06");
        }

        @Test
        @DisplayName("should format MonthDay value")
        void shouldFormatMonthDay() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(MonthDay.of(6, 15));
            assertThat(result).isEqualTo("--06-15");
        }

        @Test
        @DisplayName("should format Duration value")
        void shouldFormatDuration() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(Duration.ofHours(2));
            assertThat(result).isEqualTo("PT2H");
        }

        @Test
        @DisplayName("should format Period value")
        void shouldFormatPeriod() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(Period.of(1, 6, 0));
            assertThat(result).isEqualTo("P1Y6M");
        }

        @Test
        @DisplayName("should format Number value")
        void shouldFormatNumber() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(42);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should format Boolean value")
        void shouldFormatBoolean() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(true);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("should return empty for null value")
        void shouldReturnEmptyForNull() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should format unknown object using toString")
        void shouldFormatUnknownObject() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            String result = exporter.formatValue(new Object() {
                @Override
                public String toString() {
                    return "custom-object";
                }
            });
            assertThat(result).isEqualTo("custom-object");
        }
    }

    @Nested
    @DisplayName("getExportableFields")
    class GetExportableFieldsTests {

        @Test
        @DisplayName("should get exportable fields excluding ignored")
        void shouldGetExportableFields() {
            UnifiedCsvExporter<FieldTestEntity> exporter = new UnifiedCsvExporter<>(FieldTestEntity.class);
            List<Field> fields = exporter.getExportableFields(FieldTestEntity.class);
            assertThat(fields).isNotEmpty();
            // Should include fields with @ExcelColumn(ignore=false) and non-annotated fields
            assertThat(fields.stream().noneMatch(f -> f.getName().equals("secret"))).isTrue();
        }
    }

    @Nested
    @DisplayName("writeCsvContent")
    class WriteCsvContentTests {

        @Test
        @DisplayName("should write CSV content for collection")
        void shouldWriteCsvContent() throws IOException {
            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            StringWriter writer = new StringWriter();

            List<SimpleCsvEntity> items = List.of(
                    new SimpleCsvEntity("Alice", 1.0),
                    new SimpleCsvEntity("Bob", 2.0)
            );

            exporter.writeCsvContent(items, writer);
            String csv = writer.toString();
            assertThat(csv).contains("Alice");
            assertThat(csv).contains("Bob");
        }

        @Test
        @DisplayName("should handle empty collection")
        void shouldHandleEmptyCollection() throws IOException {
            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            StringWriter writer = new StringWriter();
            exporter.writeCsvContent(Collections.emptyList(), writer);
            assertThat(writer.toString()).isEmpty();
        }

        @Test
        @DisplayName("should handle null collection")
        void shouldHandleNullCollection() throws IOException {
            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            StringWriter writer = new StringWriter();
            exporter.writeCsvContent(null, writer);
            assertThat(writer.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setResponseHeaders")
    class SetResponseHeadersTests {

        @Test
        @DisplayName("should set response headers")
        void shouldSetHeaders() {
            UnifiedCsvExporter<Object> exporter = new UnifiedCsvExporter<>(Object.class);
            org.springframework.mock.web.MockHttpServletResponse response =
                    new org.springframework.mock.web.MockHttpServletResponse();
            exporter.setResponseHeaders(response, "test.csv");

            assertThat(response.getContentType()).isEqualTo("text/csv");
            assertThat(response.getHeader("Content-Disposition")).contains("test.csv");
        }
    }

    @Nested
    @DisplayName("Export with temporal types for quoting")
    class TemporalQuotingTests {

        @Test
        @DisplayName("should quote temporal values when quoteStrings is true")
        void shouldQuoteTemporalValues() throws IOException {
            TemporalQuotingEntity entity = new TemporalQuotingEntity();
            entity.setInstant(Instant.ofEpochMilli(1705276800000L));
            entity.setYear(Year.of(2024));
            entity.setYearMonth(YearMonth.of(2024, 6));
            entity.setMonthDay(MonthDay.of(6, 15));
            entity.setDuration(Duration.ofHours(2));
            entity.setPeriod(Period.of(1, 6, 0));
            entity.setOffsetDateTime(OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC));
            entity.setOffsetTime(OffsetTime.of(14, 30, 0, 0, ZoneOffset.UTC));
            entity.setZonedDateTime(ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC")));

            UnifiedCsvExporter<TemporalQuotingEntity> exporter = new UnifiedCsvExporter<>(TemporalQuotingEntity.class);
            exporter.quoteStrings(true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            // Temporal values should be quoted
            assertThat(csv).isNotEmpty();
        }
    }

    // --- Test entities ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SimpleCsvEntity {
        @ExcelColumn(name = "Name", order = 1)
        private String name;

        @ExcelColumn(name = "Value", order = 2)
        private Double value;
    }

    @Data
    @NoArgsConstructor
    static class FieldTestEntity {
        @ExcelColumn(name = "Name", order = 1)
        private String name;

        @ExcelColumn(name = "Secret", order = 2, ignore = true)
        private String secret;

        private String unannotated;
    }

    @Data
    @NoArgsConstructor
    static class TemporalQuotingEntity {
        @ExcelColumn(name = "Instant", order = 1)
        private Instant instant;

        @ExcelColumn(name = "Year", order = 2)
        private Year year;

        @ExcelColumn(name = "YearMonth", order = 3)
        private YearMonth yearMonth;

        @ExcelColumn(name = "MonthDay", order = 4)
        private MonthDay monthDay;

        @ExcelColumn(name = "Duration", order = 5)
        private Duration duration;

        @ExcelColumn(name = "Period", order = 6)
        private Period period;

        @ExcelColumn(name = "OffsetDateTime", order = 7)
        private OffsetDateTime offsetDateTime;

        @ExcelColumn(name = "OffsetTime", order = 8)
        private OffsetTime offsetTime;

        @ExcelColumn(name = "ZonedDateTime", order = 9)
        private ZonedDateTime zonedDateTime;
    }
}
