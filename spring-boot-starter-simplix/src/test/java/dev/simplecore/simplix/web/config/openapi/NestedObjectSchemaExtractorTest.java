package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NestedObjectSchemaExtractor - extracts inline nested objects to separate schemas")
class NestedObjectSchemaExtractorTest {

    private NestedObjectSchemaExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new NestedObjectSchemaExtractor();
    }

    @Nested
    @DisplayName("customise")
    class Customise {

        @Test
        @DisplayName("Should extract nested object properties to separate schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractNestedObjectProperty() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Nested object with properties
            Schema addressSchema = new Schema<>();
            addressSchema.setType("object");
            Map<String, Schema> addressProps = new HashMap<>();
            addressProps.put("street", new Schema<>().type("string"));
            addressProps.put("city", new Schema<>().type("string"));
            addressSchema.setProperties(addressProps);

            properties.put("address", addressSchema);
            properties.put("name", new Schema<>().type("string"));
            parentSchema.setProperties(properties);
            schemas.put("User", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // The nested object should be extracted
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);

            // The property should now be a reference
            Schema userSchema = resultSchemas.get("User");
            Schema addressProp = (Schema) userSchema.getProperties().get("address");
            assertThat(addressProp.get$ref()).isNotNull();
        }

        @Test
        @DisplayName("Should extract generic object type properties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractGenericObjectType() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Generic object type without properties
            Schema errorDetailSchema = new Schema<>();
            errorDetailSchema.setType("object");

            properties.put("errorDetail", errorDetailSchema);
            parentSchema.setProperties(properties);
            schemas.put("Response", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas).containsKey("ErrorDetail");
        }

        @Test
        @DisplayName("Should extract Map type properties (additionalProperties)")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractMapTypeProperty() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Map type: { type: object, additionalProperties: { type: string } }
            Schema mapSchema = new Schema<>();
            mapSchema.setType("object");
            Schema valueSchema = new Schema<>();
            valueSchema.setType("string");
            mapSchema.setAdditionalProperties(valueSchema);

            properties.put("labels", mapSchema);
            parentSchema.setProperties(properties);
            schemas.put("Metadata", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            // Should extract Map type schema (StringMap)
            assertThat(resultSchemas).containsKey("StringMap");
        }

        @Test
        @DisplayName("Should handle null components gracefully")
        void nullComponents() {
            OpenAPI openApi = new OpenAPI();

            extractor.customise(openApi);

            assertThat(openApi.getComponents()).isNull();
        }

        @Test
        @DisplayName("Should skip properties that already have $ref")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void skipExistingRefs() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();
            Schema refSchema = new Schema<>();
            refSchema.set$ref("#/components/schemas/Address");
            properties.put("address", refSchema);
            parentSchema.setProperties(properties);
            schemas.put("User", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Should still be a reference
            Schema prop = (Schema) openApi.getComponents().getSchemas().get("User")
                    .getProperties().get("address");
            assertThat(prop.get$ref()).isEqualTo("#/components/schemas/Address");
        }

        @Test
        @DisplayName("Should extract nested objects from array items")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractFromArrayItems() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Array with inline object items
            Schema arraySchema = new Schema<>();
            arraySchema.setType("array");
            Schema itemSchema = new Schema<>();
            itemSchema.setType("object");
            Map<String, Schema> itemProps = new HashMap<>();
            itemProps.put("id", new Schema<>().type("integer"));
            itemSchema.setProperties(itemProps);
            arraySchema.setItems(itemSchema);

            properties.put("items", arraySchema);
            parentSchema.setProperties(properties);
            schemas.put("Container", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should extract nested objects from additionalProperties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractFromAdditionalProperties() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Object with additionalProperties being another object
            Schema mapSchema = new Schema<>();
            mapSchema.setType("object");
            Schema valueSchema = new Schema<>();
            valueSchema.setType("object");
            Map<String, Schema> valueProps = new HashMap<>();
            valueProps.put("name", new Schema<>().type("string"));
            valueSchema.setProperties(valueProps);
            mapSchema.setAdditionalProperties(valueSchema);

            properties.put("attributes", mapSchema);
            parentSchema.setProperties(properties);
            schemas.put("Config", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should handle null schemas gracefully")
        void nullSchemas() {
            OpenAPI openApi = new OpenAPI();
            openApi.setComponents(new Components());

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).isNull();
        }

        @Test
        @DisplayName("Should generate schema name for metadata property")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateMetadataName() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema metadataSchema = new Schema<>();
            metadataSchema.setType("object");
            properties.put("metadata", metadataSchema);
            parentSchema.setProperties(properties);
            schemas.put("Resource", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("Metadata");
        }

        @Test
        @DisplayName("Should generate schema name for data property with parent prefix")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateDataNameWithParentPrefix() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema dataSchema = new Schema<>();
            dataSchema.setType("object");
            properties.put("data", dataSchema);
            parentSchema.setProperties(properties);
            schemas.put("Response", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("ResponseData");
        }

        @Test
        @DisplayName("Should generate schema name for config property with parent prefix")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateConfigNameWithParentPrefix() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema configSchema = new Schema<>();
            configSchema.setType("object");
            properties.put("config", configSchema);
            parentSchema.setProperties(properties);
            schemas.put("App", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("AppConfig");
        }

        @Test
        @DisplayName("Should generate schema name for options property with parent prefix")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateOptionsNameWithParentPrefix() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema optionsSchema = new Schema<>();
            optionsSchema.setType("object");
            properties.put("options", optionsSchema);
            parentSchema.setProperties(properties);
            schemas.put("Widget", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("WidgetOptions");
        }

        @Test
        @DisplayName("Should not extract schemas that already exist")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void skipExistingSchemas() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Pre-existing Address schema
            Schema existingAddress = new Schema<>();
            existingAddress.setType("object");
            existingAddress.addProperty("street", new Schema<>().type("string"));
            schemas.put("Address", existingAddress);

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();
            Schema inlineAddress = new Schema<>();
            inlineAddress.setType("object");
            inlineAddress.addProperty("street", new Schema<>().type("string"));
            properties.put("address", inlineAddress);
            parentSchema.setProperties(properties);
            schemas.put("User", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // The inline address should be replaced with a reference to existing schema
            Schema prop = (Schema) openApi.getComponents().getSchemas().get("User")
                    .getProperties().get("address");
            assertThat(prop.get$ref()).contains("Address");
        }

        @Test
        @DisplayName("Should generate IntegerMap name for map with integer values")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateIntegerMapName() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema mapSchema = new Schema<>();
            mapSchema.setType("object");
            Schema valueSchema = new Schema<>();
            valueSchema.setType("integer");
            mapSchema.setAdditionalProperties(valueSchema);

            properties.put("counts", mapSchema);
            parentSchema.setProperties(properties);
            schemas.put("Stats", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("IntegerMap");
        }
    }

    @Test
    @DisplayName("Should run after EnumSchemaExtractor")
    void orderAfterEnumExtractor() {
        assertThat(extractor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 100);
    }
}
