package dev.simplecore.simplix.excel.style;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelStyleManager")
class ExcelStyleManagerTest {

    private Workbook workbook;
    private ExcelStyleManager styleManager;

    @BeforeEach
    void setUp() {
        workbook = new XSSFWorkbook();
        styleManager = new ExcelStyleManager(workbook);
    }

    @AfterEach
    void tearDown() throws IOException {
        workbook.close();
    }

    @Nested
    @DisplayName("createHeaderStyle")
    class CreateHeaderStyleTests {

        @Test
        @DisplayName("should create header style with center alignment")
        void shouldCreateWithCenterAlignment() {
            CellStyle style = styleManager.createHeaderStyle();
            assertThat(style.getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
        }

        @Test
        @DisplayName("should create header style with center vertical alignment")
        void shouldCreateWithCenterVerticalAlignment() {
            CellStyle style = styleManager.createHeaderStyle();
            assertThat(style.getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
        }

        @Test
        @DisplayName("should create header style with thin borders")
        void shouldCreateWithThinBorders() {
            CellStyle style = styleManager.createHeaderStyle();
            assertThat(style.getBorderTop()).isEqualTo(BorderStyle.THIN);
            assertThat(style.getBorderRight()).isEqualTo(BorderStyle.THIN);
            assertThat(style.getBorderBottom()).isEqualTo(BorderStyle.THIN);
            assertThat(style.getBorderLeft()).isEqualTo(BorderStyle.THIN);
        }

        @Test
        @DisplayName("should create header style with grey background")
        void shouldCreateWithGreyBackground() {
            CellStyle style = styleManager.createHeaderStyle();
            assertThat(style.getFillForegroundColor())
                    .isEqualTo(IndexedColors.GREY_25_PERCENT.getIndex());
            assertThat(style.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
        }

        @Test
        @DisplayName("should cache header style")
        void shouldCacheHeaderStyle() {
            CellStyle first = styleManager.createHeaderStyle();
            CellStyle second = styleManager.createHeaderStyle();
            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTests {

        @Test
        @DisplayName("should clear style cache")
        void shouldClearCache() {
            CellStyle first = styleManager.createHeaderStyle();
            styleManager.clearCache();
            CellStyle second = styleManager.createHeaderStyle();
            // After clearing, a new style should be created
            assertThat(first).isNotSameAs(second);
        }
    }
}
