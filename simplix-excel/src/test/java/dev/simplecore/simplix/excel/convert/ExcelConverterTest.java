package dev.simplecore.simplix.excel.convert;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelConverter")
class ExcelConverterTest {

    @BeforeEach
    void setUp() {
        ExcelConverter.clearCaches();
    }

    @AfterEach
    void tearDown() {
        ExcelConverter.clearCaches();
    }

    @Nested
    @DisplayName("extractColumnTitles")
    class ExtractColumnTitlesTests {

        @Test
        @DisplayName("should extract column names from annotated fields")
        void shouldExtractColumnNames() {
            List<String> titles = ExcelConverter.extractColumnTitles(SampleEntity.class);
            assertThat(titles).containsExactly("Name", "Age");
        }

        @Test
        @DisplayName("should return columns in order")
        void shouldReturnInOrder() {
            List<String> titles = ExcelConverter.extractColumnTitles(SampleEntity.class);
            assertThat(titles.get(0)).isEqualTo("Name");
            assertThat(titles.get(1)).isEqualTo("Age");
        }

        @Test
        @DisplayName("should cache results")
        void shouldCacheResults() {
            List<String> first = ExcelConverter.extractColumnTitles(SampleEntity.class);
            List<String> second = ExcelConverter.extractColumnTitles(SampleEntity.class);
            // Same list reference (cached)
            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("convertToRowData")
    class ConvertToRowDataTests {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyForNull() {
            assertThat(ExcelConverter.convertToRowData(null, SampleEntity.class)).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(ExcelConverter.convertToRowData(Collections.emptyList(), SampleEntity.class)).isEmpty();
        }

        @Test
        @DisplayName("should convert entity list to row data")
        void shouldConvertToRowData() {
            SampleEntity entity = new SampleEntity();
            entity.name = "Alice";
            entity.age = 30;

            List<List<String>> rows = ExcelConverter.convertToRowData(List.of(entity), SampleEntity.class);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsExactly("Alice", "30");
        }

        @Test
        @DisplayName("should handle null field values")
        void shouldHandleNullFields() {
            SampleEntity entity = new SampleEntity();
            entity.name = null;
            entity.age = 25;

            List<List<String>> rows = ExcelConverter.convertToRowData(List.of(entity), SampleEntity.class);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get(0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("prepareDataModel")
    class PrepareDataModelTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should create data model with headers and rows")
        void shouldCreateDataModel() {
            SampleEntity entity = new SampleEntity();
            entity.name = "Bob";
            entity.age = 25;

            Map<String, Object> model = ExcelConverter.prepareDataModel(
                    List.of(entity), SampleEntity.class);

            assertThat(model).containsKeys("headers", "rows");
            List<String> headers = (List<String>) model.get("headers");
            assertThat(headers).containsExactly("Name", "Age");
            List<?> rows = (List<?>) model.get("rows");
            assertThat(rows).hasSize(1);
        }
    }

    @Nested
    @DisplayName("clearCaches")
    class ClearCachesTests {

        @Test
        @DisplayName("should clear field and column caches")
        void shouldClearCaches() {
            ExcelConverter.extractColumnTitles(SampleEntity.class);
            ExcelConverter.clearCaches();

            // After clearing, the next call should recompute
            List<String> titles = ExcelConverter.extractColumnTitles(SampleEntity.class);
            assertThat(titles).containsExactly("Name", "Age");
        }
    }

    // Test entity
    static class SampleEntity {
        @ExcelColumn(name = "Name", order = 1)
        String name;

        @ExcelColumn(name = "Age", order = 2)
        int age;

        // Field without annotation should be excluded
        String ignored;
    }
}
