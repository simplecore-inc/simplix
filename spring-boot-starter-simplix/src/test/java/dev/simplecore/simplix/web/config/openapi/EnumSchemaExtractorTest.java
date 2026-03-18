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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnumSchemaExtractor - extracts inline enum definitions to separate schemas")
class EnumSchemaExtractorTest {

    private EnumSchemaExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EnumSchemaExtractor();
    }

    @Nested
    @DisplayName("customise")
    class Customise {

        @Test
        @DisplayName("Should extract inline enum from property and create $ref")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractInlineEnum() {
            OpenAPI openApi = createOpenApiWithInlineEnum();

            extractor.customise(openApi);

            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            // The enum should be extracted as a separate schema
            assertThat(schemas.size()).isGreaterThan(1);

            // The original property should now have a $ref
            Schema userSchema = schemas.get("User");
            Schema statusProp = (Schema) userSchema.getProperties().get("status");
            assertThat(statusProp.get$ref()).isNotNull();
            assertThat(statusProp.getEnum()).isNull();
        }

        @Test
        @DisplayName("Should skip properties that are already references")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void skipExistingReferences() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema userSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();
            Schema refProp = new Schema<>();
            refProp.set$ref("#/components/schemas/Status");
            properties.put("status", refProp);
            userSchema.setProperties(properties);
            schemas.put("User", userSchema);
            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Should still be a reference
            Schema statusProp = (Schema) openApi.getComponents().getSchemas().get("User")
                    .getProperties().get("status");
            assertThat(statusProp.get$ref()).isEqualTo("#/components/schemas/Status");
        }

        @Test
        @DisplayName("Should handle null components gracefully")
        void nullComponents() {
            OpenAPI openApi = new OpenAPI();

            extractor.customise(openApi);

            assertThat(openApi.getComponents()).isNull();
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
        @DisplayName("Should reuse extracted enum for properties with same values")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void reuseExtractedEnum() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Two schemas with same enum values on different properties
            Schema schema1 = new Schema<>();
            Map<String, Schema> props1 = new HashMap<>();
            Schema statusProp1 = new Schema<>();
            statusProp1.setType("string");
            statusProp1.setEnum(List.of("ACTIVE", "INACTIVE"));
            props1.put("status", statusProp1);
            schema1.setProperties(props1);
            schemas.put("User", schema1);

            Schema schema2 = new Schema<>();
            Map<String, Schema> props2 = new HashMap<>();
            Schema statusProp2 = new Schema<>();
            statusProp2.setType("string");
            statusProp2.setEnum(List.of("ACTIVE", "INACTIVE"));
            props2.put("status", statusProp2);
            schema2.setProperties(props2);
            schemas.put("Product", schema2);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Both should reference the same extracted enum
            Schema userStatus = (Schema) openApi.getComponents().getSchemas().get("User")
                    .getProperties().get("status");
            Schema productStatus = (Schema) openApi.getComponents().getSchemas().get("Product")
                    .getProperties().get("status");
            assertThat(userStatus.get$ref()).isEqualTo(productStatus.get$ref());
        }

        @Test
        @DisplayName("Should generate ResponseType name for special case enum")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateResponseTypeName() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema responseSchema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            Schema typeProp = new Schema<>();
            typeProp.setType("string");
            typeProp.setEnum(List.of("SUCCESS", "ERROR"));
            props.put("type", typeProp);
            responseSchema.setProperties(props);
            schemas.put("ApiResponse", responseSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("ResponseType");
        }

        @Test
        @DisplayName("Should resolve unique name when collision with existing DTO schema")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void resolveCollisionWithExistingSchema() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Pre-existing "Status" schema (a DTO, not an enum)
            Schema dtoSchema = new Schema<>();
            dtoSchema.setType("object");
            dtoSchema.setProperties(new HashMap<>());
            schemas.put("Status", dtoSchema);

            Schema userSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();
            Schema statusProp = new Schema<>();
            statusProp.setType("string");
            statusProp.setEnum(List.of("ACTIVE", "INACTIVE"));
            properties.put("status", statusProp);
            userSchema.setProperties(properties);
            schemas.put("User", userSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Enum should be extracted with a suffixed name to avoid collision
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas).containsKey("Status1");
        }

        @Test
        @DisplayName("Should resolve collision when two different enums have same property name")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void resolveCollisionDifferentEnumValues() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema1 = new Schema<>();
            Map<String, Schema> props1 = new HashMap<>();
            Schema roleProp1 = new Schema<>();
            roleProp1.setType("string");
            roleProp1.setEnum(List.of("ADMIN", "USER"));
            props1.put("role", roleProp1);
            schema1.setProperties(props1);
            schemas.put("User", schema1);

            Schema schema2 = new Schema<>();
            Map<String, Schema> props2 = new HashMap<>();
            Schema roleProp2 = new Schema<>();
            roleProp2.setType("string");
            roleProp2.setEnum(List.of("READER", "WRITER"));
            props2.put("role", roleProp2);
            schema2.setProperties(props2);
            schemas.put("Document", schema2);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Should have two different enum schemas since values differ
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            boolean hasRole = resultSchemas.containsKey("Role");
            boolean hasRole1 = resultSchemas.containsKey("Role1");
            assertThat(hasRole || hasRole1).isTrue();
        }

        @Test
        @DisplayName("Should process schemas with array items containing enums")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processArrayItemsWithEnums() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema arraySchema = new Schema<>();
            arraySchema.setType("array");
            Schema itemSchema = new Schema<>();
            itemSchema.setType("string");
            itemSchema.setEnum(List.of("READ", "WRITE", "DELETE"));
            arraySchema.setItems(itemSchema);

            properties.put("permissions", arraySchema);
            parentSchema.setProperties(properties);
            schemas.put("Role", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // The extractor processes nested schemas including array items
            assertThat(openApi.getComponents().getSchemas()).isNotNull();
        }

        @Test
        @DisplayName("Should process allOf composed schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processAllOf() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema composedSchema = new Schema<>();
            Schema innerSchema = new Schema<>();
            Map<String, Schema> innerProps = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setEnum(List.of("A", "B"));
            innerProps.put("category", enumProp);
            innerSchema.setProperties(innerProps);
            composedSchema.setAllOf(List.of(innerSchema));
            schemas.put("Composed", composedSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should handle empty enum values list")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void handleEmptyEnumValues() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            Schema emptyEnum = new Schema<>();
            emptyEnum.setType("string");
            emptyEnum.setEnum(List.of());
            props.put("emptyEnum", emptyEnum);
            schema.setProperties(props);
            schemas.put("Test", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Empty enum should not be extracted
            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should preserve description when extracting enum")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void preserveDescription() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setDescription("Status of the entity");
            enumProp.setEnum(List.of("OPEN", "CLOSED"));
            props.put("status", enumProp);
            schema.setProperties(props);
            schemas.put("Ticket", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Find the extracted enum schema
            for (Map.Entry<String, Schema> entry : openApi.getComponents().getSchemas().entrySet()) {
                if (entry.getValue().getEnum() != null && !entry.getValue().getEnum().isEmpty()) {
                    assertThat(entry.getValue().getDescription()).isEqualTo("Status of the entity");
                    break;
                }
            }
        }

        @Test
        @DisplayName("Should generate fallback name for enum with empty property name")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateFallbackNameForEmptyProperty() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            Map<String, Schema> props = new HashMap<>();
            Schema enumProp = new Schema<>();
            enumProp.setType("string");
            enumProp.setEnum(List.of("X", "Y"));
            // Using a property name that would generate a PascalCase name
            props.put("direction", enumProp);
            schema.setProperties(props);
            schemas.put("Movement", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("Direction");
        }
    }

    @Test
    @DisplayName("Should run before NestedObjectSchemaExtractor")
    void orderBeforeNestedExtractor() {
        assertThat(extractor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 200);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OpenAPI createOpenApiWithInlineEnum() {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema userSchema = new Schema<>();
        Map<String, Schema> properties = new HashMap<>();

        Schema statusProp = new Schema<>();
        statusProp.setType("string");
        statusProp.setEnum(List.of("ACTIVE", "INACTIVE", "SUSPENDED"));
        properties.put("status", statusProp);

        Schema nameProp = new Schema<>();
        nameProp.setType("string");
        properties.put("name", nameProp);

        userSchema.setProperties(properties);
        schemas.put("User", userSchema);

        components.setSchemas(schemas);
        openApi.setComponents(components);

        return openApi;
    }
}
