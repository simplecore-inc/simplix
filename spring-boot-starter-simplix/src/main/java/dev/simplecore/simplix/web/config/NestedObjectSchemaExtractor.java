package dev.simplecore.simplix.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenAPI customizer that extracts inline nested objects to separate reusable schemas.
 * This includes generic 'object' types and inline object definitions with properties.
 * Runs after EnumSchemaExtractor to process remaining nested structures.
 *
 * <p>Enabled by default. To disable:
 * <pre>
 * simplix.swagger.customizers.nested-object-extractor.enabled=false
 * </pre>
 */
@Component
public class NestedObjectSchemaExtractor implements OpenApiCustomizer, Ordered {

    private static final Logger log = LoggerFactory.getLogger(NestedObjectSchemaExtractor.class);

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        Map<String, Schema> extractedSchemas = new HashMap<>();
        Map<String, String> schemaHashToName = new HashMap<>();

        log.info("Starting nested object extraction from {} schemas", schemas.size());

        // Process all schemas to find and extract nested objects
        schemas.forEach((schemaName, schema) -> {
            processSchemaForNestedObjects(schema, extractedSchemas, schemas, schemaName, schemaName, schemaHashToName);
        });

        // Add extracted schemas to components
        if (!extractedSchemas.isEmpty()) {
            extractedSchemas.forEach((name, schema) -> {
                if (!schemas.containsKey(name)) {
                    schemas.put(name, schema);
                    log.info("Added nested object schema: {}", name);
                }
            });
            log.info("Extracted {} nested object schemas", extractedSchemas.size());
        }
    }

    /**
     * Recursively process a schema to find and extract nested object definitions.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processSchemaForNestedObjects(Schema schema, Map<String, Schema> extractedSchemas,
                                              Map<String, Schema> existingSchemas,
                                              String parentName, String contextPath,
                                              Map<String, String> schemaHashToName) {
        processSchemaForNestedObjects(schema, extractedSchemas, existingSchemas,
            parentName, contextPath, schemaHashToName, new java.util.HashSet<>());
    }

    /**
     * Recursively process a schema to find and extract nested object definitions with cycle detection.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processSchemaForNestedObjects(Schema schema, Map<String, Schema> extractedSchemas,
                                              Map<String, Schema> existingSchemas,
                                              String parentName, String contextPath,
                                              Map<String, String> schemaHashToName,
                                              java.util.Set<String> processingStack) {
        if (schema == null || schema.get$ref() != null) {
            return;
        }

        // Prevent infinite recursion by checking if we're already processing this context
        if (processingStack.contains(contextPath)) {
            log.debug("Skipping already processing context: {}", contextPath);
            return;
        }

        processingStack.add(contextPath);

        // Process properties
        if (schema.getProperties() != null) {
            Map<String, Schema> properties = new HashMap<>(schema.getProperties());
            properties.forEach((propName, propSchema) -> {
                String propPath = contextPath + "." + propName;

                // Check if this is an object that needs extraction
                if (shouldExtractObject(propSchema)) {
                    String extractedSchemaName = generateSchemaName(propName, parentName, propSchema);

                    if (!existingSchemas.containsKey(extractedSchemaName) &&
                        !extractedSchemas.containsKey(extractedSchemaName)) {

                        // Create the extracted schema
                        Schema extractedSchema = createExtractedSchema(propSchema);
                        extractedSchemas.put(extractedSchemaName, extractedSchema);

                        log.debug("Extracted nested object '{}' from property '{}'",
                            extractedSchemaName, propPath);

                        // Replace with reference
                        Schema refSchema = new Schema();
                        refSchema.set$ref("#/components/schemas/" + extractedSchemaName);
                        schema.getProperties().put(propName, refSchema);

                        // Recursively process the extracted schema with the SAME processing stack
                        // to prevent infinite recursion (SpringDoc 2.8.0+ infinite recursion fix)
                        processSchemaForNestedObjects(extractedSchema, extractedSchemas,
                            existingSchemas, extractedSchemaName, extractedSchemaName, schemaHashToName,
                            processingStack);
                    } else if (existingSchemas.containsKey(extractedSchemaName) ||
                               extractedSchemas.containsKey(extractedSchemaName)) {
                        // Schema already exists, just replace with reference
                        Schema refSchema = new Schema();
                        refSchema.set$ref("#/components/schemas/" + extractedSchemaName);
                        schema.getProperties().put(propName, refSchema);
                    }
                } else {
                    // Recursively process nested schemas
                    processSchemaForNestedObjects(propSchema, extractedSchemas,
                        existingSchemas, parentName, propPath, schemaHashToName, processingStack);
                }
            });
        }

        // Process array items
        if (schema.getItems() != null) {
            Schema itemSchema = schema.getItems();
            if (shouldExtractObject(itemSchema)) {
                // Generate appropriate name for array items
                String itemName = determineArrayItemName(contextPath, parentName);
                String extractedSchemaName = generateSchemaName(itemName, parentName, itemSchema);

                if (!existingSchemas.containsKey(extractedSchemaName) &&
                    !extractedSchemas.containsKey(extractedSchemaName)) {

                    Schema extractedSchema = createExtractedSchema(itemSchema);
                    extractedSchemas.put(extractedSchemaName, extractedSchema);

                    log.debug("Extracted array item object '{}' from '{}'",
                        extractedSchemaName, contextPath);

                    // Replace with reference
                    Schema refSchema = new Schema();
                    refSchema.set$ref("#/components/schemas/" + extractedSchemaName);
                    schema.setItems(refSchema);

                    // Recursively process the extracted schema with the SAME processing stack
                    // to prevent infinite recursion (SpringDoc 2.8.0+ infinite recursion fix)
                    processSchemaForNestedObjects(extractedSchema, extractedSchemas,
                        existingSchemas, extractedSchemaName, extractedSchemaName, schemaHashToName,
                        processingStack);
                } else if (existingSchemas.containsKey(extractedSchemaName) ||
                           extractedSchemas.containsKey(extractedSchemaName)) {
                    // Schema already exists, just replace with reference
                    Schema refSchema = new Schema();
                    refSchema.set$ref("#/components/schemas/" + extractedSchemaName);
                    schema.setItems(refSchema);
                }
            } else {
                processSchemaForNestedObjects(itemSchema, extractedSchemas,
                    existingSchemas, parentName, contextPath + "[]", schemaHashToName, processingStack);
            }
        }

        // Process allOf, oneOf, anyOf
        processComposedSchemas(schema.getAllOf(), extractedSchemas, existingSchemas, parentName, contextPath, schemaHashToName);
        processComposedSchemas(schema.getOneOf(), extractedSchemas, existingSchemas, parentName, contextPath, schemaHashToName);
        processComposedSchemas(schema.getAnyOf(), extractedSchemas, existingSchemas, parentName, contextPath, schemaHashToName);

        // Process additionalProperties
        if (schema.getAdditionalProperties() instanceof Schema) {
            Schema additionalPropSchema = (Schema) schema.getAdditionalProperties();
            if (shouldExtractObject(additionalPropSchema)) {
                // Generate name for additionalProperties value type
                String valueTypeName = determineAdditionalPropertiesValueName(contextPath, parentName);
                String extractedSchemaName = generateSchemaName(valueTypeName, parentName, additionalPropSchema);

                if (!existingSchemas.containsKey(extractedSchemaName) &&
                    !extractedSchemas.containsKey(extractedSchemaName)) {

                    Schema extractedSchema = createExtractedSchema(additionalPropSchema);
                    extractedSchemas.put(extractedSchemaName, extractedSchema);

                    log.debug("Extracted additionalProperties value object '{}' from '{}'",
                        extractedSchemaName, contextPath);

                    // Replace with reference
                    Schema refSchema = new Schema();
                    refSchema.set$ref("#/components/schemas/" + extractedSchemaName);
                    schema.setAdditionalProperties(refSchema);

                    // Recursively process the extracted schema with the SAME processing stack
                    // to prevent infinite recursion (SpringDoc 2.8.0+ infinite recursion fix)
                    processSchemaForNestedObjects(extractedSchema, extractedSchemas,
                        existingSchemas, extractedSchemaName, extractedSchemaName, schemaHashToName,
                        processingStack);
                } else if (existingSchemas.containsKey(extractedSchemaName) ||
                           extractedSchemas.containsKey(extractedSchemaName)) {
                    // Schema already exists, just replace with reference
                    Schema refSchema = new Schema();
                    refSchema.set$ref("#/components/schemas/" + extractedSchemaName);
                    schema.setAdditionalProperties(refSchema);
                }
            } else {
                processSchemaForNestedObjects(additionalPropSchema,
                    extractedSchemas, existingSchemas, parentName, contextPath + "[*]", schemaHashToName, processingStack);
            }
        }

        // Remove from processing stack when done
        processingStack.remove(contextPath);
    }

    /**
     * Process composed schemas (allOf, oneOf, anyOf).
     */
    @SuppressWarnings("rawtypes")
    private void processComposedSchemas(java.util.List schemas,
                                       Map<String, Schema> extractedSchemas,
                                       Map<String, Schema> existingSchemas,
                                       String parentName, String contextPath,
                                       Map<String, String> schemaHashToName) {
        if (schemas != null) {
            schemas.forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForNestedObjects((Schema) s, extractedSchemas,
                        existingSchemas, parentName, contextPath, schemaHashToName);
                }
            });
        }
    }

    /**
     * Determine if an object schema should be extracted to a separate schema.
     */
    @SuppressWarnings("rawtypes")
    private boolean shouldExtractObject(Schema schema) {
        if (schema == null || schema.get$ref() != null) {
            return false;
        }

        // Extract if it's an object with properties
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return true;
        }

        // Extract Map patterns (e.g., Map<String, String>)
        // This handles cases like "roleNameI18n: { type: object, additionalProperties: { type: string } }"
        if ("object".equals(schema.getType()) &&
            schema.getAdditionalProperties() instanceof Schema) {
            return true;
        }

        // Extract if it's a generic object type (without properties or additionalProperties defined)
        // This handles cases like "errorDetail: { type: object }"
        if ("object".equals(schema.getType()) &&
            schema.getProperties() == null &&
            schema.getEnum() == null &&
            schema.get$ref() == null &&
            schema.getAdditionalProperties() == null) {
            return true;
        }

        return false;
    }

    /**
     * Create an extracted schema from the original schema.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Schema createExtractedSchema(Schema originalSchema) {
        Schema extractedSchema = new Schema();

        // Copy basic properties
        extractedSchema.setType(originalSchema.getType() != null ? originalSchema.getType() : "object");

        if (originalSchema.getProperties() != null) {
            extractedSchema.setProperties(new HashMap<>(originalSchema.getProperties()));
        }

        if (originalSchema.getRequired() != null) {
            extractedSchema.setRequired(originalSchema.getRequired());
        }

        if (originalSchema.getDescription() != null) {
            extractedSchema.setDescription(originalSchema.getDescription());
        }

        // Copy additionalProperties if present
        if (originalSchema.getAdditionalProperties() != null) {
            extractedSchema.setAdditionalProperties(originalSchema.getAdditionalProperties());
        }

        // For generic object types without properties, add a flexible structure
        if ((extractedSchema.getProperties() == null || extractedSchema.getProperties().isEmpty()) &&
            extractedSchema.getAdditionalProperties() == null) {
            // This represents a flexible object that can have any properties
            extractedSchema.setAdditionalProperties(true);
            if (extractedSchema.getDescription() == null) {
                extractedSchema.setDescription("Flexible object that can contain any properties");
            }
        }

        return extractedSchema;
    }

    /**
     * Generate a suitable name for an extracted schema.
     */
    @SuppressWarnings("rawtypes")
    private String generateSchemaName(String propertyName, String parentName, Schema schema) {
        // Handle Map types (objects with additionalProperties)
        if (schema.getAdditionalProperties() instanceof Schema) {
            Schema valueSchema = (Schema) schema.getAdditionalProperties();
            String valueType = valueSchema.getType();

            // Generate Map schema name based on value type
            if ("string".equals(valueType)) {
                return "StringMap";
            } else if ("integer".equals(valueType)) {
                return "IntegerMap";
            } else if ("number".equals(valueType)) {
                return "NumberMap";
            } else if ("boolean".equals(valueType)) {
                return "BooleanMap";
            } else if ("object".equals(valueType)) {
                return "ObjectMap";
            } else {
                // For complex value types, use property name
                return capitalizeFirstLetter(propertyName) + "Map";
            }
        }

        // Special cases for common property names
        if ("errorDetail".equals(propertyName)) {
            return "ErrorDetail";
        }

        if ("metadata".equals(propertyName)) {
            return "Metadata";
        }

        if ("data".equals(propertyName)) {
            return parentName + "Data";
        }

        if ("config".equals(propertyName) || "configuration".equals(propertyName)) {
            return parentName + "Config";
        }

        if ("options".equals(propertyName)) {
            return parentName + "Options";
        }

        // Generate name based on property name and parent context
        String baseName = capitalizeFirstLetter(propertyName);

        // If it's a generic object without properties, add suffix
        if (schema.getProperties() == null || schema.getProperties().isEmpty()) {
            if (!baseName.endsWith("Object") && !baseName.endsWith("Data")) {
                baseName = baseName + "Object";
            }
        }

        // Avoid too generic names by prefixing with parent name if needed
        if (isGenericName(baseName)) {
            return parentName + baseName;
        }

        return baseName;
    }

    /**
     * Check if a name is too generic and needs context.
     */
    private boolean isGenericName(String name) {
        return "Object".equals(name) ||
               "Data".equals(name) ||
               "Item".equals(name) ||
               "Value".equals(name) ||
               "Result".equals(name);
    }

    /**
     * Determine the appropriate name for additionalProperties value type.
     */
    private String determineAdditionalPropertiesValueName(String contextPath, String parentName) {
        // Extract the property name from the context path
        if (contextPath.contains(".")) {
            String[] parts = contextPath.split("\\.");
            String lastPart = parts[parts.length - 1];

//            // Special handling for I18n properties
//            if (lastPart.endsWith("I18n")) {
//                // e.g., tenantNameI18n -> I18nValue
//                return "I18nValue";
//            }

            // Other special cases
            if (lastPart.equals("metadata")) {
                return "MetadataValue";
            }
            if (lastPart.equals("attributes")) {
                return "AttributeValue";
            }
            if (lastPart.equals("properties")) {
                return "PropertyValue";
            }
            if (lastPart.equals("tags")) {
                return "TagValue";
            }

            // Default: use the property name with "Value" suffix
            return capitalizeFirstLetter(lastPart) + "Value";
        }

        // Fallback to parent-based naming
        return parentName + "Value";
    }

    /**
     * Determine the appropriate name for an array item.
     */
    private String determineArrayItemName(String contextPath, String parentName) {
        // Extract the property name from the context path
        if (contextPath.contains(".")) {
            String[] parts = contextPath.split("\\.");
            String lastPart = parts[parts.length - 1];

            // Special handling for common array properties
            if ("domainEvents".equals(lastPart)) {
                return "DomainEvent";
            }
            if ("items".equals(lastPart)) {
                return "Item";
            }
            if ("elements".equals(lastPart)) {
                return "Element";
            }
            if ("children".equals(lastPart)) {
                return "Child";
            }
            if ("entries".equals(lastPart)) {
                return "Entry";
            }

            // Remove 's' or 'es' from plural forms
            if (lastPart.endsWith("ies")) {
                // companies -> company
                return lastPart.substring(0, lastPart.length() - 3) + "y";
            }
            if (lastPart.endsWith("es")) {
                // boxes -> box, matches -> match
                return lastPart.substring(0, lastPart.length() - 2);
            }
            if (lastPart.endsWith("s")) {
                // items -> item
                return lastPart.substring(0, lastPart.length() - 1);
            }

            return lastPart + "Item";
        }

        // Fallback to parent-based naming
        return parentName + "Item";
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    @Override
    public int getOrder() {
        // Run after EnumSchemaExtractor (which has default order)
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}