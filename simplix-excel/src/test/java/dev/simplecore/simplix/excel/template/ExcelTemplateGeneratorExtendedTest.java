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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelTemplateGenerator - Extended Coverage")
class ExcelTemplateGeneratorExtendedTest {

    @Nested
    @DisplayName("generateDefaultTemplate with file path")
    class GenerateToPathTests {

        @Test
        @DisplayName("should generate template at specified path")
        void shouldGenerateAtPath() throws IOException {
            Path tempFile = Files.createTempFile("template-test-", ".xlsx");
            ExcelTemplateGenerator.generateDefaultTemplate(tempFile.toString());

            assertThat(Files.exists(tempFile)).isTrue();
            assertThat(Files.size(tempFile)).isGreaterThan(0);

            // Verify content
            try (Workbook wb = new XSSFWorkbook(Files.newInputStream(tempFile))) {
                assertThat(wb.getNumberOfSheets()).isEqualTo(1);
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Data");
            }

            Files.deleteIfExists(tempFile);
        }
    }

    @Nested
    @DisplayName("generateTemplate with options")
    class GenerateWithOptionsTests {

        @Test
        @DisplayName("should generate template with JXLS markers and additional markers")
        void shouldGenerateWithJxlsAndAdditional() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.TemplateOptions options =
                    ExcelTemplateGenerator.TemplateOptions.builder()
                            .useJxlsMarkers(true)
                            .additionalMarkers(List.of("${extra}"))
                            .build();
            ExcelTemplateGenerator.generateTemplate(baos, options);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Sheet sheet = wb.getSheetAt(0);
                assertThat(sheet).isNotNull();
                // JXLS markers at rows 0-3, additional marker at row 4
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("jx:area");
                assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("${extra}");
            }
        }

        @Test
        @DisplayName("should generate template with additional markers")
        void shouldGenerateWithAdditionalMarkers() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExcelTemplateGenerator.TemplateOptions options =
                    ExcelTemplateGenerator.TemplateOptions.builder()
                            .additionalMarkers(List.of("${customMarker1}", "${customMarker2}"))
                            .build();
            ExcelTemplateGenerator.generateTemplate(baos, options);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                Sheet sheet = wb.getSheetAt(0);
                // Additional markers should be added after JXLS markers
                assertThat(sheet.getRow(4)).isNotNull();
                assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("${customMarker1}");
                assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("${customMarker2}");
            }
        }
    }

    @Nested
    @DisplayName("generateTemplateResource")
    class GenerateTemplateResourceTests {

        @Test
        @DisplayName("should generate template resource when not on classpath")
        void shouldGenerateTemplateResource() throws IOException {
            var resource = ExcelTemplateGenerator.generateTemplateResource();
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
        }
    }

    @Nested
    @DisplayName("TemplateOptions builder defaults")
    class TemplateOptionsTests {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaults() {
            ExcelTemplateGenerator.TemplateOptions options =
                    ExcelTemplateGenerator.TemplateOptions.builder().build();

            assertThat(options.getSheetName()).isEqualTo("Data");
            assertThat(options.getDefaultColumnWidth()).isEqualTo(15);
            assertThat(options.isUseJxlsMarkers()).isTrue();
            assertThat(options.isApplyHeaderStyle()).isTrue();
            assertThat(options.getAdditionalMarkers()).isEmpty();
        }
    }
}
