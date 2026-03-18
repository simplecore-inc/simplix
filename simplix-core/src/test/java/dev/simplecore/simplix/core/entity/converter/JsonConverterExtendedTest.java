package dev.simplecore.simplix.core.entity.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation")
@DisplayName("JSON Converters - Extended Coverage")
class JsonConverterExtendedTest {

    @Nested
    @DisplayName("JsonListConverter")
    class JsonListConverterTests {

        private final JsonListConverter converter = new JsonListConverter();

        @Test
        @DisplayName("should return empty list JSON for null attribute")
        void shouldReturnNullJson() {
            String result = converter.convertToDatabaseColumn(null);
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("should return empty list for invalid JSON")
        void shouldReturnEmptyForInvalidJson() {
            List<String> result = converter.convertToEntityAttribute("invalid-json");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("JsonMapConverter")
    class JsonMapConverterTests {

        private final JsonMapConverter converter = new JsonMapConverter();

        @Test
        @DisplayName("should return {} for null attribute")
        void shouldReturnEmptyJsonForNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
        }

        @Test
        @DisplayName("should return {} for empty map")
        void shouldReturnEmptyJsonForEmpty() {
            assertThat(converter.convertToDatabaseColumn(Map.of())).isEqualTo("{}");
        }

        @Test
        @DisplayName("should return empty map for null dbData")
        void shouldReturnEmptyMapForNull() {
            assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty map for empty dbData")
        void shouldReturnEmptyMapForEmpty() {
            assertThat(converter.convertToEntityAttribute("")).isEmpty();
        }

        @Test
        @DisplayName("should throw for invalid JSON on read")
        void shouldThrowForInvalidJson() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to deserialize JSON");
        }
    }

    @Nested
    @DisplayName("HashingAttributeConverter")
    class HashingConverterTests {

        private final HashingAttributeConverter converter = new HashingAttributeConverter();

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("should return empty for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        }

        @Test
        @DisplayName("should hash plain text")
        void shouldHashPlainText() {
            String result = converter.convertToDatabaseColumn("test@example.com");
            assertThat(result).isNotEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should not double-hash already hashed values")
        void shouldNotDoubleHash() {
            String firstHash = converter.convertToDatabaseColumn("test@example.com");
            String secondHash = converter.convertToDatabaseColumn(firstHash);
            assertThat(secondHash).isEqualTo(firstHash);
        }

        @Test
        @DisplayName("should return dbData as-is for convertToEntityAttribute")
        void shouldReturnAsIs() {
            assertThat(converter.convertToEntityAttribute("hashed-value")).isEqualTo("hashed-value");
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }
}
