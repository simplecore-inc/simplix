package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AbstractExporter - Extended Coverage")
class AbstractExporterExtendedTest {

    @Nested
    @DisplayName("Field extraction")
    class FieldExtractionTests {

        @Test
        @DisplayName("should extract and sort annotated fields")
        void shouldExtractAnnotatedFields() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            assertThat(exporter.getExportFields()).hasSize(2);
            assertThat(exporter.getExportFields().get(0).getName()).isEqualTo("name");
            assertThat(exporter.getExportFields().get(1).getName()).isEqualTo("value");
        }

        @Test
        @DisplayName("should exclude ignored fields")
        void shouldExcludeIgnoredFields() {
            TestExporter<IgnoredFieldEntity> exporter = new TestExporter<>(IgnoredFieldEntity.class);
            assertThat(exporter.getExportFields()).hasSize(1);
            assertThat(exporter.getExportFields().get(0).getName()).isEqualTo("name");
        }
    }

    @Nested
    @DisplayName("filename and streaming setters")
    class SetterTests {

        @Test
        @DisplayName("should set filename")
        void shouldSetFilename() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            exporter.filename("custom.xlsx");
            assertThat(exporter.getFilenameValue()).isEqualTo("custom.xlsx");
        }

        @Test
        @DisplayName("should set streaming flag")
        void shouldSetStreaming() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            exporter.streaming(true);
            assertThat(exporter.isStreamingValue()).isTrue();
        }
    }

    @Nested
    @DisplayName("configureExcelResponse")
    class ConfigureExcelResponseTests {

        @Test
        @DisplayName("should set Excel content type and headers")
        void shouldSetExcelHeaders() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            exporter.filename("test.xlsx");

            MockHttpServletResponse response = new MockHttpServletResponse();
            exporter.callConfigureExcelResponse(response);

            assertThat(response.getContentType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            assertThat(response.getHeader("Content-Disposition")).contains("test.xlsx");
            assertThat(response.getHeader("Cache-Control")).contains("no-cache");
            assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
            assertThat(response.getHeader("Expires")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("configureCsvResponse")
    class ConfigureCsvResponseTests {

        @Test
        @DisplayName("should set CSV content type and headers")
        void shouldSetCsvHeaders() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            exporter.filename("test.csv");

            MockHttpServletResponse response = new MockHttpServletResponse();
            exporter.callConfigureCsvResponse(response, "UTF-8");

            assertThat(response.getContentType()).isEqualTo("text/csv; charset=UTF-8");
            assertThat(response.getHeader("Content-Disposition")).contains("test.csv");
        }
    }

    @Nested
    @DisplayName("processBatches")
    class ProcessBatchesTests {

        @Test
        @DisplayName("should process items in batches")
        void shouldProcessInBatches() throws IOException {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            List<TestEntity> items = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                items.add(new TestEntity("Item " + i, (double) i));
            }

            AtomicInteger batchCount = new AtomicInteger(0);
            exporter.callProcessBatches(items, 10, batch -> {
                batchCount.incrementAndGet();
                assertThat(batch.size()).isLessThanOrEqualTo(10);
            });

            assertThat(batchCount.get()).isEqualTo(3); // 10 + 10 + 5
        }

        @Test
        @DisplayName("should handle null collection")
        void shouldHandleNullCollection() throws IOException {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            AtomicInteger batchCount = new AtomicInteger(0);
            exporter.callProcessBatches(null, 10, batch -> batchCount.incrementAndGet());
            assertThat(batchCount.get()).isZero();
        }

        @Test
        @DisplayName("should handle empty collection")
        void shouldHandleEmptyCollection() throws IOException {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            AtomicInteger batchCount = new AtomicInteger(0);
            exporter.callProcessBatches(Collections.emptyList(), 10, batch -> batchCount.incrementAndGet());
            assertThat(batchCount.get()).isZero();
        }
    }

    @Nested
    @DisplayName("logExportStart and logExportCompletion")
    class LogTests {

        @Test
        @DisplayName("should log export start for null collection")
        void shouldLogForNullCollection() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            // Should not throw
            exporter.callLogExportStart(null, "Starting export of {} items of type {}");
        }

        @Test
        @DisplayName("should log export start for non-empty collection")
        void shouldLogForNonEmptyCollection() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            exporter.callLogExportStart(
                    List.of(new TestEntity("Test", 1.0)),
                    "Starting export of {} items of type {}");
        }

        @Test
        @DisplayName("should log export completion")
        void shouldLogCompletion() {
            TestExporter<TestEntity> exporter = new TestExporter<>(TestEntity.class);
            exporter.callLogExportCompletion(java.time.Instant.now().minusSeconds(5));
        }
    }

    // --- Helper: Concrete test subclass to access protected methods ---

    static class TestExporter<T> extends AbstractExporter<T> {
        protected TestExporter(Class<T> dataClass) {
            super(dataClass);
        }

        public List<java.lang.reflect.Field> getExportFields() {
            return exportFields;
        }

        public String getFilenameValue() {
            return filename;
        }

        public boolean isStreamingValue() {
            return useStreaming;
        }

        public void callConfigureExcelResponse(HttpServletResponse response) {
            configureExcelResponse(response);
        }

        public void callConfigureCsvResponse(HttpServletResponse response, String charset) {
            configureCsvResponse(response, charset);
        }

        public void callProcessBatches(java.util.Collection<T> items, int batchSize,
                                       BatchProcessor<T> processor) throws IOException {
            processBatches(items, batchSize, processor);
        }

        public void callLogExportStart(java.util.Collection<T> items, String format) {
            logExportStart(items, format);
        }

        public void callLogExportCompletion(java.time.Instant start) {
            logExportCompletion(start);
        }
    }

    // --- Test entities ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestEntity {
        @ExcelColumn(name = "Name", order = 1)
        private String name;

        @ExcelColumn(name = "Value", order = 2)
        private Double value;
    }

    @Data
    @NoArgsConstructor
    static class IgnoredFieldEntity {
        @ExcelColumn(name = "Name", order = 1)
        private String name;

        @ExcelColumn(name = "Secret", order = 2, ignore = true)
        private String secret;
    }
}
