package dev.simplecore.simplix.excel.template;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelTemplateGenerator")
class ExcelTemplateGeneratorTest {

    @Nested
    @DisplayName("generateDefaultTemplate to OutputStream")
    class GenerateToOutputStreamTests {

        @Test
        @DisplayName("should generate valid Excel template")
        void shouldGenerateValidTemplate() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.generateDefaultTemplate(baos);

            assertThat(baos.size()).isGreaterThan(0);

            // Verify it is a valid Excel file
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Data");
            }
        }

        @Test
        @DisplayName("should contain JXLS area marker")
        void shouldContainJxlsMarkers() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.generateDefaultTemplate(baos);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);
                String areaValue = sheet.getRow(0).getCell(0).getStringCellValue();
                assertThat(areaValue).contains("jx:area");
            }
        }
    }

    @Nested
    @DisplayName("generateDefaultTemplate to file")
    class GenerateToFileTests {

        @Test
        @DisplayName("should generate template file")
        void shouldGenerateTemplateFile() throws IOException {
            String path = ExcelTemplateGenerator.generateDefaultTemplate();
            assertThat(path).isNotEmpty();

            Path file = Path.of(path);
            assertThat(Files.exists(file)).isTrue();
            assertThat(Files.size(file)).isGreaterThan(0);

            // Cleanup
            Files.deleteIfExists(file);
        }
    }

    @Nested
    @DisplayName("generateTemplate with options")
    class GenerateWithOptionsTests {

        @Test
        @DisplayName("should generate template with default options")
        void shouldGenerateWithDefaultOptions() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.generateTemplate(baos);

            assertThat(baos.size()).isGreaterThan(0);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Data");
            }
        }

        @Test
        @DisplayName("should generate template with custom sheet name")
        void shouldGenerateWithCustomSheetName() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.TemplateOptions options =
                    ExcelTemplateGenerator.TemplateOptions.builder()
                            .sheetName("Custom")
                            .build();
            ExcelTemplateGenerator.generateTemplate(baos, options);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Custom");
            }
        }

        @Test
        @DisplayName("should generate template with custom column width")
        void shouldGenerateWithCustomColumnWidth() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.TemplateOptions options =
                    ExcelTemplateGenerator.TemplateOptions.builder()
                            .defaultColumnWidth(20)
                            .build();
            ExcelTemplateGenerator.generateTemplate(baos, options);

            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet).isNotNull();
                // Column width is set to 20 * 256
                assertThat(sheet.getColumnWidth(0)).isEqualTo(20 * 256);
            }
        }
    }
}
