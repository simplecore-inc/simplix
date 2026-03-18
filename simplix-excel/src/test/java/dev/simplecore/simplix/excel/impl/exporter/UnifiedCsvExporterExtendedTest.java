package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UnifiedCsvExporter - Extended Coverage")
class UnifiedCsvExporterExtendedTest {

    @Nested
    @DisplayName("Export with various data types")
    class DataTypeExportTests {

        @Test
        @DisplayName("should export Date and Calendar values")
        void shouldExportDateAndCalendar() throws IOException {
            DateCalEntity entity = new DateCalEntity();
            entity.setDate(new Date(0));
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(0);
            entity.setCal(cal);

            UnifiedCsvExporter<DateCalEntity> exporter = new UnifiedCsvExporter<>(DateCalEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).isNotEmpty();
            // Date and Calendar should be formatted
            assertThat(csv.split("\r\n").length).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should export Number values")
        void shouldExportNumberValues() throws IOException {
            NumberEntity entity = new NumberEntity();
            entity.setAmount(12345.678);

            UnifiedCsvExporter<NumberEntity> exporter = new UnifiedCsvExporter<>(NumberEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).contains("12");
        }

        @Test
        @DisplayName("should export Enum values")
        void shouldExportEnumValues() throws IOException {
            EnumCsvEntity entity = new EnumCsvEntity();
            entity.setStatus(CsvTestStatus.ACTIVE);

            UnifiedCsvExporter<EnumCsvEntity> exporter = new UnifiedCsvExporter<>(EnumCsvEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).contains("ACTIVE");
        }

        @Test
        @DisplayName("should export various temporal types")
        void shouldExportTemporalTypes() throws IOException {
            TemporalCsvEntity entity = new TemporalCsvEntity();
            entity.setDate(LocalDate.of(2024, 6, 15));
            entity.setDateTime(LocalDateTime.of(2024, 6, 15, 10, 30));
            entity.setTime(LocalTime.of(14, 30));

            UnifiedCsvExporter<TemporalCsvEntity> exporter = new UnifiedCsvExporter<>(TemporalCsvEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).contains("2024");
            assertThat(csv).contains("14:30");
        }

        @Test
        @DisplayName("should export null field values as empty")
        void shouldExportNullAsEmpty() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName(null);
            entity.setValue(null);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Export without header")
    class NoHeaderTests {

        @Test
        @DisplayName("should export without header when disabled")
        void shouldExportWithoutHeader() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Alice");
            entity.setValue(42.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.includeHeader(false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).doesNotContain("\"Name\"");
            assertThat(csv).contains("Alice");
        }
    }

    @Nested
    @DisplayName("Export with quoting disabled")
    class NoQuotingTests {

        @Test
        @DisplayName("should export without quoting strings")
        void shouldExportWithoutQuotes() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Alice");
            entity.setValue(42.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.quoteStrings(false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            // Headers should not be quoted
            assertThat(csv.split("\r\n")[0]).doesNotContain("\"");
        }
    }

    @Nested
    @DisplayName("Export with null/empty collections")
    class EmptyCollectionTests {

        @Test
        @DisplayName("should handle null collection")
        void shouldHandleNullCollection() throws IOException {
            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(null, baos);

            String csv = baos.toString("UTF-8");
            // Should at least have the header
            assertThat(csv).isNotEmpty();
        }

        @Test
        @DisplayName("should handle empty collection")
        void shouldHandleEmptyCollection() throws IOException {
            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(Collections.emptyList(), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Encoding options")
    class EncodingTests {

        @Test
        @DisplayName("should export with UTF-8 BOM")
        void shouldExportWithUtf8Bom() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Test");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding(UnifiedCsvExporter.Encoding.UTF8_BOM);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            byte[] bytes = baos.toByteArray();
            // Check BOM bytes
            assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
            assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
            assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        }

        @Test
        @DisplayName("should export with UTF-16LE BOM")
        void shouldExportWithUtf16LeBom() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("T");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding(UnifiedCsvExporter.Encoding.UTF16_LE);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            byte[] bytes = baos.toByteArray();
            assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
            assertThat(bytes[1] & 0xFF).isEqualTo(0xFE);
        }

        @Test
        @DisplayName("should export with UTF-16BE BOM")
        void shouldExportWithUtf16BeBom() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("T");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding(UnifiedCsvExporter.Encoding.UTF16_BE);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            byte[] bytes = baos.toByteArray();
            assertThat(bytes[0] & 0xFF).isEqualTo(0xFE);
            assertThat(bytes[1] & 0xFF).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("should handle unknown encoding string gracefully")
        void shouldHandleUnknownEncoding() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Test");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding("UNKNOWN_ENCODING");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            assertThat(baos.size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should handle encoding by enum name string")
        void shouldHandleEncodingByEnumName() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Test");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding("UTF8_BOM");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            byte[] bytes = baos.toByteArray();
            assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        }

        @Test
        @DisplayName("should handle ISO-8859-1 encoding")
        void shouldHandleIso88591() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Test");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding("ISO-8859-1");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            assertThat(baos.size()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Streaming mode with data provider")
    class StreamingTests {

        @Test
        @DisplayName("should export using data provider in streaming mode")
        void shouldExportWithDataProvider() throws IOException {
            List<SimpleCsvEntity> allData = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                SimpleCsvEntity e = new SimpleCsvEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                allData.add(e);
            }

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.streaming(true)
                    .batchSize(10)
                    .dataProvider(pageRequest -> {
                        int start = (int) pageRequest.getOffset();
                        int end = Math.min(start + pageRequest.getPageSize(), allData.size());
                        List<SimpleCsvEntity> content = start < allData.size()
                                ? allData.subList(start, end)
                                : List.of();
                        return new PageImpl<>(content, pageRequest, allData.size());
                    });

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(null, baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).contains("User 0");
            assertThat(csv).contains("User 29");
        }

        @Test
        @DisplayName("should export streaming with data provider to HTTP response")
        void shouldExportStreamingToResponse() throws IOException {
            List<SimpleCsvEntity> allData = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                SimpleCsvEntity e = new SimpleCsvEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                allData.add(e);
            }

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.streaming(true)
                    .batchSize(10)
                    .dataProvider(pageRequest -> {
                        int start = (int) pageRequest.getOffset();
                        int end = Math.min(start + pageRequest.getPageSize(), allData.size());
                        List<SimpleCsvEntity> content = start < allData.size()
                                ? allData.subList(start, end)
                                : List.of();
                        return new PageImpl<>(content, pageRequest, allData.size());
                    });

            MockHttpServletResponse response = new MockHttpServletResponse();
            exporter.export(null, response);

            assertThat(response.getContentAsByteArray().length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Batch processing")
    class BatchProcessingTests {

        @Test
        @DisplayName("should process large collection in batches")
        void shouldProcessInBatches() throws IOException {
            List<SimpleCsvEntity> items = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                SimpleCsvEntity e = new SimpleCsvEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                items.add(e);
            }

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.batchSize(500);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(items, baos);

            String csv = baos.toString("UTF-8");
            String[] lines = csv.split("\r\n");
            // Header + 5000 data lines
            assertThat(lines.length).isEqualTo(5001);
        }
    }

    @Nested
    @DisplayName("Custom settings")
    class CustomSettingsTests {

        @Test
        @DisplayName("should use custom date format")
        void shouldUseCustomDateFormat() throws IOException {
            TemporalCsvEntity entity = new TemporalCsvEntity();
            entity.setDate(LocalDate.of(2024, 6, 15));
            entity.setDateTime(LocalDateTime.of(2024, 6, 15, 10, 30));
            entity.setTime(LocalTime.of(14, 30));

            UnifiedCsvExporter<TemporalCsvEntity> exporter = new UnifiedCsvExporter<>(TemporalCsvEntity.class);
            exporter.dateFormat("dd/MM/yyyy");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).isNotEmpty();
        }

        @Test
        @DisplayName("should use custom number format")
        void shouldUseCustomNumberFormat() throws IOException {
            NumberEntity entity = new NumberEntity();
            entity.setAmount(12345.678);

            UnifiedCsvExporter<NumberEntity> exporter = new UnifiedCsvExporter<>(NumberEntity.class);
            exporter.numberFormat("#,##0.00");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).contains("12,345.68");
        }

        @Test
        @DisplayName("should use custom line ending")
        void shouldUseCustomLineEnding() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Test");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.lineEnding("\n");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            String csv = baos.toString("UTF-8");
            assertThat(csv).doesNotContain("\r\n");
            assertThat(csv).contains("\n");
        }
    }

    @Nested
    @DisplayName("HTTP response export with BOM")
    class HttpResponseBomTests {

        @Test
        @DisplayName("should write BOM for UTF8_BOM encoding via HTTP response")
        void shouldWriteBomInHttpResponse() throws IOException {
            SimpleCsvEntity entity = new SimpleCsvEntity();
            entity.setName("Test");
            entity.setValue(1.0);

            UnifiedCsvExporter<SimpleCsvEntity> exporter = new UnifiedCsvExporter<>(SimpleCsvEntity.class);
            exporter.encoding(UnifiedCsvExporter.Encoding.UTF8_BOM);

            MockHttpServletResponse response = new MockHttpServletResponse();
            exporter.export(List.of(entity), response);

            byte[] bytes = response.getContentAsByteArray();
            assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
            assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
            assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        }
    }

    // --- Test entity classes ---

    @Data
    @NoArgsConstructor
    static class SimpleCsvEntity {
        @ExcelColumn(name = "Name", order = 1)
        private String name;

        @ExcelColumn(name = "Value", order = 2)
        private Double value;
    }

    @Data
    @NoArgsConstructor
    static class DateCalEntity {
        @ExcelColumn(name = "Date", order = 1)
        private Date date;

        @ExcelColumn(name = "Cal", order = 2)
        private Calendar cal;
    }

    @Data
    @NoArgsConstructor
    static class NumberEntity {
        @ExcelColumn(name = "Amount", order = 1)
        private Double amount;
    }

    @Data
    @NoArgsConstructor
    static class EnumCsvEntity {
        @ExcelColumn(name = "Status", order = 1)
        private CsvTestStatus status;
    }

    @Data
    @NoArgsConstructor
    static class TemporalCsvEntity {
        @ExcelColumn(name = "Date", order = 1)
        private LocalDate date;

        @ExcelColumn(name = "DateTime", order = 2)
        private LocalDateTime dateTime;

        @ExcelColumn(name = "Time", order = 3)
        private LocalTime time;
    }

    enum CsvTestStatus {
        ACTIVE, INACTIVE
    }
}
