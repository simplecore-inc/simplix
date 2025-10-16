package dev.simplecore.simplix.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAPI customizer that extracts inline enum definitions to separate reusable schemas.
 * This improves API documentation by creating named enum types that can be referenced
 * throughout the documentation.
 */
@Component
public class EnumSchemaExtractor implements OpenApiCustomizer {

    private static final Logger log = LoggerFactory.getLogger(EnumSchemaExtractor.class);

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        Map<String, Schema> extractedEnums = new HashMap<>();
        Map<String, String> enumHashToName = new HashMap<>();

        log.info("Starting enum extraction from {} schemas", schemas.size());

        // Process all schemas to find and extract enums
        schemas.forEach((schemaName, schema) -> {
            processSchemaForEnums(schema, extractedEnums, enumHashToName, schemaName);
        });

        // Add extracted enums to components
        if (!extractedEnums.isEmpty()) {
            extractedEnums.forEach((enumName, enumSchema) -> {
                if (!schemas.containsKey(enumName)) {
                    schemas.put(enumName, enumSchema);
                    log.info("Added enum schema: {} with {} values", enumName,
                        enumSchema.getEnum() != null ? enumSchema.getEnum().size() : 0);
                }
            });
            log.info("Extracted {} enum schemas", extractedEnums.size());
        }
    }

    /**
     * Recursively process a schema to find and extract enum definitions.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processSchemaForEnums(Schema schema, Map<String, Schema> extractedEnums,
                                       Map<String, String> enumHashToName, String contextName) {
        if (schema == null) {
            return;
        }

        // Process properties
        if (schema.getProperties() != null) {
            Map<String, Schema> properties = schema.getProperties();
            properties.forEach((propName, propSchema) -> {
                processPropertyEnum(propName, propSchema, extractedEnums, enumHashToName);
                processSchemaForEnums(propSchema, extractedEnums, enumHashToName, propName);
            });
        }

        // Process array items
        if (schema.getItems() != null) {
            processSchemaForEnums(schema.getItems(), extractedEnums, enumHashToName, contextName);
        }

        // Process allOf, oneOf, anyOf
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForEnums((Schema) s, extractedEnums, enumHashToName, contextName);
                }
            });
        }

        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForEnums((Schema) s, extractedEnums, enumHashToName, contextName);
                }
            });
        }

        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForEnums((Schema) s, extractedEnums, enumHashToName, contextName);
                }
            });
        }

        // Process additionalProperties
        if (schema.getAdditionalProperties() instanceof Schema) {
            processSchemaForEnums((Schema) schema.getAdditionalProperties(),
                extractedEnums, enumHashToName, contextName);
        }
    }

    /**
     * Process a property that might be an enum and extract it if necessary.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processPropertyEnum(String propertyName, Schema propertySchema,
                                     Map<String, Schema> extractedEnums,
                                     Map<String, String> enumHashToName) {
        // Skip if already a reference or no enum values
        if (propertySchema == null || propertySchema.get$ref() != null ||
            propertySchema.getEnum() == null || propertySchema.getEnum().isEmpty()) {
            return;
        }

        List<Object> enumValues = propertySchema.getEnum();
        String enumName = determineEnumName(propertyName, enumValues, enumHashToName);

        // Check if this enum has already been extracted
        if (!extractedEnums.containsKey(enumName)) {
            // Create the enum schema
            Schema enumSchema = new Schema();
            enumSchema.setType(propertySchema.getType());
            enumSchema.setEnum(enumValues);

            // Copy description if available
            if (propertySchema.getDescription() != null) {
                enumSchema.setDescription(propertySchema.getDescription());
            }

            extractedEnums.put(enumName, enumSchema);
            log.debug("Extracted enum '{}' from property '{}' with values: {}",
                enumName, propertyName, enumValues);
        }

        // Replace the inline enum with a reference
        propertySchema.setEnum(null);
        propertySchema.set$ref("#/components/schemas/" + enumName);

        // Clean up properties that are now in the referenced schema
        propertySchema.setTitle(null);
        if (propertySchema.getDescription() != null) {
            propertySchema.setDescription(null);
        }
    }

    /**
     * Determine the name for an enum schema based on property name and values.
     */
    private String determineEnumName(String propertyName, List<Object> enumValues,
                                     Map<String, String> enumHashToName) {
        // Create a hash of the enum values to detect duplicates
        String enumHash = enumValues.stream()
            .map(Object::toString)
            .sorted()
            .collect(Collectors.joining(","));

        // Check if we've seen these exact values before
        if (enumHashToName.containsKey(enumHash)) {
            return enumHashToName.get(enumHash);
        }

        // Generate name based on property name or values
        String enumName = generateEnumName(propertyName, enumValues);
        enumHashToName.put(enumHash, enumName);

        return enumName;
    }

    /**
     * Generate a suitable name for an enum based on property name and values.
     */
    private String generateEnumName(String propertyName, List<Object> enumValues) {
        // Special case for common enums
        if (propertyName.equalsIgnoreCase("type") &&
            enumValues.contains("SUCCESS") && enumValues.contains("ERROR")) {
            return "ResponseType";
        }

        // Convert property name to PascalCase
        if (propertyName != null && !propertyName.isEmpty()) {
            return Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        }

        // Fallback to generic name
        return "GeneratedEnum" + Math.abs(enumValues.hashCode());
    }
}