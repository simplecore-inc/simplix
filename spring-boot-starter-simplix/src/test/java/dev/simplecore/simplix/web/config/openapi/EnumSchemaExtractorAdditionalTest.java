package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additional tests for EnumSchemaExtractor targeting uncovered branches:
 * - oneOf composed schema processing
 * - anyOf composed schema processing
 * - additionalProperties enum extraction
 * - Null schema handling
 * - Null enum list
 */
@DisplayName("EnumSchemaExtractor - additional branch coverage tests")
class EnumSchemaExtractorAdditionalTest {

    private EnumSchemaExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EnumSchemaExtractor();
    }

    @Nested
    @DisplayName("oneOf composed schemas")
    class OneOfSchemas {

        @Test
        @DisplayName("Should process enums within oneOf composed schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processOneOfEnums() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema composedSchema = new Schema<>();
            Schema innerSchema = new Schema<>();
            Map<String, Schema> innerProps = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setEnum(List.of("CIRCLE", "SQUARE", "TRIANGLE"));
            innerProps.put("shape", enumProp);
            innerSchema.setProperties(innerProps);
            composedSchema.setOneOf(List.of(innerSchema));
            schemas.put("Drawing", composedSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("Shape");
        }
    }

    @Nested
    @DisplayName("anyOf composed schemas")
    class AnyOfSchemas {

        @Test
        @DisplayName("Should process enums within anyOf composed schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processAnyOfEnums() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema composedSchema = new Schema<>();
            Schema innerSchema = new Schema<>();
            Map<String, Schema> innerProps = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setEnum(List.of("HIGH", "MEDIUM", "LOW"));
            innerProps.put("priority", enumProp);
            innerSchema.setProperties(innerProps);
            composedSchema.setAnyOf(List.of(innerSchema));
            schemas.put("Task", composedSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("Priority");
        }
    }

    @Nested
    @DisplayName("additionalProperties enum extraction")
    class AdditionalPropertiesEnums {

        @Test
        @DisplayName("Should process enums within additionalProperties schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processAdditionalPropertiesEnums() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Object with additionalProperties that has enum properties
            Schema mapSchema = new Schema<>();
            mapSchema.setType("object");
            Schema valueSchema = new Schema<>();
            Map<String, Schema> valueProps = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setEnum(List.of("ENABLED", "DISABLED"));
            valueProps.put("status", enumProp);
            valueSchema.setProperties(valueProps);
            mapSchema.setAdditionalProperties(valueSchema);

            properties.put("config", mapSchema);
            parentSchema.setProperties(properties);
            schemas.put("Settings", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("Status");
        }
    }

    @Nested
    @DisplayName("Null handling")
    class NullHandling {

        @Test
        @DisplayName("Should handle schema property with null enum list")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void nullEnumList() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            Schema stringProp = new Schema<>();
            stringProp.setType("string");
            // No enum set (null)
            props.put("name", stringProp);
            schema.setProperties(props);
            schemas.put("Simple", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Should not extract any enums
            assertThat(openApi.getComponents().getSchemas().size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle null schema in properties gracefully")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void nullSchemaInProperties() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            props.put("nullProp", null);
            schema.setProperties(props);
            schemas.put("WithNull", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("WithNull");
        }
    }

    @Nested
    @DisplayName("generateEnumName edge cases")
    class GenerateEnumName {

        @Test
        @DisplayName("Should generate PascalCase name from camelCase property name")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void pascalCaseFromCamelCase() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setEnum(List.of("RED", "GREEN", "BLUE"));
            props.put("favoriteColor", enumProp);
            schema.setProperties(props);
            schemas.put("Preferences", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("FavoriteColor");
        }
    }
}
