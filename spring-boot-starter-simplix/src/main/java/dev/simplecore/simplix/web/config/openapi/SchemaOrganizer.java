package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.Ordered;

import java.util.Map;
import java.util.TreeMap;

/**
 * OpenAPI customizer that organizes component schemas:
 * <ul>
 *   <li>Sorts schemas alphabetically (A-Z)</li>
 *   <li>Marks enum schemas with {@code x-schema-type: "enum"} extension</li>
 * </ul>
 *
 * <p>Runs last to ensure all other customizers have finished adding/modifying schemas.
 */
public class SchemaOrganizer implements OpenApiCustomizer, Ordered {

    @Override
    @SuppressWarnings("rawtypes")
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemas = openApi.getComponents().getSchemas();

        // Mark enum schemas with x-schema-type extension
        schemas.forEach((name, schema) -> {
            if (isEnumSchema(schema)) {
                schema.addExtension("x-schema-type", "enum");
            }
        });

        // Sort schemas alphabetically using case-insensitive ordering
        TreeMap<String, Schema> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(schemas);
        openApi.getComponents().setSchemas(sorted);
    }

    @SuppressWarnings("rawtypes")
    private boolean isEnumSchema(Schema schema) {
        return schema.getEnum() != null && !schema.getEnum().isEmpty();
    }

    @Override
    public int getOrder() {
        // Run after all other customizers
        return Ordered.LOWEST_PRECEDENCE;
    }
}
