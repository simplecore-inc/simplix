package dev.simplecore.simplix.core.entity.converter;

import dev.simplecore.simplix.core.security.hashing.HashingUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HashingAttributeConverter")
class HashingAttributeConverterTest {

    private HashingAttributeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new HashingAttributeConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("should hash plain text value")
        void shouldHashPlainTextValue() {
            String plainText = "test@example.com";

            String result = converter.convertToDatabaseColumn(plainText);

            assertThat(result).isNotEqualTo(plainText);
            assertThat(HashingUtils.isValidSha256Hash(result)).isTrue();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            String result = converter.convertToDatabaseColumn(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmptyInput() {
            String result = converter.convertToDatabaseColumn("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should not double-hash already hashed value")
        void shouldNotDoubleHash() {
            String plainText = "test@example.com";
            String hashed = HashingUtils.hash(plainText);

            String result = converter.convertToDatabaseColumn(hashed);

            assertThat(result).isEqualTo(hashed);
        }

        @Test
        @DisplayName("should produce consistent hash for same input")
        void shouldProduceConsistentHash() {
            String input = "consistent-input";

            String result1 = converter.convertToDatabaseColumn(input);
            String result2 = converter.convertToDatabaseColumn(input);

            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("should return hashed value as-is since hashing is one-way")
        void shouldReturnHashedValueAsIs() {
            String hashed = HashingUtils.hash("test@example.com");

            String result = converter.convertToEntityAttribute(hashed);

            assertThat(result).isEqualTo(hashed);
        }

        @Test
        @DisplayName("should return null for null dbData")
        void shouldReturnNullForNull() {
            String result = converter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }
    }
}
