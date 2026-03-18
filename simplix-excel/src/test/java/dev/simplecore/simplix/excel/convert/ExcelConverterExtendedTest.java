package dev.simplecore.simplix.excel.convert;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelConverter - Extended Coverage")
class ExcelConverterExtendedTest {

    @BeforeEach
    void setUp() {
        ExcelConverter.clearCaches();
    }

    @AfterEach
    void tearDown() {
        ExcelConverter.clearCaches();
    }

    @Nested
    @DisplayName("configure")
    class ConfigureTests {

        @Test
        @DisplayName("should configure with properties and disable field cache")
        void shouldConfigureWithDisabledFieldCache() {
            SimplixExcelProperties properties = new SimplixExcelProperties();
            properties.getExport().setPageSize(500);
            properties.getCache().setFieldCacheEnabled(false);
            properties.getCache().setColumnCacheEnabled(true);

            ExcelConverter.configure(properties);

            // After configuring with disabled field cache, fields should still work
            List<String> titles = ExcelConverter.extractColumnTitles(TestEntity.class);
            assertThat(titles).containsExactly("Name", "Value");
        }

        @Test
        @DisplayName("should configure with properties and disable column cache")
        void shouldConfigureWithDisabledColumnCache() {
            SimplixExcelProperties properties = new SimplixExcelProperties();
            properties.getExport().setPageSize(500);
            properties.getCache().setFieldCacheEnabled(true);
            properties.getCache().setColumnCacheEnabled(false);

            ExcelConverter.configure(properties);

            List<String> titles = ExcelConverter.extractColumnTitles(TestEntity.class);
            assertThat(titles).containsExactly("Name", "Value");
        }
    }

    @Nested
    @DisplayName("convertToRowData with batch processing")
    class BatchProcessingTests {

        @Test
        @DisplayName("should process data in batches when exceeding batch size")
        void shouldProcessInBatches() {
            // Configure small batch size
            SimplixExcelProperties properties = new SimplixExcelProperties();
            properties.getExport().setPageSize(5);
            properties.getCache().setFieldCacheEnabled(true);
            properties.getCache().setColumnCacheEnabled(true);
            ExcelConverter.configure(properties);

            List<TestEntity> items = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                TestEntity entity = new TestEntity();
                entity.name = "Item " + i;
                entity.value = i;
                items.add(entity);
            }

            List<List<String>> rows = ExcelConverter.convertToRowData(items, TestEntity.class);
            assertThat(rows).hasSize(12);
            assertThat(rows.get(0)).containsExactly("Item 0", "0");
            assertThat(rows.get(11)).containsExactly("Item 11", "11");
        }
    }

    @Nested
    @DisplayName("prepareDataModel with empty collection")
    class PrepareDataModelTests {

        @Test
        @DisplayName("should create data model with empty rows for null items")
        void shouldCreateModelWithEmptyRows() {
            Map<String, Object> model = ExcelConverter.prepareDataModel(null, TestEntity.class);
            assertThat(model).containsKeys("headers", "rows");
            assertThat((List<?>) model.get("rows")).isEmpty();
        }
    }

    // Test entity
    static class TestEntity {
        @ExcelColumn(name = "Name", order = 1)
        String name;

        @ExcelColumn(name = "Value", order = 2)
        int value;
    }
}
