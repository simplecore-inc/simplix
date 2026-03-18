package dev.simplecore.simplix.excel.impl.importer;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StandardExcelImporter - Extended Coverage")
class StandardExcelImporterExtendedTest {

    @Nested
    @DisplayName("Import with various numeric types")
    class NumericTypeTests {

        @Test
        @DisplayName("should import Integer field from numeric cell")
        void shouldImportIntegerField() throws IOException {
            ByteArrayOutputStream outputStream = createExcelWithNumericRow(42.0);
            MockMultipartFile file = createMockFile(outputStream);

            StandardExcelImporter<IntEntity> importer = new StandardExcelImporter<>(IntEntity.class);
            List<IntEntity> result = importer.importFromExcel(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("should import Float field from numeric cell")
        void shouldImportFloatField() throws IOException {
            ByteArrayOutputStream outputStream = createExcelWithNumericRow(3.14);
            MockMultipartFile file = createMockFile(outputStream);

            StandardExcelImporter<FloatEntity> importer = new StandardExcelImporter<>(FloatEntity.class);
            List<FloatEntity> result = importer.importFromExcel(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isCloseTo(3.14f, org.assertj.core.api.Assertions.within(0.01f));
        }

        @Test
        @DisplayName("should import BigDecimal field from numeric cell")
        void shouldImportBigDecimalField() throws IOException {
            ByteArrayOutputStream outputStream = createExcelWithNumericRow(123.45);
            MockMultipartFile file = createMockFile(outputStream);

            StandardExcelImporter<BigDecimalEntity> importer = new StandardExcelImporter<>(BigDecimalEntity.class);
            List<BigDecimalEntity> result = importer.importFromExcel(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Import with boolean cells")
    class BooleanCellTests {

        @Test
        @DisplayName("should import boolean cell value")
        void shouldImportBooleanCell() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Data");
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Active");

                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue(true);

                workbook.write(outputStream);
            }

            MockMultipartFile file = createMockFile(outputStream);
            StandardExcelImporter<BooleanEntity> importer = new StandardExcelImporter<>(BooleanEntity.class);
            List<BooleanEntity> result = importer.importFromExcel(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Import with blank cells")
    class BlankCellTests {

        @Test
        @DisplayName("should handle blank cells gracefully")
        void shouldHandleBlankCells() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Data");
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Value");

                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setBlank();

                workbook.write(outputStream);
            }

            MockMultipartFile file = createMockFile(outputStream);
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            List<StringEntity> result = importer.importFromExcel(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isNull();
        }
    }

    @Nested
    @DisplayName("Import from CSV with various types")
    class CsvTypeTests {

        @Test
        @DisplayName("should import Integer from CSV")
        void shouldImportInteger() throws IOException {
            String csv = "Value\n42";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<IntEntity> importer = new StandardExcelImporter<>(IntEntity.class);
            List<IntEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("should import Float from CSV")
        void shouldImportFloat() throws IOException {
            String csv = "Value\n3.14";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<FloatEntity> importer = new StandardExcelImporter<>(FloatEntity.class);
            List<FloatEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isCloseTo(3.14f, org.assertj.core.api.Assertions.within(0.01f));
        }

        @Test
        @DisplayName("should import BigDecimal from CSV")
        void shouldImportBigDecimal() throws IOException {
            String csv = "Value\n123.45";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<BigDecimalEntity> importer = new StandardExcelImporter<>(BigDecimalEntity.class);
            List<BigDecimalEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualByComparingTo(new BigDecimal("123.45"));
        }

        @Test
        @DisplayName("should import Boolean from CSV as Y")
        void shouldImportBooleanY() throws IOException {
            String csv = "Active\nY";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<BooleanEntity> importer = new StandardExcelImporter<>(BooleanEntity.class);
            List<BooleanEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getActive()).isTrue();
        }

        @Test
        @DisplayName("should import Duration from CSV")
        void shouldImportDuration() throws IOException {
            String csv = "Value\nPT2H30M";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<DurationEntity> importer = new StandardExcelImporter<>(DurationEntity.class);
            List<DurationEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(Duration.ofHours(2).plusMinutes(30));
        }

        @Test
        @DisplayName("should import Period from CSV")
        void shouldImportPeriod() throws IOException {
            String csv = "Value\nP1Y6M";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<PeriodEntity> importer = new StandardExcelImporter<>(PeriodEntity.class);
            List<PeriodEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(Period.of(1, 6, 0));
        }

        @Test
        @DisplayName("should import MonthDay from CSV")
        void shouldImportMonthDay() throws IOException {
            String csv = "Value\n--06-15";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<MonthDayEntity> importer = new StandardExcelImporter<>(MonthDayEntity.class);
            List<MonthDayEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(MonthDay.of(6, 15));
        }

        @Test
        @DisplayName("should import Year from CSV as number")
        void shouldImportYear() throws IOException {
            String csv = "Value\n2024";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<YearEntity> importer = new StandardExcelImporter<>(YearEntity.class);
            List<YearEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(Year.of(2024));
        }

        @Test
        @DisplayName("should import LocalTime from CSV")
        void shouldImportLocalTime() throws IOException {
            String csv = "Value\n10:30:45";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<LocalTimeEntity> importer = new StandardExcelImporter<>(LocalTimeEntity.class);
            List<LocalTimeEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo(LocalTime.of(10, 30, 45));
        }
    }

    @Nested
    @DisplayName("Import settings")
    class SettingsTests {

        @Test
        @DisplayName("should skip header when set to true")
        void shouldSkipHeader() throws IOException {
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            assertThat(importer.isSkipHeader()).isTrue();
            importer.setSkipHeader(false);
            assertThat(importer.isSkipHeader()).isFalse();
        }

        @Test
        @DisplayName("should use custom sheet index")
        void shouldUseCustomSheetIndex() {
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            assertThat(importer.getSheetIndex()).isZero();
            importer.setSheetIndex(1);
            assertThat(importer.getSheetIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("should use custom date format")
        void shouldUseCustomDateFormat() {
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            importer.setDateFormat("dd/MM/yyyy");
            assertThat(importer.getDateFormat()).isEqualTo("dd/MM/yyyy");
        }

        @Test
        @DisplayName("should use custom datetime format")
        void shouldUseCustomDateTimeFormat() {
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            importer.setDateTimeFormat("dd/MM/yyyy HH:mm");
            assertThat(importer.getDateTimeFormat()).isEqualTo("dd/MM/yyyy HH:mm");
        }

        @Test
        @DisplayName("should import without skipping header")
        void shouldImportWithoutSkippingHeader() throws IOException {
            String csv = "Value1\nValue2";
            MockMultipartFile file = new MockMultipartFile("data.csv", "data.csv", "text/csv", csv.getBytes());

            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            importer.setSkipHeader(false);
            List<StringEntity> result = importer.importFromCsv(file);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should handle column mapping with unknown field name")
        void shouldHandleUnknownFieldMapping() {
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            Map<Integer, String> mapping = new HashMap<>();
            mapping.put(0, "nonexistentField");
            importer.setColumnMapping(mapping);
            // Should not throw; unknown field is simply not mapped
        }
    }

    @Nested
    @DisplayName("Import from Excel with null rows")
    class NullRowTests {

        @Test
        @DisplayName("should skip null rows in Excel")
        void shouldSkipNullRows() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Data");
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Value");

                // Row 1 is intentionally left as null (not created)
                Row dataRow = sheet.createRow(2);
                dataRow.createCell(0).setCellValue("Test");

                workbook.write(outputStream);
            }

            MockMultipartFile file = createMockFile(outputStream);
            StandardExcelImporter<StringEntity> importer = new StandardExcelImporter<>(StringEntity.class);
            List<StringEntity> result = importer.importFromExcel(file);

            // Row 1 is null so skipped, row 2 should be imported
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValue()).isEqualTo("Test");
        }
    }

    @Nested
    @DisplayName("Import with date-formatted numeric cells")
    class DateFormattedCellTests {

        @Test
        @DisplayName("should import LocalDate from date-formatted numeric cell")
        void shouldImportLocalDateFromDateCell() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Data");
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Date");

                Row dataRow = sheet.createRow(1);
                Cell dateCell = dataRow.createCell(0);
                CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat((short) 14);
                dateCell.setCellStyle(dateStyle);
                dateCell.setCellValue(LocalDate.of(2024, 6, 15));

                workbook.write(outputStream);
            }

            MockMultipartFile file = createMockFile(outputStream);
            StandardExcelImporter<DateEntity> importer = new StandardExcelImporter<>(DateEntity.class);
            List<DateEntity> result = importer.importFromExcel(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        }
    }

    // --- Helper methods ---

    private ByteArrayOutputStream createExcelWithNumericRow(double value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Value");

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(value);

            workbook.write(outputStream);
        }
        return outputStream;
    }

    private MockMultipartFile createMockFile(ByteArrayOutputStream outputStream) {
        return new MockMultipartFile(
                "data.xlsx", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
        );
    }

    // --- Test entities ---

    @Data @NoArgsConstructor
    static class StringEntity {
        @ExcelColumn(name = "Value", order = 1)
        private String value;
    }

    @Data @NoArgsConstructor
    static class IntEntity {
        @ExcelColumn(name = "Value", order = 1)
        private Integer value;
    }

    @Data @NoArgsConstructor
    static class FloatEntity {
        @ExcelColumn(name = "Value", order = 1)
        private Float value;
    }

    @Data @NoArgsConstructor
    static class BigDecimalEntity {
        @ExcelColumn(name = "Value", order = 1)
        private BigDecimal value;
    }

    @Data @NoArgsConstructor
    static class BooleanEntity {
        @ExcelColumn(name = "Active", order = 1)
        private Boolean active;
    }

    @Data @NoArgsConstructor
    static class DateEntity {
        @ExcelColumn(name = "Date", order = 1)
        private LocalDate date;
    }

    @Data @NoArgsConstructor
    static class DurationEntity {
        @ExcelColumn(name = "Value", order = 1)
        private Duration value;
    }

    @Data @NoArgsConstructor
    static class PeriodEntity {
        @ExcelColumn(name = "Value", order = 1)
        private Period value;
    }

    @Data @NoArgsConstructor
    static class MonthDayEntity {
        @ExcelColumn(name = "Value", order = 1)
        private MonthDay value;
    }

    @Data @NoArgsConstructor
    static class YearEntity {
        @ExcelColumn(name = "Value", order = 1)
        private Year value;
    }

    @Data @NoArgsConstructor
    static class YearMonthEntity {
        @ExcelColumn(name = "Value", order = 1)
        private YearMonth value;
    }

    @Data @NoArgsConstructor
    static class LocalTimeEntity {
        @ExcelColumn(name = "Value", order = 1)
        private LocalTime value;
    }
}
