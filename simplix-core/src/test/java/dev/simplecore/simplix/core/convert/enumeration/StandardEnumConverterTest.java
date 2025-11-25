package dev.simplecore.simplix.core.convert.enumeration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.core.jackson.SimpliXEnumSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StandardEnumConverter Tests")
class StandardEnumConverterTest {

    private StandardEnumConverter converter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        converter = new StandardEnumConverter();

        // Setup ObjectMapper with SimpliXEnumSerializer
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer((Class)Enum.class, new SimpliXEnumSerializer());
        objectMapper.registerModule(module);
    }

    @Test
    @DisplayName("toMap should include label field for SimpliXLabeledEnum")
    void toMap_WithSimplixLabeledEnum_ShouldIncludeLabelField() {
        // Given
        UserRole role = UserRole.ADMIN;

        // When
        Map<String, Object> result = converter.toMap(role);

        // Then
        assertNotNull(result);
        assertEquals("ADMIN", result.get("value"));
        assertEquals("관리자", result.get("label"));
        assertEquals(2, result.size()); // value and label only
    }

    @Test
    @DisplayName("toMap should work with regular enum")
    void toMap_WithRegularEnum_ShouldIncludeValueOnly() {
        // Given
        Status status = Status.ACTIVE;

        // When
        Map<String, Object> result = converter.toMap(status);

        // Then
        assertNotNull(result);
        assertEquals("ACTIVE", result.get("value"));
        assertEquals(1, result.size()); // value only
    }

    @Test
    @DisplayName("toMap should include custom getter fields")
    void toMap_WithCustomGetters_ShouldIncludeCustomFields() {
        // Given
        Priority priority = Priority.HIGH;

        // When
        Map<String, Object> result = converter.toMap(priority);

        // Then
        assertNotNull(result);
        assertEquals("HIGH", result.get("value"));
        assertEquals("높음", result.get("label"));
        assertEquals(3, result.get("level"));
        assertEquals(3, result.size()); // value, label, and level
    }

    @Test
    @DisplayName("SimpliXEnumSerializer should serialize SimpliXLabeledEnum correctly")
    void serialize_WithSimplixLabeledEnum_ShouldProduceCorrectJson() throws Exception {
        // Given
        UserRole role = UserRole.ADMIN;

        // When
        String json = objectMapper.writeValueAsString(role);

        // Then
        assertTrue(json.contains("\"value\":\"ADMIN\"") || json.contains("\"value\" : \"ADMIN\""));
        assertTrue(json.contains("\"label\":\"관리자\"") || json.contains("\"label\" : \"관리자\""));
    }

    @Test
    @DisplayName("SimpliXEnumSerializer should serialize regular enum correctly")
    void serialize_WithRegularEnum_ShouldProduceCorrectJson() throws Exception {
        // Given
        Status status = Status.ACTIVE;

        // When
        String json = objectMapper.writeValueAsString(status);

        // Then
        assertTrue(json.contains("\"value\":\"ACTIVE\"") || json.contains("\"value\" : \"ACTIVE\""));
        assertFalse(json.contains("label")); // Regular enum should not have label
    }

    @Test
    @DisplayName("fromString should convert string to enum")
    void fromString_WithValidValue_ShouldReturnEnum() {
        // When
        UserRole role = converter.fromString("ADMIN", UserRole.class);

        // Then
        assertNotNull(role);
        assertEquals(UserRole.ADMIN, role);
    }

    @Test
    @DisplayName("fromString should be case insensitive")
    void fromString_WithLowercaseValue_ShouldReturnEnum() {
        // When
        UserRole role = converter.fromString("admin", UserRole.class);

        // Then
        assertNotNull(role);
        assertEquals(UserRole.ADMIN, role);
    }

    @Test
    @DisplayName("toString should return enum name")
    void toString_WithEnum_ShouldReturnName() {
        // When
        String result = converter.toString(UserRole.ADMIN);

        // Then
        assertEquals("ADMIN", result);
    }

    @Test
    @DisplayName("toMap should return null for null enum")
    void toMap_WithNullEnum_ShouldReturnNull() {
        // When
        Map<String, Object> result = converter.toMap(null);

        // Then
        assertNull(result);
    }

    // Test enums

    enum UserRole implements SimpliXLabeledEnum {
        ADMIN("관리자"),
        USER("사용자"),
        GUEST("게스트");

        private final String label;

        UserRole(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    enum Priority implements SimpliXLabeledEnum {
        HIGH("높음", 3),
        MEDIUM("중간", 2),
        LOW("낮음", 1);

        private final String label;
        private final int level;

        Priority(String label, int level) {
            this.label = label;
            this.level = level;
        }

        @Override
        public String getLabel() {
            return label;
        }

        public int getLevel() {
            return level;
        }
    }
}
