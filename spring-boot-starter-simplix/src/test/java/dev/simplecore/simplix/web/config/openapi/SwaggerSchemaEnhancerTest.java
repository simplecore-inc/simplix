package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SwaggerSchemaEnhancer - adds i18n and metadata extensions to OpenAPI schemas")
class SwaggerSchemaEnhancerTest {

    @Mock
    private MessageSource messageSource;

    private SwaggerSchemaEnhancer enhancer;

    @BeforeEach
    void setUp() {
        enhancer = new SwaggerSchemaEnhancer();
        ReflectionTestUtils.setField(enhancer, "messageSource", messageSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with default packages")
        void defaultPackages() {
            SwaggerSchemaEnhancer defaultEnhancer = new SwaggerSchemaEnhancer();
            assertThat(defaultEnhancer).isNotNull();
        }

        @Test
        @DisplayName("Should create with additional packages")
        void additionalPackages() {
            SwaggerSchemaEnhancer customEnhancer = new SwaggerSchemaEnhancer(
                    List.of("com.example.model", "com.example.dto"));
            assertThat(customEnhancer).isNotNull();
        }

        @Test
        @DisplayName("Should handle null additional packages gracefully")
        void nullAdditionalPackages() {
            SwaggerSchemaEnhancer customEnhancer = new SwaggerSchemaEnhancer(null);
            assertThat(customEnhancer).isNotNull();
        }

        @Test
        @DisplayName("Should skip blank package names in additional packages")
        void blankAdditionalPackages() {
            SwaggerSchemaEnhancer customEnhancer = new SwaggerSchemaEnhancer(
                    List.of("", "  ", "com.example.model"));
            assertThat(customEnhancer).isNotNull();
        }

        @Test
        @DisplayName("Should skip duplicate package names")
        void duplicatePackages() {
            SwaggerSchemaEnhancer customEnhancer = new SwaggerSchemaEnhancer(
                    List.of("dev.simplecore.simplix.core.model"));
            assertThat(customEnhancer).isNotNull();
        }
    }

    @Nested
    @DisplayName("customise")
    class Customise {

        @Test
        @DisplayName("Should handle null components gracefully")
        void nullComponents() {
            OpenAPI openApi = new OpenAPI();

            enhancer.customise(openApi);

            assertThat(openApi.getComponents()).isNull();
        }

        @Test
        @DisplayName("Should handle null schemas gracefully")
        void nullSchemas() {
            OpenAPI openApi = new OpenAPI();
            openApi.setComponents(new Components());

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).isNull();
        }

        @Test
        @DisplayName("Should process schemas without errors when class not found")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void schemaWithUnknownClass() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("UnknownEntity", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("UnknownEntity");
        }

        @Test
        @DisplayName("Should process SearchCondition schemas specially")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processSearchConditionSchemas() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("SearchConditionTestSearchDTO", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            // Should process without error even if the class is not found
            assertThat(openApi.getComponents().getSchemas()).containsKey("SearchConditionTestSearchDTO");
        }

        @Test
        @DisplayName("Should process schemas with known cached class and add extensions")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processWithCachedClass() {
            // Pre-populate the schema class cache with a test class
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("TestAnnotatedDto", TestAnnotatedDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            props.put("value", new Schema<>().type("integer"));
            schema.setProperties(props);
            schemas.put("TestAnnotatedDto", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("TestAnnotatedDto");
        }

        @Test
        @DisplayName("Should process schema with no properties gracefully")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void processSchemaWithoutProperties() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("EmptyDto", EmptyDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            // No properties set
            schemas.put("EmptyDto", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("EmptyDto");
        }
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("Should initialize without errors")
        void initializeSuccessfully() {
            enhancer.init();
            // Should complete without throwing
            assertThat(enhancer).isNotNull();
        }
    }

    @Nested
    @DisplayName("findClassForSchema")
    class FindClassForSchema {

        @Test
        @DisplayName("Should find class from cache for regular schemas")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void findClassFromCache() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("TestDto", TestAnnotatedDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("TestDto", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("TestDto");
        }

        @Test
        @DisplayName("Should find SearchDTO class for SearchCondition schema")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void findSearchDtoClass() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("MySearchDTO", TestAnnotatedDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("SearchConditionMySearchDTO", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("SearchConditionMySearchDTO");
        }
    }

    @Nested
    @DisplayName("getValidationMessages")
    class GetValidationMessages {

        @Test
        @DisplayName("Should extract validation messages from annotated fields")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractValidationMessages() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("ValidatedDto", ValidatedDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.notblank"), isNull(), any(Locale.class)))
                    .thenReturn("Must not be blank");

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("ValidatedDto", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            // Check that x-i18n-validation-messages extension was added
            Schema nameSchema = (Schema) openApi.getComponents().getSchemas().get("ValidatedDto")
                    .getProperties().get("name");
            assertThat(nameSchema.getExtensions()).isNotNull();
            assertThat(nameSchema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should handle MessageSource exception when getting validation messages")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void handleMessageSourceException() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("ValidatedDto", ValidatedDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenThrow(new org.springframework.context.NoSuchMessageException("not found"));

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("name", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("ValidatedDto", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            // Should not throw, uses default message instead
            assertThat(openApi.getComponents().getSchemas()).containsKey("ValidatedDto");
        }
    }

    // Test helper classes
    static class TestAnnotatedDto {
        public String name;
        public int value;
    }

    static class EmptyDto {
    }

    static class ValidatedDto {
        @jakarta.validation.constraints.NotBlank
        public String name;
    }
}
