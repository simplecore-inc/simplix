package dev.simplecore.simplix.core.entity.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonListConverter")
class JsonListConverterTest {

    private JsonListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonListConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("should serialize list to JSON string")
        void shouldSerializeList() {
            List<String> list = List.of("apple", "banana", "cherry");

            String result = converter.convertToDatabaseColumn(list);

            assertThat(result).isEqualTo("[\"apple\",\"banana\",\"cherry\"]");
        }

        @Test
        @DisplayName("should serialize empty list to empty JSON array")
        void shouldSerializeEmptyList() {
            List<String> list = new ArrayList<>();

            String result = converter.convertToDatabaseColumn(list);

            assertThat(result).isEqualTo("[]");
        }

        @Test
        @DisplayName("should serialize null to JSON null string")
        void shouldSerializeNull() {
            String result = converter.convertToDatabaseColumn(null);

            assertThat(result).isEqualTo("null");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("should deserialize JSON array to list")
        void shouldDeserializeJsonArray() {
            String json = "[\"apple\",\"banana\",\"cherry\"]";

            List<String> result = converter.convertToEntityAttribute(json);

            assertThat(result).containsExactly("apple", "banana", "cherry");
        }

        @Test
        @DisplayName("should deserialize empty JSON array to empty list")
        void shouldDeserializeEmptyArray() {
            String json = "[]";

            List<String> result = converter.convertToEntityAttribute(json);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for invalid JSON")
        void shouldReturnEmptyListForInvalidJson() {
            String invalidJson = "not-json";

            List<String> result = converter.convertToEntityAttribute(invalidJson);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("should preserve data through convert and convertBack")
        void shouldPreserveDataThroughRoundtrip() {
            List<String> original = List.of("one", "two", "three");

            String dbValue = converter.convertToDatabaseColumn(original);
            List<String> restored = converter.convertToEntityAttribute(dbValue);

            assertThat(restored).isEqualTo(original);
        }
    }
}
