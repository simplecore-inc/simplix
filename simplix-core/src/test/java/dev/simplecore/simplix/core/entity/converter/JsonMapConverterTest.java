package dev.simplecore.simplix.core.entity.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation")
@DisplayName("JsonMapConverter")
class JsonMapConverterTest {

    private JsonMapConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonMapConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("should serialize map to JSON string")
        void shouldSerializeMap() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("key1", "value1");
            map.put("key2", "value2");

            String result = converter.convertToDatabaseColumn(map);

            assertThat(result).contains("\"key1\"");
            assertThat(result).contains("\"value1\"");
            assertThat(result).contains("\"key2\"");
            assertThat(result).contains("\"value2\"");
        }

        @Test
        @DisplayName("should return empty JSON object for null map")
        void shouldReturnEmptyJsonForNull() {
            String result = converter.convertToDatabaseColumn(null);

            assertThat(result).isEqualTo("{}");
        }

        @Test
        @DisplayName("should return empty JSON object for empty map")
        void shouldReturnEmptyJsonForEmptyMap() {
            String result = converter.convertToDatabaseColumn(Collections.emptyMap());

            assertThat(result).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("should deserialize JSON string to map")
        void shouldDeserializeJsonToMap() {
            String json = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

            Map<String, String> result = converter.convertToEntityAttribute(json);

            assertThat(result).hasSize(2);
            assertThat(result).containsEntry("key1", "value1");
            assertThat(result).containsEntry("key2", "value2");
        }

        @Test
        @DisplayName("should return empty map for null dbData")
        void shouldReturnEmptyMapForNull() {
            Map<String, String> result = converter.convertToEntityAttribute(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty map for empty string")
        void shouldReturnEmptyMapForEmptyString() {
            Map<String, String> result = converter.convertToEntityAttribute("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw for invalid JSON")
        void shouldThrowForInvalidJson() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize JSON");
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("should preserve data through serialize and deserialize")
        void shouldPreserveDataThroughRoundtrip() {
            Map<String, String> original = Map.of("name", "test", "value", "123");

            String dbValue = converter.convertToDatabaseColumn(original);
            Map<String, String> restored = converter.convertToEntityAttribute(dbValue);

            assertThat(restored).isEqualTo(original);
        }
    }
}
