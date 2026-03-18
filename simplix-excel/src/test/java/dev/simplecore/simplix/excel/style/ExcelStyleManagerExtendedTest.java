package dev.simplecore.simplix.excel.style;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelStyleManager - Extended Coverage")
class ExcelStyleManagerExtendedTest {

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
    @DisplayName("createDataStyle with various value types")
    class CreateDataStyleTests {

        @Test
        @DisplayName("should create style for String value with custom font color")
        void shouldCreateStyleForStringWithFontColor() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.RED, false, false,
                    HorizontalAlignment.LEFT, true, "");

            CellStyle style = styleManager.createDataStyle("text", column);
            assertThat(style).isNotNull();
            assertThat(style.getAlignment()).isEqualTo(HorizontalAlignment.LEFT);
        }

        @Test
        @DisplayName("should create style for Number value with format pattern")
        void shouldCreateStyleForNumber() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, true, false,
                    HorizontalAlignment.RIGHT, false, "#,##0.00");

            CellStyle style = styleManager.createDataStyle(42.5, column);
            assertThat(style).isNotNull();
        }

        @Test
        @DisplayName("should create style for Date value with format pattern")
        void shouldCreateStyleForDate() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, false, false,
                    HorizontalAlignment.CENTER, false, "yyyy-MM-dd");

            CellStyle style = styleManager.createDataStyle(new Date(), column);
            assertThat(style).isNotNull();
        }

        @Test
        @DisplayName("should create style for Temporal value with format pattern")
        void shouldCreateStyleForTemporal() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, false, false,
                    HorizontalAlignment.LEFT, false, "yyyy-MM-dd");

            CellStyle style = styleManager.createDataStyle(LocalDate.now(), column);
            assertThat(style).isNotNull();
        }

        @Test
        @DisplayName("should create style with background color")
        void shouldCreateStyleWithBackgroundColor() {
            ExcelColumn column = createColumn(IndexedColors.YELLOW, IndexedColors.AUTOMATIC, false, false,
                    HorizontalAlignment.LEFT, false, "");

            CellStyle style = styleManager.createDataStyle("text", column);
            assertThat(style).isNotNull();
            assertThat(style.getFillForegroundColor()).isEqualTo(IndexedColors.YELLOW.getIndex());
        }

        @Test
        @DisplayName("should create style with italic font")
        void shouldCreateStyleWithItalicFont() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, false, true,
                    HorizontalAlignment.LEFT, false, "");

            CellStyle style = styleManager.createDataStyle("text", column);
            assertThat(style).isNotNull();
        }

        @Test
        @DisplayName("should cache styles with same key")
        void shouldCacheStyles() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, false, false,
                    HorizontalAlignment.LEFT, false, "");

            CellStyle first = styleManager.createDataStyle("text1", column);
            CellStyle second = styleManager.createDataStyle("text2", column);
            // Same value type (String) with same annotation config = same cache key
            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("should create different styles for different value types")
        void shouldCreateDifferentStylesForDifferentTypes() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, false, false,
                    HorizontalAlignment.LEFT, false, "");

            CellStyle stringStyle = styleManager.createDataStyle("text", column);
            CellStyle numberStyle = styleManager.createDataStyle(42, column);
            // Different value types should produce different cache keys
            assertThat(stringStyle).isNotSameAs(numberStyle);
        }

        @Test
        @DisplayName("should handle null value for style key")
        void shouldHandleNullValue() {
            ExcelColumn column = createColumn(IndexedColors.AUTOMATIC, IndexedColors.AUTOMATIC, false, false,
                    HorizontalAlignment.LEFT, false, "");

            CellStyle style = styleManager.createDataStyle(null, column);
            assertThat(style).isNotNull();
        }
    }

    // Helper to create ExcelColumn annotation proxy
    private ExcelColumn createColumn(IndexedColors bgColor, IndexedColors fontColor,
                                     boolean bold, boolean italic,
                                     HorizontalAlignment alignment,
                                     boolean wrapText, String format) {
        return new ExcelColumn() {
            @Override public Class<? extends Annotation> annotationType() { return ExcelColumn.class; }
            @Override public String name() { return "Test"; }
            @Override public int order() { return 1; }
            @Override public boolean ignore() { return false; }
            @Override public int width() { return 15; }
            @Override public String fontName() { return "Arial"; }
            @Override public short fontSize() { return 10; }
            @Override public boolean bold() { return bold; }
            @Override public boolean italic() { return italic; }
            @Override public HorizontalAlignment alignment() { return alignment; }
            @Override public IndexedColors backgroundColor() { return bgColor; }
            @Override public IndexedColors fontColor() { return fontColor; }
            @Override public boolean wrapText() { return wrapText; }
            @Override public String format() { return format; }
        };
    }
}
