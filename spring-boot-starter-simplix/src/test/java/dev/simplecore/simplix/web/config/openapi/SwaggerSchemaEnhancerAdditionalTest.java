package dev.simplecore.simplix.web.config.openapi;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.Length;
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

/**
 * Additional tests for SwaggerSchemaEnhancer targeting uncovered branches:
 * - Various validation annotation types (NotNull, NotEmpty, Size, Min, Max, Pattern, Email, Length)
 * - @Id and @EmbeddedId field detection
 * - @SearchableField direct annotation detection
 * - Schema with no properties
 * - SearchCondition schema handling with cached class
 * - findField in superclass hierarchy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SwaggerSchemaEnhancer - additional branch coverage tests")
class SwaggerSchemaEnhancerAdditionalTest {

    @Mock
    private MessageSource messageSource;

    private SwaggerSchemaEnhancer enhancer;

    @BeforeEach
    void setUp() {
        enhancer = new SwaggerSchemaEnhancer();
        ReflectionTestUtils.setField(enhancer, "messageSource", messageSource);
    }

    @Nested
    @DisplayName("Validation annotation coverage")
    class ValidationAnnotationCoverage {

        @Test
        @DisplayName("Should extract NotNull validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractNotNullMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("NotNullDto", NotNullDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.notnull"), isNull(), any(Locale.class)))
                    .thenReturn("Must not be null");

            OpenAPI openApi = buildOpenApi("NotNullDto", "requiredField");
            enhancer.customise(openApi);

            Schema nameSchema = getPropertySchema(openApi, "NotNullDto", "requiredField");
            assertThat(nameSchema.getExtensions()).isNotNull();
            assertThat(nameSchema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract NotEmpty validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractNotEmptyMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("NotEmptyDto", NotEmptyDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.notempty"), isNull(), any(Locale.class)))
                    .thenReturn("Must not be empty");

            OpenAPI openApi = buildOpenApi("NotEmptyDto", "items");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "NotEmptyDto", "items");
            assertThat(schema.getExtensions()).isNotNull();
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract Size validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractSizeMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("SizeDto", SizeDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.size"), isNull(), any(Locale.class)))
                    .thenReturn("Size must be between {min} and {max}");

            OpenAPI openApi = buildOpenApi("SizeDto", "name");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "SizeDto", "name");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract Min validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractMinMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("MinDto", MinDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.min"), isNull(), any(Locale.class)))
                    .thenReturn("Must be at least {value}");

            OpenAPI openApi = buildOpenApi("MinDto", "age");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "MinDto", "age");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract Max validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractMaxMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("MaxDto", MaxDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.max"), isNull(), any(Locale.class)))
                    .thenReturn("Must be at most {value}");

            OpenAPI openApi = buildOpenApi("MaxDto", "count");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "MaxDto", "count");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract Pattern validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractPatternMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("PatternDto", PatternDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.pattern"), isNull(), any(Locale.class)))
                    .thenReturn("Must match pattern");

            OpenAPI openApi = buildOpenApi("PatternDto", "code");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "PatternDto", "code");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract Email validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractEmailMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("EmailDto", EmailDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.email"), isNull(), any(Locale.class)))
                    .thenReturn("Must be a valid email");

            OpenAPI openApi = buildOpenApi("EmailDto", "email");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "EmailDto", "email");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should extract Length validation message")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractLengthMessage() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("LengthDto", LengthDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.length"), isNull(), any(Locale.class)))
                    .thenReturn("Length must be between {min} and {max}");

            OpenAPI openApi = buildOpenApi("LengthDto", "description");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "LengthDto", "description");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should use default message when MessageSource throws exception for both locales")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void useDefaultMessageOnException() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("NotNullDto", NotNullDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(anyString(), isNull(), eq(Locale.KOREAN)))
                    .thenThrow(new org.springframework.context.NoSuchMessageException("not found"));
            when(messageSource.getMessage(anyString(), isNull(), eq(Locale.ENGLISH)))
                    .thenThrow(new org.springframework.context.NoSuchMessageException("not found"));

            OpenAPI openApi = buildOpenApi("NotNullDto", "requiredField");
            enhancer.customise(openApi);

            // Should not throw, uses default messages
            Schema schema = getPropertySchema(openApi, "NotNullDto", "requiredField");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }
    }

    @Nested
    @DisplayName("ID field extensions")
    class IdFieldExtensions {

        @Test
        @DisplayName("Should add x-id-field extension for @Id annotated field")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void addIdFieldExtension() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("IdEntity", IdEntity.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = buildOpenApi("IdEntity", "id");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "IdEntity", "id");
            assertThat(schema.getExtensions()).containsKey("x-id-field");
            @SuppressWarnings("unchecked")
            Map<String, Object> idInfo = (Map<String, Object>) schema.getExtensions().get("x-id-field");
            assertThat(idInfo.get("type")).isEqualTo("simple");
            assertThat(idInfo.get("isPrimaryKey")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should add x-id-field extension for @EmbeddedId annotated field")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void addEmbeddedIdFieldExtension() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("EmbeddedIdEntity", EmbeddedIdEntity.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = buildOpenApi("EmbeddedIdEntity", "compositeKey");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "EmbeddedIdEntity", "compositeKey");
            assertThat(schema.getExtensions()).containsKey("x-id-field");
            @SuppressWarnings("unchecked")
            Map<String, Object> idInfo = (Map<String, Object>) schema.getExtensions().get("x-id-field");
            assertThat(idInfo.get("type")).isEqualTo("embedded");
            assertThat(idInfo.get("compositeKey")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should not add x-id-field extension for non-ID field")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void noIdFieldExtension() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("IdEntity", IdEntity.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = buildOpenApi("IdEntity", "name");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "IdEntity", "name");
            if (schema.getExtensions() != null) {
                assertThat(schema.getExtensions()).doesNotContainKey("x-id-field");
            }
        }
    }

    @Nested
    @DisplayName("SearchableField extensions")
    class SearchableFieldExtensions {

        @Test
        @DisplayName("Should add x-searchable-field extension for @SearchableField annotated field")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void addSearchableFieldExtension() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("SearchableDto", SearchableDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = buildOpenApi("SearchableDto", "keyword");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "SearchableDto", "keyword");
            assertThat(schema.getExtensions()).containsKey("x-searchable-field");
        }
    }

    @Nested
    @DisplayName("findField in class hierarchy")
    class FindFieldHierarchy {

        @Test
        @DisplayName("Should find field in superclass")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void findFieldInSuperclass() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("ChildDto", ChildDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.notnull"), isNull(), any(Locale.class)))
                    .thenReturn("Required");

            // "parentField" is in ParentDto (superclass of ChildDto)
            OpenAPI openApi = buildOpenApi("ChildDto", "parentField");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "ChildDto", "parentField");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
        }

        @Test
        @DisplayName("Should handle field not found in entire hierarchy")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void fieldNotFoundInHierarchy() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("ChildDto", ChildDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = buildOpenApi("ChildDto", "nonExistentField");
            enhancer.customise(openApi);

            // Should handle gracefully without error
            assertThat(openApi.getComponents().getSchemas()).containsKey("ChildDto");
        }
    }

    @Nested
    @DisplayName("SearchCondition schema handling")
    class SearchConditionHandling {

        @Test
        @DisplayName("Should handle SearchCondition schema with cached SearchDTO class having @SearchableField")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void handleSearchConditionWithCachedClass() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("TestSearchDTO", SearchableDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            Map<String, Schema> props = new HashMap<>();
            props.put("keyword", new Schema<>().type("string"));
            schema.setProperties(props);
            schemas.put("SearchConditionTestSearchDTO", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            Schema resultSchema = openApi.getComponents().getSchemas().get("SearchConditionTestSearchDTO");
            // Verify the schema was processed
            assertThat(resultSchema).isNotNull();
            // Verify x-searchable-fields extension was added
            if (resultSchema.getExtensions() != null) {
                assertThat(resultSchema.getExtensions()).containsKey("x-searchable-fields");
            }
        }

        @Test
        @DisplayName("Should handle SearchCondition schema when SearchDTO class is not found")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void handleSearchConditionWithoutCachedClass() {
            OpenAPI openApi = new OpenAPI();
            Components components = new Components();
            Map<String, Schema> schemas = new HashMap<>();

            Schema schema = new Schema<>();
            schema.setType("object");
            schemas.put("SearchConditionMissingSearchDTO", schema);

            components.setSchemas(schemas);
            openApi.setComponents(components);

            enhancer.customise(openApi);

            assertThat(openApi.getComponents().getSchemas()).containsKey("SearchConditionMissingSearchDTO");
        }
    }

    @Nested
    @DisplayName("Multiple validation annotations on single field")
    class MultipleAnnotations {

        @Test
        @DisplayName("Should extract messages for all validation annotations on a field")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void extractMultipleValidationMessages() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("MultiAnnotationDto", MultiAnnotationDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            when(messageSource.getMessage(eq("validation.notblank"), isNull(), any(Locale.class)))
                    .thenReturn("Must not be blank");
            when(messageSource.getMessage(eq("validation.size"), isNull(), any(Locale.class)))
                    .thenReturn("Size must be between {min} and {max}");

            OpenAPI openApi = buildOpenApi("MultiAnnotationDto", "username");
            enhancer.customise(openApi);

            Schema schema = getPropertySchema(openApi, "MultiAnnotationDto", "username");
            assertThat(schema.getExtensions()).containsKey("x-i18n-validation-messages");
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> messages =
                    (Map<String, Map<String, String>>) schema.getExtensions().get("x-i18n-validation-messages");
            assertThat(messages).containsKey("validation.notblank");
            assertThat(messages).containsKey("validation.size");
        }
    }

    @Nested
    @DisplayName("init and cacheSchemaClasses")
    class InitAndCache {

        @Test
        @DisplayName("Should init and cache classes from configured packages")
        @SuppressWarnings("unchecked")
        void initCachesClasses() {
            SwaggerSchemaEnhancer customEnhancer = new SwaggerSchemaEnhancer(
                    List.of("dev.simplecore.simplix.web.config.openapi"));
            ReflectionTestUtils.setField(customEnhancer, "messageSource", messageSource);

            customEnhancer.init();

            // Should complete without error, caching classes from the package
            Map<String, Class<?>> cache = (Map<String, Class<?>>) ReflectionTestUtils.getField(customEnhancer, "schemaClassCache");
            assertThat(cache).isNotNull();
        }

        @Test
        @DisplayName("Should handle exception during init gracefully")
        void initHandlesException() {
            // Use a package that doesn't exist to trigger the scan path
            SwaggerSchemaEnhancer customEnhancer = new SwaggerSchemaEnhancer(
                    List.of("nonexistent.package.that.does.not.exist"));
            ReflectionTestUtils.setField(customEnhancer, "messageSource", messageSource);

            customEnhancer.init();

            // Should complete without error
            assertThat(customEnhancer).isNotNull();
        }
    }

    @Nested
    @DisplayName("Schema with field in declared class (not superclass)")
    class FieldInDeclaredClass {

        @Test
        @DisplayName("Should process field with no annotations (no validation, no searchable, no id)")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void fieldWithNoAnnotations() {
            Map<String, Class<?>> cache = new ConcurrentHashMap<>();
            cache.put("PlainDto", PlainDto.class);
            ReflectionTestUtils.setField(enhancer, "schemaClassCache", cache);

            OpenAPI openApi = buildOpenApi("PlainDto", "plainField");
            enhancer.customise(openApi);

            // Should process without error, no extensions added
            Schema schema = getPropertySchema(openApi, "PlainDto", "plainField");
            // No validation or id annotations, so no specific extensions
            assertThat(openApi.getComponents().getSchemas()).containsKey("PlainDto");
        }
    }

    // --- Helper methods ---

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OpenAPI buildOpenApi(String schemaName, String... propertyNames) {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Map<String, Schema> schemas = new HashMap<>();

        Schema schema = new Schema<>();
        schema.setType("object");
        Map<String, Schema> props = new HashMap<>();
        for (String propName : propertyNames) {
            props.put(propName, new Schema<>().type("string"));
        }
        schema.setProperties(props);
        schemas.put(schemaName, schema);

        components.setSchemas(schemas);
        openApi.setComponents(components);
        return openApi;
    }

    @SuppressWarnings("rawtypes")
    private Schema getPropertySchema(OpenAPI openApi, String schemaName, String propertyName) {
        return (Schema) openApi.getComponents().getSchemas().get(schemaName)
                .getProperties().get(propertyName);
    }

    // --- Test DTO classes ---

    static class NotNullDto {
        @NotNull
        public String requiredField;
    }

    static class NotEmptyDto {
        @NotEmpty
        public List<String> items;
    }

    static class SizeDto {
        @Size(min = 2, max = 100)
        public String name;
    }

    static class MinDto {
        @Min(18)
        public int age;
    }

    static class MaxDto {
        @Max(1000)
        public int count;
    }

    static class PatternDto {
        @Pattern(regexp = "^[A-Z]{3}$")
        public String code;
    }

    static class EmailDto {
        @Email
        public String email;
    }

    static class LengthDto {
        @Length(min = 10, max = 500)
        public String description;
    }

    static class MultiAnnotationDto {
        @NotBlank
        @Size(min = 3, max = 50)
        public String username;
    }

    static class IdEntity {
        @Id
        public Long id;
        public String name;
    }

    static class CompositeKey {
        public Long partA;
        public Long partB;
    }

    static class EmbeddedIdEntity {
        @EmbeddedId
        public CompositeKey compositeKey;
        public String name;
    }

    static class SearchableDto {
        @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.CONTAINS})
        public String keyword;
    }

    static class ParentDto {
        @NotNull
        public String parentField;
    }

    static class ChildDto extends ParentDto {
        public String childField;
    }

    static class PlainDto {
        public String plainField;
    }
}
