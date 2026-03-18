package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StandardExcelExporter - Extended Coverage")
class StandardExcelExporterExtendedTest {

    static {
        org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.001);
    }

    @Nested
    @DisplayName("Various data type cell values")
    class DataTypeCellValueTests {

        @Test
        @DisplayName("should export Boolean field correctly")
        void shouldExportBoolean() throws IOException {
            AllTypesEntity entity = new AllTypesEntity();
            entity.setBoolField(true);
            entity.setStringField("test");

            StandardExcelExporter<AllTypesEntity> exporter = new StandardExcelExporter<>(AllTypesEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("test");
                assertThat(row.getCell(1).getBooleanCellValue()).isTrue();
            }
        }

        @Test
        @DisplayName("should export LocalTime as string")
        void shouldExportLocalTime() throws IOException {
            TimeEntity entity = new TimeEntity();
            entity.setTime(LocalTime.of(14, 30, 0));

            StandardExcelExporter<TimeEntity> exporter = new StandardExcelExporter<>(TimeEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).contains("14:30");
            }
        }

        @Test
        @DisplayName("should export OffsetDateTime correctly")
        void shouldExportOffsetDateTime() throws IOException {
            OffsetDateTimeEntity entity = new OffsetDateTimeEntity();
            entity.setOdt(OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC));

            StandardExcelExporter<OffsetDateTimeEntity> exporter = new StandardExcelExporter<>(OffsetDateTimeEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            }
        }

        @Test
        @DisplayName("should export ZonedDateTime correctly")
        void shouldExportZonedDateTime() throws IOException {
            ZonedDateTimeEntity entity = new ZonedDateTimeEntity();
            entity.setZdt(ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC")));

            StandardExcelExporter<ZonedDateTimeEntity> exporter = new StandardExcelExporter<>(ZonedDateTimeEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            }
        }

        @Test
        @DisplayName("should export Instant correctly")
        void shouldExportInstant() throws IOException {
            InstantEntity entity = new InstantEntity();
            entity.setCreatedAt(Instant.ofEpochMilli(1705276800000L));

            StandardExcelExporter<InstantEntity> exporter = new StandardExcelExporter<>(InstantEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            }
        }

        @Test
        @DisplayName("should export Year correctly")
        void shouldExportYear() throws IOException {
            YearEntity entity = new YearEntity();
            entity.setYear(Year.of(2024));

            StandardExcelExporter<YearEntity> exporter = new StandardExcelExporter<>(YearEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(2024.0);
            }
        }

        @Test
        @DisplayName("should export YearMonth as string")
        void shouldExportYearMonth() throws IOException {
            YearMonthEntity entity = new YearMonthEntity();
            entity.setYearMonth(YearMonth.of(2024, 6));

            StandardExcelExporter<YearMonthEntity> exporter = new StandardExcelExporter<>(YearMonthEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2024-06");
            }
        }

        @Test
        @DisplayName("should export MonthDay as string")
        void shouldExportMonthDay() throws IOException {
            MonthDayEntity entity = new MonthDayEntity();
            entity.setMonthDay(MonthDay.of(6, 15));

            StandardExcelExporter<MonthDayEntity> exporter = new StandardExcelExporter<>(MonthDayEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("--06-15");
            }
        }

        @Test
        @DisplayName("should export Duration as string")
        void shouldExportDuration() throws IOException {
            DurationEntity entity = new DurationEntity();
            entity.setDuration(Duration.ofHours(2).plusMinutes(30));

            StandardExcelExporter<DurationEntity> exporter = new StandardExcelExporter<>(DurationEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("PT2H30M");
            }
        }

        @Test
        @DisplayName("should export Period as string")
        void shouldExportPeriod() throws IOException {
            PeriodEntity entity = new PeriodEntity();
            entity.setPeriod(Period.of(1, 6, 15));

            StandardExcelExporter<PeriodEntity> exporter = new StandardExcelExporter<>(PeriodEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("P1Y6M15D");
            }
        }

        @Test
        @DisplayName("should export Date correctly")
        void shouldExportLegacyDate() throws IOException {
            LegacyDateEntity entity = new LegacyDateEntity();
            entity.setDate(new Date(0));

            StandardExcelExporter<LegacyDateEntity> exporter = new StandardExcelExporter<>(LegacyDateEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            }
        }

        @Test
        @DisplayName("should export Enum value")
        void shouldExportEnum() throws IOException {
            EnumEntity entity = new EnumEntity();
            entity.setStatus(TestStatus.ACTIVE);

            StandardExcelExporter<EnumEntity> exporter = new StandardExcelExporter<>(EnumEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("ACTIVE");
            }
        }

        @Test
        @DisplayName("should export Collection field")
        void shouldExportCollection() throws IOException {
            CollectionEntity entity = new CollectionEntity();
            entity.setTags(List.of("java", "spring", "excel"));

            StandardExcelExporter<CollectionEntity> exporter = new StandardExcelExporter<>(CollectionEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                String value = row.getCell(0).getStringCellValue();
                assertThat(value).contains("java").contains("spring").contains("excel");
            }
        }

        @Test
        @DisplayName("should export empty Collection as empty string")
        void shouldExportEmptyCollection() throws IOException {
            CollectionEntity entity = new CollectionEntity();
            entity.setTags(Collections.emptyList());

            StandardExcelExporter<CollectionEntity> exporter = new StandardExcelExporter<>(CollectionEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEmpty();
            }
        }

        @Test
        @DisplayName("should export object using getId method")
        void shouldExportObjectWithGetId() throws IOException {
            ObjectFieldEntity entity = new ObjectFieldEntity();
            entity.setRef(new IdHolder(42L));

            StandardExcelExporter<ObjectFieldEntity> exporter = new StandardExcelExporter<>(ObjectFieldEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("42");
            }
        }

        @Test
        @DisplayName("should export object using getName when no getId")
        void shouldExportObjectWithGetName() throws IOException {
            NameFieldEntity entity = new NameFieldEntity();
            entity.setRef(new NameHolder("Test Name"));

            StandardExcelExporter<NameFieldEntity> exporter = new StandardExcelExporter<>(NameFieldEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Test Name");
            }
        }

        @Test
        @DisplayName("should export null field as blank cell")
        void shouldExportNullAsBlank() throws IOException {
            AllTypesEntity entity = new AllTypesEntity();
            entity.setStringField(null);
            entity.setBoolField(null);

            StandardExcelExporter<AllTypesEntity> exporter = new StandardExcelExporter<>(AllTypesEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            }
        }
    }

    @Nested
    @DisplayName("Streaming mode with data provider")
    class StreamingDataProviderTests {

        @Test
        @DisplayName("should export using data provider in streaming mode")
        void shouldExportWithDataProvider() throws IOException {
            List<SimpleEntity> allData = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                SimpleEntity e = new SimpleEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                allData.add(e);
            }

            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.streaming(true)
                    .pageSize(10)
                    .dataProvider(pageRequest -> {
                        int start = (int) pageRequest.getOffset();
                        int end = Math.min(start + pageRequest.getPageSize(), allData.size());
                        List<SimpleEntity> content = start < allData.size()
                                ? allData.subList(start, end)
                                : List.of();
                        return new PageImpl<>(content, pageRequest, allData.size());
                    });

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(null, baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Sheet sheet = wb.getSheetAt(0);
                // Header + 50 data rows
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(51);
            }
        }

        @Test
        @DisplayName("should export streaming from collection for large datasets")
        void shouldExportStreamingFromCollection() throws IOException {
            List<SimpleEntity> items = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                SimpleEntity e = new SimpleEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                items.add(e);
            }

            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.streaming(true)
                    .pageSize(500);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(items, baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(2001);
            }
        }

        @Test
        @DisplayName("should handle empty collection in streaming mode")
        void shouldHandleEmptyCollectionStreaming() throws IOException {
            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.streaming(true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(Collections.emptyList(), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("should handle null collection in streaming mode")
        void shouldHandleNullCollectionStreaming() throws IOException {
            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.streaming(true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(null, baos);

            assertThat(baos.size()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("HTTP response streaming")
    class HttpResponseStreamingTests {

        @Test
        @DisplayName("should export streaming with data provider to HTTP response")
        void shouldExportStreamingToResponse() throws IOException {
            List<SimpleEntity> allData = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                SimpleEntity e = new SimpleEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                allData.add(e);
            }

            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.streaming(true)
                    .dataProvider(pageRequest -> {
                        int start = (int) pageRequest.getOffset();
                        int end = Math.min(start + pageRequest.getPageSize(), allData.size());
                        List<SimpleEntity> content = start < allData.size()
                                ? allData.subList(start, end)
                                : List.of();
                        return new PageImpl<>(content, pageRequest, allData.size());
                    });

            MockHttpServletResponse response = new MockHttpServletResponse();
            exporter.export(null, response);

            assertThat(response.getContentAsByteArray().length).isGreaterThan(0);
            assertThat(response.getContentType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @Test
        @DisplayName("should export streaming from collection to HTTP response")
        void shouldExportStreamingCollectionToResponse() throws IOException {
            List<SimpleEntity> items = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                SimpleEntity e = new SimpleEntity();
                e.setName("User " + i);
                e.setValue((double) i);
                items.add(e);
            }

            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.streaming(true);

            MockHttpServletResponse response = new MockHttpServletResponse();
            exporter.export(items, response);

            assertThat(response.getContentAsByteArray().length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Auto-size columns")
    class AutoSizeColumnsTests {

        @Test
        @DisplayName("should export with auto-size disabled (default)")
        void shouldExportWithAutoSizeDisabled() throws IOException {
            SimpleEntity entity = new SimpleEntity();
            entity.setName("Test");
            entity.setValue(42.0);

            StandardExcelExporter<SimpleEntity> exporter = new StandardExcelExporter<>(SimpleEntity.class);
            exporter.autoSizeColumns(false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            assertThat(baos.size()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("OffsetTime type")
    class OffsetTimeTests {

        @Test
        @DisplayName("should export OffsetTime as string")
        void shouldExportOffsetTime() throws IOException {
            OffsetTimeEntity entity = new OffsetTimeEntity();
            entity.setTime(OffsetTime.of(14, 30, 0, 0, ZoneOffset.UTC));

            StandardExcelExporter<OffsetTimeEntity> exporter = new StandardExcelExporter<>(OffsetTimeEntity.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.export(List.of(entity), baos);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Row row = wb.getSheetAt(0).getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).contains("14:30");
            }
        }
    }

    // --- Test entity classes ---

    @Data
    @NoArgsConstructor
    static class AllTypesEntity {
        @ExcelColumn(name = "String", order = 1)
        private String stringField;

        @ExcelColumn(name = "Boolean", order = 2)
        private Boolean boolField;
    }

    @Data
    @NoArgsConstructor
    static class SimpleEntity {
        @ExcelColumn(name = "Name", order = 1)
        private String name;

        @ExcelColumn(name = "Value", order = 2)
        private Double value;
    }

    @Data
    @NoArgsConstructor
    static class TimeEntity {
        @ExcelColumn(name = "Time", order = 1)
        private LocalTime time;
    }

    @Data
    @NoArgsConstructor
    static class OffsetDateTimeEntity {
        @ExcelColumn(name = "ODT", order = 1)
        private OffsetDateTime odt;
    }

    @Data
    @NoArgsConstructor
    static class ZonedDateTimeEntity {
        @ExcelColumn(name = "ZDT", order = 1)
        private ZonedDateTime zdt;
    }

    @Data
    @NoArgsConstructor
    static class InstantEntity {
        @ExcelColumn(name = "Created", order = 1)
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    static class YearEntity {
        @ExcelColumn(name = "Year", order = 1)
        private Year year;
    }

    @Data
    @NoArgsConstructor
    static class YearMonthEntity {
        @ExcelColumn(name = "YearMonth", order = 1)
        private YearMonth yearMonth;
    }

    @Data
    @NoArgsConstructor
    static class MonthDayEntity {
        @ExcelColumn(name = "MonthDay", order = 1)
        private MonthDay monthDay;
    }

    @Data
    @NoArgsConstructor
    static class DurationEntity {
        @ExcelColumn(name = "Duration", order = 1)
        private Duration duration;
    }

    @Data
    @NoArgsConstructor
    static class PeriodEntity {
        @ExcelColumn(name = "Period", order = 1)
        private Period period;
    }

    @Data
    @NoArgsConstructor
    static class LegacyDateEntity {
        @ExcelColumn(name = "Date", order = 1)
        private Date date;
    }

    @Data
    @NoArgsConstructor
    static class EnumEntity {
        @ExcelColumn(name = "Status", order = 1)
        private TestStatus status;
    }

    @Data
    @NoArgsConstructor
    static class CollectionEntity {
        @ExcelColumn(name = "Tags", order = 1)
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    static class ObjectFieldEntity {
        @ExcelColumn(name = "Ref", order = 1)
        private IdHolder ref;
    }

    @Data
    @NoArgsConstructor
    static class NameFieldEntity {
        @ExcelColumn(name = "Ref", order = 1)
        private NameHolder ref;
    }

    @Data
    @NoArgsConstructor
    static class OffsetTimeEntity {
        @ExcelColumn(name = "Time", order = 1)
        private OffsetTime time;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class IdHolder {
        private Long id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class NameHolder {
        private String name;
    }

    enum TestStatus {
        ACTIVE, INACTIVE
    }
}
