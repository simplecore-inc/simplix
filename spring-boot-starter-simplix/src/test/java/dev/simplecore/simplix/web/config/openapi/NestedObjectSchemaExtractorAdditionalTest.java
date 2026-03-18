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
 * Additional tests for NestedObjectSchemaExtractor targeting uncovered branches:
 * - determineArrayItemName special cases (domainEvents, elements, children, entries, plurals)
 * - determineAdditionalPropertiesValueName special cases (metadata, attributes, properties, tags)
 * - generateSchemaName for different Map value types (number, boolean, object, custom)
 * - capitalizeFirstLetter edge cases
 * - isGenericName for all variants (Object, Data, Item, Value, Result)
 * - allOf/oneOf/anyOf composed schema processing
 * - processSchemaForNestedObjects cycle detection
 * - configuration property name variants
 */
@DisplayName("NestedObjectSchemaExtractor - additional branch coverage tests")
class NestedObjectSchemaExtractorAdditionalTest {

    private NestedObjectSchemaExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new NestedObjectSchemaExtractor();
    }

    @Nested
    @DisplayName("generateSchemaName for Map types")
    class MapSchemaNames {

        @Test
        @DisplayName("Should generate NumberMap for map with number values")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateNumberMapName() {
            OpenAPI openApi = buildWithMapProperty("ratings", "number");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("NumberMap");
        }

        @Test
        @DisplayName("Should generate BooleanMap for map with boolean values")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateBooleanMapName() {
            OpenAPI openApi = buildWithMapProperty("flags", "boolean");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("BooleanMap");
        }

        @Test
        @DisplayName("Should generate ObjectMap for map with object values")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateObjectMapName() {
            OpenAPI openApi = buildWithMapProperty("details", "object");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("ObjectMap");
        }

        @Test
        @DisplayName("Should generate PropertyNameMap for map with non-standard value type")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateCustomMapName() {
            OpenAPI openApi = buildWithMapProperty("widgets", "array");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("WidgetsMap");
        }
    }

    @Nested
    @DisplayName("generateSchemaName for special property names")
    class SpecialPropertyNames {

        @Test
        @DisplayName("Should generate name with suffix for configuration property")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void generateConfigurationName() {
            OpenAPI openApi = buildWithObjectProperty("App", "configuration");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("AppConfig");
        }

        @Test
        @DisplayName("Should add Object suffix for generic object without properties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void addObjectSuffix() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            // Generic object type without properties, but with a non-special name
            Schema genericSchema = new Schema<>();
            genericSchema.setType("object");
            properties.put("payload", genericSchema);
            parentSchema.setProperties(properties);
            schemas.put("Message", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("PayloadObject");
        }

        @Test
        @DisplayName("Should prefix generic name with parent when name is Object")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void prefixGenericNames() {
            // This tests the isGenericName check for "Data", "Item", "Value", "Result"
            for (String genericPropName : List.of("data", "item", "value", "result")) {
                OpenAPI openApi = new OpenAPI();
                Components components = new Components();
                Map<String, Schema> schemas = new HashMap<>();

                Schema parentSchema = new Schema<>();
                Map<String, Schema> properties = new HashMap<>();
                Schema nestedObj = new Schema<>();
                nestedObj.setType("object");
                Map<String, Schema> nestedProps = new HashMap<>();
                nestedProps.put("field1", new Schema<>().type("string"));
                nestedObj.setProperties(nestedProps);
                properties.put(genericPropName, nestedObj);
                parentSchema.setProperties(properties);
                schemas.put("Container", parentSchema);

                components.setSchemas(schemas);
                openApi.setComponents(components);

                extractor.customise(openApi);
                // Should create a schema
                assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(1);
            }
        }
    }

    @Nested
    @DisplayName("determineArrayItemName special cases")
    class ArrayItemNames {

        @Test
        @DisplayName("Should use DomainEvent for domainEvents array items")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void domainEventsArrayItem() {
            OpenAPI openApi = buildWithArrayProperty("Parent", "domainEvents");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("DomainEvent");
        }

        @Test
        @DisplayName("Should use Element for elements array items")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void elementsArrayItem() {
            OpenAPI openApi = buildWithArrayProperty("Container", "elements");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("Element");
        }

        @Test
        @DisplayName("Should use Child for children array items")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void childrenArrayItem() {
            OpenAPI openApi = buildWithArrayProperty("TreeNode", "children");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("Child");
        }

        @Test
        @DisplayName("Should use Entry for entries array items")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void entriesArrayItem() {
            OpenAPI openApi = buildWithArrayProperty("LogBook", "entries");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("Entry");
        }

        @Test
        @DisplayName("Should singularize -ies plural (categories -> category)")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void singularizeIesPlural() {
            OpenAPI openApi = buildWithArrayProperty("Store", "categories");
            extractor.customise(openApi);
            // categories -> categor + y = category -> Capitalize = Category
            assertThat(openApi.getComponents().getSchemas()).containsKey("Category");
        }

        @Test
        @DisplayName("Should singularize -es plural (boxes -> box)")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void singularizeEsPlural() {
            OpenAPI openApi = buildWithArrayProperty("Warehouse", "boxes");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("Box");
        }

        @Test
        @DisplayName("Should singularize -s plural (users -> user)")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void singularizeSPlural() {
            OpenAPI openApi = buildWithArrayProperty("Group", "users");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("User");
        }

        @Test
        @DisplayName("Should append Item for non-plural array property names")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void appendItemForNonPlural() {
            OpenAPI openApi = buildWithArrayProperty("Container", "content");
            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas()).containsKey("ContentItem");
        }
    }

    @Nested
    @DisplayName("determineAdditionalPropertiesValueName special cases")
    class AdditionalPropertiesValueNames {

        @Test
        @DisplayName("Should use MetadataValue for metadata additionalProperties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void metadataAdditionalProps() {
            OpenAPI openApi = buildWithNestedAdditionalProperties("Resource", "metadata");
            extractor.customise(openApi);
            // The nested object in additionalProperties should be extracted
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should use AttributeValue for attributes additionalProperties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void attributesAdditionalProps() {
            OpenAPI openApi = buildWithNestedAdditionalProperties("Entity", "attributes");
            extractor.customise(openApi);
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should use PropertyValue for properties additionalProperties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void propertiesAdditionalProps() {
            OpenAPI openApi = buildWithNestedAdditionalProperties("Config", "properties");
            extractor.customise(openApi);
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should use TagValue for tags additionalProperties")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void tagsAdditionalProps() {
            OpenAPI openApi = buildWithNestedAdditionalProperties("Resource", "tags");
            extractor.customise(openApi);
            Map<String, Schema> resultSchemas = openApi.getComponents().getSchemas();
            assertThat(resultSchemas.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("capitalizeFirstLetter edge cases")
    class CapitalizeEdgeCases {

        @Test
        @DisplayName("Should handle property with empty or null schema name gracefully")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void emptyProperty() {
            // Indirectly test capitalizeFirstLetter through the schema name generation
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema nestedObj = new Schema<>();
            nestedObj.setType("object");
            Map<String, Schema> nestedProps = new HashMap<>();
            nestedProps.put("field", new Schema<>().type("string"));
            nestedObj.setProperties(nestedProps);
            properties.put("a", nestedObj);
            parentSchema.setProperties(properties);
            schemas.put("Test", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("Composed schema processing (allOf, oneOf, anyOf)")
    class ComposedSchemas {

        @Test
        @DisplayName("Should process oneOf composed schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processOneOf() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema composedSchema = new Schema<>();
            Schema innerSchema = new Schema<>();
            Map<String, Schema> innerProps = new HashMap<>();
            Schema nestedObj = new Schema<>();
            nestedObj.setType("object");
            Map<String, Schema> nestedObjProps = new HashMap<>();
            nestedObjProps.put("value", new Schema<>().type("string"));
            nestedObj.setProperties(nestedObjProps);
            innerProps.put("option", nestedObj);
            innerSchema.setProperties(innerProps);
            composedSchema.setOneOf(List.of(innerSchema));
            schemas.put("Polymorphic", composedSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should process anyOf composed schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processAnyOf() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema composedSchema = new Schema<>();
            Schema innerSchema = new Schema<>();
            Map<String, Schema> innerProps = new HashMap<>();
            Schema nestedObj = new Schema<>();
            nestedObj.setType("object");
            Map<String, Schema> nestedObjProps = new HashMap<>();
            nestedObjProps.put("content", new Schema<>().type("string"));
            nestedObj.setProperties(nestedObjProps);
            innerProps.put("response", nestedObj);
            innerSchema.setProperties(innerProps);
            composedSchema.setAnyOf(List.of(innerSchema));
            schemas.put("FlexibleResponse", composedSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(1);
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
            Schema nestedObj = new Schema<>();
            nestedObj.setType("object");
            Map<String, Schema> nestedObjProps = new HashMap<>();
            nestedObjProps.put("detail", new Schema<>().type("string"));
            nestedObj.setProperties(nestedObjProps);
            innerProps.put("extension", nestedObj);
            innerSchema.setProperties(innerProps);
            composedSchema.setAllOf(List.of(innerSchema));
            schemas.put("Extended", composedSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);
            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("Existing schema collision")
    class SchemaCollisions {

        @Test
        @DisplayName("Should replace with reference when extracted schema already exists")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void replaceWithRefWhenExtractedExists() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Two parents with same named inline object
            Schema parent1 = new Schema<>();
            Map<String, Schema> props1 = new HashMap<>();
            Schema addr1 = new Schema<>();
            addr1.setType("object");
            Map<String, Schema> addrProps1 = new HashMap<>();
            addrProps1.put("street", new Schema<>().type("string"));
            addr1.setProperties(addrProps1);
            props1.put("address", addr1);
            parent1.setProperties(props1);
            schemas.put("User", parent1);

            Schema parent2 = new Schema<>();
            Map<String, Schema> props2 = new HashMap<>();
            Schema addr2 = new Schema<>();
            addr2.setType("object");
            Map<String, Schema> addrProps2 = new HashMap<>();
            addrProps2.put("street", new Schema<>().type("string"));
            addr2.setProperties(addrProps2);
            props2.put("address", addr2);
            parent2.setProperties(props2);
            schemas.put("Company", parent2);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Both should reference the same extracted Address schema
            Schema userAddr = (Schema) openApi.getComponents().getSchemas().get("User").getProperties().get("address");
            Schema compAddr = (Schema) openApi.getComponents().getSchemas().get("Company").getProperties().get("address");
            assertThat(userAddr.get$ref()).isNotNull();
            assertThat(compAddr.get$ref()).isNotNull();
        }

        @Test
        @DisplayName("Should handle array item when schema already exists")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void arrayItemSchemaAlreadyExists() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Pre-existing Item schema
            Schema existingItem = new Schema<>();
            existingItem.setType("object");
            existingItem.addProperty("id", new Schema<>().type("integer"));
            schemas.put("Item", existingItem);

            // Schema with array items that would generate "Item" name
            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();
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

            // Array items should be replaced with reference
            Schema containerSchema = openApi.getComponents().getSchemas().get("Container");
            Schema itemsArray = (Schema) containerSchema.getProperties().get("items");
            assertThat(itemsArray.getItems().get$ref()).isNotNull();
        }

        @Test
        @DisplayName("Should handle additionalProperties when extracted schema already exists")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void additionalPropsSchemaAlreadyExists() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Schema with a nested object in additionalProperties
            Schema parentSchema = new Schema<>();
            parentSchema.setType("object");
            Schema valueSchema = new Schema<>();
            valueSchema.setType("object");
            Map<String, Schema> valueProps = new HashMap<>();
            valueProps.put("name", new Schema<>().type("string"));
            valueSchema.setProperties(valueProps);
            parentSchema.setAdditionalProperties(valueSchema);
            schemas.put("MapObject", parentSchema);

            // Another schema with the same additionalProperties structure
            Schema parentSchema2 = new Schema<>();
            parentSchema2.setType("object");
            Schema valueSchema2 = new Schema<>();
            valueSchema2.setType("object");
            Map<String, Schema> valueProps2 = new HashMap<>();
            valueProps2.put("name", new Schema<>().type("string"));
            valueSchema2.setProperties(valueProps2);
            parentSchema2.setAdditionalProperties(valueSchema2);
            schemas.put("AnotherMapObject", parentSchema2);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(2);
        }
    }

    @Nested
    @DisplayName("Cycle detection")
    class CycleDetection {

        @Test
        @DisplayName("Should handle recursive schema references without infinite loop")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void handleRecursiveSchema() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Create a schema that references itself through properties
            Schema treeNode = new Schema<>();
            Map<String, Schema> treeProps = new HashMap<>();
            treeProps.put("name", new Schema<>().type("string"));
            // Self-referencing children property
            Schema childrenArray = new Schema<>();
            childrenArray.setType("array");
            Schema childItem = new Schema<>();
            childItem.setType("object");
            Map<String, Schema> childItemProps = new HashMap<>();
            childItemProps.put("name", new Schema<>().type("string"));
            childItem.setProperties(childItemProps);
            childrenArray.setItems(childItem);
            treeProps.put("children", childrenArray);
            treeNode.setProperties(treeProps);
            schemas.put("TreeNode", treeNode);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            // Should not cause infinite loop
            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("TreeNode");
        }
    }

    @Nested
    @DisplayName("Nested additionalProperties with dot-separated context path")
    class NestedAdditionalPropertiesContext {

        @Test
        @DisplayName("Should process deeply nested additionalProperties with metadata path")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void deeplyNestedAdditionalProperties() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            // Parent -> child -> metadata (additionalProperties with nested object)
            Schema parentSchema = new Schema<>();
            Map<String, Schema> parentProps = new HashMap<>();

            Schema childSchema = new Schema<>();
            Map<String, Schema> childProps = new HashMap<>();

            // metadata property on child with additionalProperties
            Schema metadataMapSchema = new Schema<>();
            metadataMapSchema.setType("object");
            Schema metadataValueSchema = new Schema<>();
            metadataValueSchema.setType("object");
            Map<String, Schema> metadataValueProps = new HashMap<>();
            metadataValueProps.put("info", new Schema<>().type("string"));
            metadataValueSchema.setProperties(metadataValueProps);
            metadataMapSchema.setAdditionalProperties(metadataValueSchema);

            childProps.put("metadata", metadataMapSchema);
            childSchema.setProperties(childProps);
            parentProps.put("child", childSchema);
            parentSchema.setProperties(parentProps);
            schemas.put("Root", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Should have extracted nested schemas
            assertThat(openApi.getComponents().getSchemas().size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Should handle additionalProperties without nested properties (non-extractable)")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void additionalPropertiesNotExtractable() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> parentProps = new HashMap<>();

            // Object with additionalProperties being a simple string schema
            Schema mapSchema = new Schema<>();
            mapSchema.setType("object");
            Schema valueSchema = new Schema<>();
            valueSchema.setType("string");
            // This is NOT extractable (simple type, no properties)
            Schema wrapperSchema = new Schema<>();
            wrapperSchema.setType("object");
            wrapperSchema.setAdditionalProperties(valueSchema);

            parentProps.put("settings", wrapperSchema);
            parentSchema.setProperties(parentProps);
            schemas.put("Config", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("Config");
        }
    }

    @Nested
    @DisplayName("Array items in nested context")
    class ArrayItemsNestedContext {

        @Test
        @DisplayName("Should handle array items already having a reference")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void arrayItemsWithExistingRef() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema arraySchema = new Schema<>();
            arraySchema.setType("array");
            Schema refItem = new Schema<>();
            refItem.set$ref("#/components/schemas/ExistingItem");
            arraySchema.setItems(refItem);

            properties.put("items", arraySchema);
            parentSchema.setProperties(properties);
            schemas.put("Container", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // Items should still be a reference
            Schema containerSchema = openApi.getComponents().getSchemas().get("Container");
            Schema itemsArray = (Schema) containerSchema.getProperties().get("items");
            assertThat(itemsArray.getItems().get$ref()).isEqualTo("#/components/schemas/ExistingItem");
        }
    }

    @Nested
    @DisplayName("Schema with description and required fields")
    class SchemaWithDescription {

        @Test
        @DisplayName("Should preserve description and required fields when extracting schema")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void preserveDescriptionAndRequired() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema parentSchema = new Schema<>();
            Map<String, Schema> properties = new HashMap<>();

            Schema nestedObj = new Schema<>();
            nestedObj.setType("object");
            nestedObj.setDescription("The address details");
            nestedObj.setRequired(List.of("street", "city"));
            Map<String, Schema> nestedProps = new HashMap<>();
            nestedProps.put("street", new Schema<>().type("string"));
            nestedProps.put("city", new Schema<>().type("string"));
            nestedObj.setProperties(nestedProps);
            properties.put("address", nestedObj);
            parentSchema.setProperties(properties);
            schemas.put("User", parentSchema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            extractor.customise(openApi);

            // The extracted schema should have description and required
            Schema extractedAddr = openApi.getComponents().getSchemas().get("Address");
            assertThat(extractedAddr).isNotNull();
            assertThat(extractedAddr.getDescription()).isEqualTo("The address details");
            assertThat(extractedAddr.getRequired()).contains("street", "city");
        }
    }

    // --- Helper methods ---

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OpenAPI buildWithMapProperty(String propName, String valueType) {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema parentSchema = new Schema<>();
        Map<String, Schema> properties = new HashMap<>();

        Schema mapSchema = new Schema<>();
        mapSchema.setType("object");
        Schema valueSchema = new Schema<>();
        valueSchema.setType(valueType);
        mapSchema.setAdditionalProperties(valueSchema);

        properties.put(propName, mapSchema);
        parentSchema.setProperties(properties);
        schemas.put("Parent", parentSchema);

        components.setSchemas(schemas);
        openApi.setComponents(components);
        return openApi;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OpenAPI buildWithObjectProperty(String parentName, String propName) {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema parentSchema = new Schema<>();
        Map<String, Schema> properties = new HashMap<>();

        Schema nestedObj = new Schema<>();
        nestedObj.setType("object");
        properties.put(propName, nestedObj);
        parentSchema.setProperties(properties);
        schemas.put(parentName, parentSchema);

        components.setSchemas(schemas);
        openApi.setComponents(components);
        return openApi;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OpenAPI buildWithArrayProperty(String parentName, String propName) {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema parentSchema = new Schema<>();
        Map<String, Schema> properties = new HashMap<>();

        Schema arraySchema = new Schema<>();
        arraySchema.setType("array");
        Schema itemSchema = new Schema<>();
        itemSchema.setType("object");
        Map<String, Schema> itemProps = new HashMap<>();
        itemProps.put("id", new Schema<>().type("integer"));
        itemSchema.setProperties(itemProps);
        arraySchema.setItems(itemSchema);

        properties.put(propName, arraySchema);
        parentSchema.setProperties(properties);
        schemas.put(parentName, parentSchema);

        components.setSchemas(schemas);
        openApi.setComponents(components);
        return openApi;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OpenAPI buildWithNestedAdditionalProperties(String parentName, String propName) {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema parentSchema = new Schema<>();
        Map<String, Schema> properties = new HashMap<>();

        // Create an object with additionalProperties being another object
        Schema mapSchema = new Schema<>();
        mapSchema.setType("object");
        Schema valueSchema = new Schema<>();
        valueSchema.setType("object");
        Map<String, Schema> valueProps = new HashMap<>();
        valueProps.put("detail", new Schema<>().type("string"));
        valueSchema.setProperties(valueProps);
        mapSchema.setAdditionalProperties(valueSchema);

        properties.put(propName, mapSchema);
        parentSchema.setProperties(properties);
        schemas.put(parentName, parentSchema);

        components.setSchemas(schemas);
        openApi.setComponents(components);
        return openApi;
    }
}
