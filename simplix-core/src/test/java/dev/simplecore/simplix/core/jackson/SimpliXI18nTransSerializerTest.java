package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.simplecore.simplix.core.jackson.annotation.I18nTrans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimpliXI18nTransSerializer Tests")
class SimpliXI18nTransSerializerTest {

    private ObjectMapper objectMapper;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        originalLocale = LocaleContextHolder.getLocale();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.setLocale(originalLocale);
    }

    // ===========================================
    // Simple Mode Tests
    // ===========================================

    @Nested
    @DisplayName("Simple Mode Tests")
    class SimpleModeTests {

        @Test
        @DisplayName("Should translate field using exact locale match")
        void shouldTranslateUsingExactLocaleMatch() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);
            SimpleDto dto = new SimpleDto();
            dto.setName("Default Name");
            dto.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Korean Name\""));
        }

        @Test
        @DisplayName("Should translate field using language code fallback")
        void shouldTranslateUsingLanguageCodeFallback() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREA); // ko_KR
            SimpleDto dto = new SimpleDto();
            dto.setName("Default Name");
            dto.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Korean Name\""));
        }

        @Test
        @DisplayName("Should use default locale when current locale not found")
        void shouldUseDefaultLocaleWhenCurrentNotFound() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.FRENCH); // Not in map
            SimpleDto dto = new SimpleDto();
            dto.setName("Default Name");
            dto.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            // Should fallback to "en" (default locale in config)
            assertTrue(json.contains("\"name\":\"English Name\""));
        }

        @Test
        @DisplayName("Should return original value when i18n map is null")
        void shouldReturnOriginalValueWhenI18nMapIsNull() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);
            SimpleDto dto = new SimpleDto();
            dto.setName("Original Name");
            dto.setNameI18n(null);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Original Name\""));
        }

        @Test
        @DisplayName("Should return original value when i18n map is empty")
        void shouldReturnOriginalValueWhenI18nMapIsEmpty() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);
            SimpleDto dto = new SimpleDto();
            dto.setName("Original Name");
            dto.setNameI18n(new HashMap<>());

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Original Name\""));
        }

        @Test
        @DisplayName("Should preserve null field value when field is null")
        void shouldPreserveNullFieldValue() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);
            SimpleDto dto = new SimpleDto();
            dto.setName(null);
            dto.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            // Null field values are preserved as null (no translation applied to null)
            assertTrue(json.contains("\"name\":null"));
        }
    }

    // ===========================================
    // Nested Mode Tests
    // ===========================================

    @Nested
    @DisplayName("Nested Mode Tests")
    class NestedModeTests {

        @Test
        @DisplayName("Should translate nested object field using dot notation")
        void shouldTranslateNestedObjectField() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            TagGroup tagGroup = new TagGroup();
            tagGroup.setId(1L);
            tagGroup.setName("Default Tag Group");
            tagGroup.setNameI18n(createI18nMap("en", "English Tag Group", "ko", "Korean Tag Group"));

            NestedDto dto = new NestedDto();
            dto.setTagGroup(tagGroup);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Korean Tag Group\""));
            assertTrue(json.contains("\"id\":1"));
        }

        @Test
        @DisplayName("Should handle nested object with English locale")
        void shouldTranslateNestedObjectFieldWithEnglishLocale() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            TagGroup tagGroup = new TagGroup();
            tagGroup.setId(1L);
            tagGroup.setName("Default Tag Group");
            tagGroup.setNameI18n(createI18nMap("en", "English Tag Group", "ko", "Korean Tag Group"));

            NestedDto dto = new NestedDto();
            dto.setTagGroup(tagGroup);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"English Tag Group\""));
        }

        @Test
        @DisplayName("Should translate correctly when locale changes on same object")
        void shouldTranslateCorrectlyWhenLocaleChanges() throws JsonProcessingException {
            // Given - create object once, serialize multiple times with different locales
            TagGroup tagGroup = new TagGroup();
            tagGroup.setId(1L);
            tagGroup.setName("Default Tag Group");
            tagGroup.setNameI18n(createI18nMap("en", "English Tag Group", "ko", "Korean Tag Group"));

            NestedDto dto = new NestedDto();
            dto.setTagGroup(tagGroup);

            // When - serialize with Korean
            LocaleContextHolder.setLocale(Locale.KOREAN);
            String jsonKo = objectMapper.writeValueAsString(dto);
            System.out.println("Korean locale JSON: " + jsonKo);

            // When - serialize with English (same object!)
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            String jsonEn = objectMapper.writeValueAsString(dto);
            System.out.println("English locale JSON: " + jsonEn);

            // Then - both should have correct translations
            assertTrue(jsonKo.contains("\"name\":\"Korean Tag Group\""),
                    "Korean serialization should contain Korean translation");
            assertTrue(jsonEn.contains("\"name\":\"English Tag Group\""),
                    "English serialization should contain English translation");
        }

        @Test
        @DisplayName("Should return null when nested object is null")
        void shouldReturnNullWhenNestedObjectIsNull() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);
            NestedDto dto = new NestedDto();
            dto.setTagGroup(null);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"tagGroup\":null"));
        }

        @Test
        @DisplayName("Should preserve nested object when i18n map is null")
        void shouldPreserveNestedObjectWhenI18nMapIsNull() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            TagGroup tagGroup = new TagGroup();
            tagGroup.setId(1L);
            tagGroup.setName("Original Name");
            tagGroup.setNameI18n(null);

            NestedDto dto = new NestedDto();
            dto.setTagGroup(tagGroup);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Original Name\""));
            assertTrue(json.contains("\"id\":1"));
        }

        @Test
        @DisplayName("Should handle deeply nested source path")
        void shouldHandleDeeplyNestedSourcePath() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            InnerTagGroup innerTagGroup = new InnerTagGroup();
            innerTagGroup.setName("Default Inner Tag Group");
            innerTagGroup.setNameI18n(createI18nMap("en", "English Inner Tag Group", "ko", "Korean Inner Tag Group"));

            OuterContainer outer = new OuterContainer();
            outer.setInner(innerTagGroup);

            DeepNestedDto dto = new DeepNestedDto();
            dto.setContainer(outer);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Korean Inner Tag Group\""));
        }
    }

    // ===========================================
    // Repeatable Annotation Tests
    // ===========================================

    @Nested
    @DisplayName("Repeatable @I18nTrans Annotation Tests")
    class RepeatableAnnotationTests {

        @Test
        @DisplayName("Should translate multiple nested fields with repeatable @I18nTrans")
        void shouldTranslateMultipleNestedFieldsWithRepeatableAnnotation() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            ProductTranslations translations = new ProductTranslations();
            translations.setName("Default Name");
            translations.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));
            translations.setDescription("Default Description");
            translations.setDescriptionI18n(createI18nMap("en", "English Description", "ko", "Korean Description"));
            translations.setCategory("Default Category");
            translations.setCategoryI18n(createI18nMap("en", "English Category", "ko", "Korean Category"));

            ProductWithRepeatableDto dto = new ProductWithRepeatableDto();
            dto.setTranslations(translations);

            // When
            String json = objectMapper.writeValueAsString(dto);
            System.out.println("Repeatable annotation JSON: " + json);

            // Then
            assertTrue(json.contains("\"name\":\"Korean Name\""),
                    "name should be translated to Korean");
            assertTrue(json.contains("\"description\":\"Korean Description\""),
                    "description should be translated to Korean");
            assertTrue(json.contains("\"category\":\"Korean Category\""),
                    "category should be translated to Korean");
        }

        @Test
        @DisplayName("Should translate multiple nested fields with English locale")
        void shouldTranslateMultipleNestedFieldsWithEnglishLocale() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            ProductTranslations translations = new ProductTranslations();
            translations.setName("Default Name");
            translations.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));
            translations.setDescription("Default Description");
            translations.setDescriptionI18n(createI18nMap("en", "English Description", "ko", "Korean Description"));

            ProductWithRepeatableDto dto = new ProductWithRepeatableDto();
            dto.setTranslations(translations);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"English Name\""));
            assertTrue(json.contains("\"description\":\"English Description\""));
        }

        @Test
        @DisplayName("Should correctly translate when locale changes on same object with repeatable annotations")
        void shouldTranslateCorrectlyWhenLocaleChangesWithRepeatableAnnotations() throws JsonProcessingException {
            // Given
            ProductTranslations translations = new ProductTranslations();
            translations.setName("Default Name");
            translations.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));
            translations.setDescription("Default Description");
            translations.setDescriptionI18n(createI18nMap("en", "English Description", "ko", "Korean Description"));

            ProductWithRepeatableDto dto = new ProductWithRepeatableDto();
            dto.setTranslations(translations);

            // When - serialize with Korean
            LocaleContextHolder.setLocale(Locale.KOREAN);
            String jsonKo = objectMapper.writeValueAsString(dto);
            System.out.println("Korean locale JSON (repeatable): " + jsonKo);

            // When - serialize with English (same object!)
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            String jsonEn = objectMapper.writeValueAsString(dto);
            System.out.println("English locale JSON (repeatable): " + jsonEn);

            // Then - both should have correct translations
            assertTrue(jsonKo.contains("\"name\":\"Korean Name\""));
            assertTrue(jsonKo.contains("\"description\":\"Korean Description\""));
            assertTrue(jsonEn.contains("\"name\":\"English Name\""));
            assertTrue(jsonEn.contains("\"description\":\"English Description\""));
        }

        @Test
        @DisplayName("Should handle partial i18n maps with repeatable annotations")
        void shouldHandlePartialI18nMapsWithRepeatableAnnotations() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            ProductTranslations translations = new ProductTranslations();
            translations.setName("Default Name");
            translations.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));
            translations.setDescription("Default Description");
            translations.setDescriptionI18n(null);  // null i18n map
            translations.setCategory("Default Category");
            translations.setCategoryI18n(new HashMap<>());  // empty i18n map

            ProductWithRepeatableDto dto = new ProductWithRepeatableDto();
            dto.setTranslations(translations);

            // When
            String json = objectMapper.writeValueAsString(dto);
            System.out.println("Partial i18n maps JSON: " + json);

            // Then
            assertTrue(json.contains("\"name\":\"Korean Name\""),
                    "name should be translated to Korean");
            assertTrue(json.contains("\"description\":\"Default Description\""),
                    "description should keep original value (null i18n map)");
            assertTrue(json.contains("\"category\":\"Default Category\""),
                    "category should keep original value (empty i18n map)");
        }
    }

    // ===========================================
    // Edge Case Tests
    // ===========================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return first available translation when locale not supported")
        void shouldReturnFirstAvailableWhenLocaleNotSupported() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(new Locale("xx")); // Unsupported locale
            SimpleDto dto = new SimpleDto();
            dto.setName("Default Name");
            // Only has "zh" which is not in supported locales list
            dto.setNameI18n(createI18nMap("zh", "Chinese Name"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Chinese Name\""));
        }

        @Test
        @DisplayName("Should handle underscore locale format")
        void shouldHandleUnderscoreLocaleFormat() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(new Locale("ko", "KR"));
            SimpleDto dto = new SimpleDto();
            dto.setName("Default Name");
            dto.setNameI18n(createI18nMap("ko_KR", "Korean KR Name", "ko", "Korean Name"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"name\":\"Korean KR Name\""));
        }

        @Test
        @DisplayName("Should serialize multiple i18n fields correctly")
        void shouldSerializeMultipleI18nFieldsCorrectly() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);
            MultiFieldDto dto = new MultiFieldDto();
            dto.setTitle("Default Title");
            dto.setTitleI18n(createI18nMap("en", "English Title", "ko", "Korean Title"));
            dto.setDescription("Default Description");
            dto.setDescriptionI18n(createI18nMap("en", "English Description", "ko", "Korean Description"));

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"title\":\"Korean Title\""));
            assertTrue(json.contains("\"description\":\"Korean Description\""));
        }

        @Test
        @DisplayName("Should preserve non-i18n fields in nested object")
        void shouldPreserveNonI18nFieldsInNestedObject() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            TagGroup tagGroup = new TagGroup();
            tagGroup.setId(999L);
            tagGroup.setCode("TAG_CODE");
            tagGroup.setName("Default Name");
            tagGroup.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            NestedDto dto = new NestedDto();
            dto.setTagGroup(tagGroup);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            assertTrue(json.contains("\"id\":999"));
            assertTrue(json.contains("\"code\":\"TAG_CODE\""));
            assertTrue(json.contains("\"name\":\"Korean Name\""));
        }

        @Test
        @DisplayName("Should work with @JsonIncludeProperties even when nameI18n is not included")
        void shouldWorkWithJsonIncludeProperties() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            TagGroupWithIncludeProps tagGroup = new TagGroupWithIncludeProps();
            tagGroup.setId(1L);
            tagGroup.setCode("TAG_CODE");
            tagGroup.setName("Default Name");
            tagGroup.setDescription("This should NOT appear in JSON");
            tagGroup.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            NestedWithIncludePropsDto dto = new NestedWithIncludePropsDto();
            dto.setTagGroup(tagGroup);

            // When
            String json = objectMapper.writeValueAsString(dto);

            // Then
            // nameI18n should NOT appear in JSON (due to @JsonIncludeProperties)
            assertFalse(json.contains("nameI18n"), "nameI18n should not appear in JSON");
            // description should NOT appear in JSON (not in @JsonIncludeProperties)
            assertFalse(json.contains("description"), "description should not appear in JSON");
            // But translation should still work via reflection
            assertTrue(json.contains("\"name\":\"Korean Name\""), "name should be translated");
            // Only included properties should appear
            assertTrue(json.contains("\"id\":1"), "id should appear");
            assertTrue(json.contains("\"code\":\"TAG_CODE\""), "code should appear");
        }

        @Test
        @DisplayName("Should respect field-level @JsonIncludeProperties in nested mode")
        void shouldRespectFieldLevelJsonIncludeProperties() throws JsonProcessingException {
            // Given
            LocaleContextHolder.setLocale(Locale.KOREAN);

            TagGroupNoClassAnnotation tagGroup = new TagGroupNoClassAnnotation();
            tagGroup.setId(1L);
            tagGroup.setCode("TAG_CODE");
            tagGroup.setName("Default Name");
            tagGroup.setDescription("This should NOT appear in JSON");
            tagGroup.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

            NestedWithFieldLevelIncludePropsDto dto = new NestedWithFieldLevelIncludePropsDto();
            dto.setTagGroup(tagGroup);

            // When
            String json = objectMapper.writeValueAsString(dto);
            System.out.println("Field-level @JsonIncludeProperties JSON: " + json);

            // Then
            // description should NOT appear in JSON (not in field-level @JsonIncludeProperties)
            assertFalse(json.contains("description"), "description should not appear in JSON");
            // nameI18n should NOT appear in JSON
            assertFalse(json.contains("nameI18n"), "nameI18n should not appear in JSON");
            // Translation should still work
            assertTrue(json.contains("\"name\":\"Korean Name\""), "name should be translated");
            // Only included properties should appear
            assertTrue(json.contains("\"id\":1"), "id should appear");
            assertTrue(json.contains("\"code\":\"TAG_CODE\""), "code should appear");
        }
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private Map<String, String> createI18nMap(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ===========================================
    // Test DTOs
    // ===========================================

    /**
     * Simple DTO for basic i18n translation tests.
     */
    static class SimpleDto {
        @I18nTrans(source = "nameI18n")
        private String name;

        @JsonIgnore
        private Map<String, String> nameI18n;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getNameI18n() {
            return nameI18n;
        }

        public void setNameI18n(Map<String, String> nameI18n) {
            this.nameI18n = nameI18n;
        }
    }

    /**
     * DTO with multiple i18n fields.
     */
    static class MultiFieldDto {
        @I18nTrans(source = "titleI18n")
        private String title;

        @JsonIgnore
        private Map<String, String> titleI18n;

        @I18nTrans(source = "descriptionI18n")
        private String description;

        @JsonIgnore
        private Map<String, String> descriptionI18n;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Map<String, String> getTitleI18n() {
            return titleI18n;
        }

        public void setTitleI18n(Map<String, String> titleI18n) {
            this.titleI18n = titleI18n;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, String> getDescriptionI18n() {
            return descriptionI18n;
        }

        public void setDescriptionI18n(Map<String, String> descriptionI18n) {
            this.descriptionI18n = descriptionI18n;
        }
    }

    /**
     * Nested object for nested mode tests.
     */
    static class TagGroup {
        private Long id;
        private String code;
        private String name;

        @JsonIgnore
        private Map<String, String> nameI18n;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getNameI18n() {
            return nameI18n;
        }

        public void setNameI18n(Map<String, String> nameI18n) {
            this.nameI18n = nameI18n;
        }
    }

    /**
     * DTO with nested object for nested mode tests.
     */
    static class NestedDto {
        @I18nTrans(source = "tagGroup.nameI18n", target = "tagGroup.name")
        private TagGroup tagGroup;

        public TagGroup getTagGroup() {
            return tagGroup;
        }

        public void setTagGroup(TagGroup tagGroup) {
            this.tagGroup = tagGroup;
        }
    }

    /**
     * Inner nested object for deep nesting tests.
     */
    static class InnerTagGroup {
        private String name;

        @JsonIgnore
        private Map<String, String> nameI18n;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getNameI18n() {
            return nameI18n;
        }

        public void setNameI18n(Map<String, String> nameI18n) {
            this.nameI18n = nameI18n;
        }
    }

    /**
     * Outer container for deep nesting tests.
     */
    static class OuterContainer {
        private InnerTagGroup inner;

        public InnerTagGroup getInner() {
            return inner;
        }

        public void setInner(InnerTagGroup inner) {
            this.inner = inner;
        }
    }

    /**
     * DTO with deeply nested source path for deep nesting tests.
     */
    static class DeepNestedDto {
        @I18nTrans(source = "container.inner.nameI18n", target = "container.inner.name")
        private OuterContainer container;

        public OuterContainer getContainer() {
            return container;
        }

        public void setContainer(OuterContainer container) {
            this.container = container;
        }
    }

    /**
     * Nested object with @JsonIncludeProperties to test selective field inclusion.
     * nameI18n is NOT included in the properties, but should still be accessible via reflection.
     * description is also NOT included to verify @JsonIncludeProperties works correctly.
     */
    @JsonIncludeProperties({"id", "code", "name"})
    static class TagGroupWithIncludeProps {
        private Long id;
        private String code;
        private String name;
        private String description;  // NOT in @JsonIncludeProperties - should NOT appear in JSON
        private Map<String, String> nameI18n;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, String> getNameI18n() {
            return nameI18n;
        }

        public void setNameI18n(Map<String, String> nameI18n) {
            this.nameI18n = nameI18n;
        }
    }

    /**
     * DTO with nested object using @JsonIncludeProperties on class level.
     */
    static class NestedWithIncludePropsDto {
        @I18nTrans(source = "tagGroup.nameI18n", target = "tagGroup.name")
        private TagGroupWithIncludeProps tagGroup;

        public TagGroupWithIncludeProps getTagGroup() {
            return tagGroup;
        }

        public void setTagGroup(TagGroupWithIncludeProps tagGroup) {
            this.tagGroup = tagGroup;
        }
    }

    /**
     * TagGroup without class-level @JsonIncludeProperties for field-level annotation test.
     */
    static class TagGroupNoClassAnnotation {
        private Long id;
        private String code;
        private String name;
        private String description;  // Should be filtered by field-level @JsonIncludeProperties
        private Map<String, String> nameI18n;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, String> getNameI18n() {
            return nameI18n;
        }

        public void setNameI18n(Map<String, String> nameI18n) {
            this.nameI18n = nameI18n;
        }
    }

    /**
     * DTO with field-level @JsonIncludeProperties (the real-world use case).
     */
    static class NestedWithFieldLevelIncludePropsDto {
        @JsonIncludeProperties({"id", "code", "name"})  // Field-level annotation
        @I18nTrans(source = "tagGroup.nameI18n", target = "tagGroup.name")
        private TagGroupNoClassAnnotation tagGroup;

        public TagGroupNoClassAnnotation getTagGroup() {
            return tagGroup;
        }

        public void setTagGroup(TagGroupNoClassAnnotation tagGroup) {
            this.tagGroup = tagGroup;
        }
    }

    /**
     * Nested object with multiple i18n fields for repeatable annotation tests.
     */
    static class ProductTranslations {
        private String name;
        @JsonIgnore
        private Map<String, String> nameI18n;

        private String description;
        @JsonIgnore
        private Map<String, String> descriptionI18n;

        private String category;
        @JsonIgnore
        private Map<String, String> categoryI18n;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getNameI18n() {
            return nameI18n;
        }

        public void setNameI18n(Map<String, String> nameI18n) {
            this.nameI18n = nameI18n;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, String> getDescriptionI18n() {
            return descriptionI18n;
        }

        public void setDescriptionI18n(Map<String, String> descriptionI18n) {
            this.descriptionI18n = descriptionI18n;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Map<String, String> getCategoryI18n() {
            return categoryI18n;
        }

        public void setCategoryI18n(Map<String, String> categoryI18n) {
            this.categoryI18n = categoryI18n;
        }
    }

    /**
     * DTO using repeatable @I18nTrans annotations to translate multiple nested fields.
     */
    static class ProductWithRepeatableDto {
        @I18nTrans(source = "translations.nameI18n", target = "translations.name")
        @I18nTrans(source = "translations.descriptionI18n", target = "translations.description")
        @I18nTrans(source = "translations.categoryI18n", target = "translations.category")
        private ProductTranslations translations;

        public ProductTranslations getTranslations() {
            return translations;
        }

        public void setTranslations(ProductTranslations translations) {
            this.translations = translations;
        }
    }
}