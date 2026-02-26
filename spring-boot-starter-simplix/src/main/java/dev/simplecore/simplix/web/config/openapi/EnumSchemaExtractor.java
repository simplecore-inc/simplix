package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAPI customizer that extracts inline enum definitions to separate reusable schemas.
 * This improves API documentation by creating named enum types that can be referenced
 * throughout the documentation.
 *
 * <p>NOTE: This customizer is ENABLED by default. To disable:
 * <pre>
 * simplix.swagger.customizers.enum-extractor.enabled=false
 * </pre>
 */
public class EnumSchemaExtractor implements OpenApiCustomizer, Ordered {

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
            processSchemaForEnums(schema, extractedEnums, enumHashToName, schemas, schemaName);
        });

        // Add extracted enums to components
        if (!extractedEnums.isEmpty()) {
            extractedEnums.forEach((enumName, enumSchema) -> {
                schemas.put(enumName, enumSchema);
                log.info("Added enum schema: {} with {} values", enumName,
                    enumSchema.getEnum() != null ? enumSchema.getEnum().size() : 0);
            });
            log.info("Extracted {} enum schemas", extractedEnums.size());
        }
    }

    /**
     * Recursively process a schema to find and extract enum definitions.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processSchemaForEnums(Schema schema, Map<String, Schema> extractedEnums,
                                       Map<String, String> enumHashToName,
                                       Map<String, Schema> existingSchemas, String contextName) {
        if (schema == null) {
            return;
        }

        // Process properties
        if (schema.getProperties() != null) {
            Map<String, Schema> properties = schema.getProperties();
            properties.forEach((propName, propSchema) -> {
                processPropertyEnum(propName, propSchema, extractedEnums, enumHashToName, existingSchemas);
                processSchemaForEnums(propSchema, extractedEnums, enumHashToName, existingSchemas, propName);
            });
        }

        // Process array items
        if (schema.getItems() != null) {
            processSchemaForEnums(schema.getItems(), extractedEnums, enumHashToName, existingSchemas, contextName);
        }

        // Process allOf, oneOf, anyOf
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForEnums((Schema) s, extractedEnums, enumHashToName, existingSchemas, contextName);
                }
            });
        }

        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForEnums((Schema) s, extractedEnums, enumHashToName, existingSchemas, contextName);
                }
            });
        }

        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(s -> {
                if (s instanceof Schema) {
                    processSchemaForEnums((Schema) s, extractedEnums, enumHashToName, existingSchemas, contextName);
                }
            });
        }

        // Process additionalProperties
        if (schema.getAdditionalProperties() instanceof Schema) {
            processSchemaForEnums((Schema) schema.getAdditionalProperties(),
                extractedEnums, enumHashToName, existingSchemas, contextName);
        }
    }

    /**
     * Process a property that might be an enum and extract it if necessary.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processPropertyEnum(String propertyName, Schema propertySchema,
                                     Map<String, Schema> extractedEnums,
                                     Map<String, String> enumHashToName,
                                     Map<String, Schema> existingSchemas) {
        // Skip if already a reference or no enum values
        if (propertySchema == null || propertySchema.get$ref() != null ||
            propertySchema.getEnum() == null || propertySchema.getEnum().isEmpty()) {
            return;
        }

        List<Object> enumValues = propertySchema.getEnum();
        String enumName = determineEnumName(propertyName, enumValues, enumHashToName);

        // Resolve name collisions: if the name is taken by a different enum or existing DTO schema,
        // append a numeric suffix to ensure uniqueness
        String resolvedName = resolveUniqueEnumName(enumName, enumValues, extractedEnums, existingSchemas);

        // Check if this enum has already been extracted (same name, same values)
        if (!extractedEnums.containsKey(resolvedName)) {
            Schema enumSchema = new Schema();
            enumSchema.setType(propertySchema.getType());
            enumSchema.setEnum(enumValues);

            if (propertySchema.getDescription() != null) {
                enumSchema.setDescription(propertySchema.getDescription());
            }

            extractedEnums.put(resolvedName, enumSchema);
            log.trace("Extracted enum '{}' from property '{}' with values: {}",
                resolvedName, propertyName, enumValues);
        }

        // Replace the inline enum with a reference
        propertySchema.setEnum(null);
        propertySchema.set$ref("#/components/schemas/" + resolvedName);

        // Clean up properties that are now in the referenced schema.
        // When $ref is set, sibling properties must be cleared per OpenAPI 3.0 spec.
        propertySchema.setType(null);
        propertySchema.setTitle(null);
        if (propertySchema.getDescription() != null) {
            propertySchema.setDescription(null);
        }
    }

    /**
     * Resolve a unique schema name for an enum, avoiding collisions with
     * existing DTO schemas or previously extracted enums with different values.
     */
    @SuppressWarnings("rawtypes")
    private String resolveUniqueEnumName(String baseName, List<Object> enumValues,
                                          Map<String, Schema> extractedEnums,
                                          Map<String, Schema> existingSchemas) {
        String candidate = baseName;
        int suffix = 1;

        while (true) {
            // Check collision with existing DTO schemas (non-enum)
            if (existingSchemas.containsKey(candidate) && !extractedEnums.containsKey(candidate)) {
                candidate = baseName + suffix++;
                continue;
            }

            // Check collision with previously extracted enum having different values
            Schema existing = extractedEnums.get(candidate);
            if (existing != null && !hasSameEnumValues(existing, enumValues)) {
                candidate = baseName + suffix++;
                continue;
            }

            return candidate;
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean hasSameEnumValues(Schema schema, List<Object> values) {
        if (schema.getEnum() == null) {
            return false;
        }
        return schema.getEnum().size() == values.size()
                && schema.getEnum().containsAll(values);
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

    @Override
    public int getOrder() {
        // Run before NestedObjectSchemaExtractor (LOWEST_PRECEDENCE - 100)
        return Ordered.LOWEST_PRECEDENCE - 200;
    }
}
