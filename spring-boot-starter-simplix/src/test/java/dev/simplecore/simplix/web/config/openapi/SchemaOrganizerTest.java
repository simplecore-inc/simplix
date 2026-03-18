package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchemaOrganizer - sorts schemas and marks enums")
class SchemaOrganizerTest {

    private SchemaOrganizer organizer;

    @BeforeEach
    void setUp() {
        organizer = new SchemaOrganizer();
    }

    @Test
    @DisplayName("Should sort schemas alphabetically (case-insensitive)")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sortSchemasAlphabetically() {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();
        schemas.put("Zebra", new Schema<>());
        schemas.put("Apple", new Schema<>());
        schemas.put("Mango", new Schema<>());
        schemas.put("banana", new Schema<>());
        components.setSchemas(schemas);
        openApi.setComponents(components);

        organizer.customise(openApi);

        Map<String, Schema> sorted = openApi.getComponents().getSchemas();
        assertThat(sorted.keySet().stream().toList())
                .containsExactly("Apple", "banana", "Mango", "Zebra");
    }

    @Test
    @DisplayName("Should mark enum schemas with x-schema-type extension")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void markEnumSchemas() {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema enumSchema = new Schema<>();
        enumSchema.setEnum(List.of("SUCCESS", "ERROR"));
        schemas.put("Status", enumSchema);

        Schema regularSchema = new Schema<>();
        regularSchema.setType("string");
        schemas.put("Name", regularSchema);

        components.setSchemas(schemas);
        openApi.setComponents(components);

        organizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas().get("Status").getExtensions())
                .containsEntry("x-schema-type", "enum");
        Schema nameSchema = openApi.getComponents().getSchemas().get("Name");
        assertThat(nameSchema.getExtensions()).isNull();
    }

    @Test
    @DisplayName("Should handle null components gracefully")
    void nullComponents() {
        OpenAPI openApi = new OpenAPI();

        // Should not throw
        organizer.customise(openApi);

        assertThat(openApi.getComponents()).isNull();
    }

    @Test
    @DisplayName("Should handle null schemas gracefully")
    void nullSchemas() {
        OpenAPI openApi = new OpenAPI();
        openApi.setComponents(new Components());

        // Should not throw
        organizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).isNull();
    }

    @Test
    @DisplayName("Should run at LOWEST_PRECEDENCE order")
    void lowestPrecedence() {
        assertThat(organizer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
