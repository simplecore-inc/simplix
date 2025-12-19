package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.simplecore.simplix.core.jackson.annotation.I18nTrans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to reproduce the nested mode translation issue.
 */
@DisplayName("Debug: Nested Mode Translation Issue")
class SimpliXI18nTransSerializerDebugTest {

    private ObjectMapper objectMapper;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        // Setup ObjectMapper like SimpliXJacksonAutoConfiguration
        objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Note: Hibernate6Module is registered in production via SimpliXJacksonAutoConfiguration
        // For this test, we test without it to verify the serializer works independently

        originalLocale = LocaleContextHolder.getLocale();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.setLocale(originalLocale);
    }

    @Test
    @DisplayName("Compare Simple mode vs Nested mode translation")
    void compareSimpleVsNestedMode() throws JsonProcessingException {
        System.out.println("\n=== Simple Mode Test ===");

        // Simple mode
        SimpleDto simpleDto = new SimpleDto();
        simpleDto.setName("Default Name");
        simpleDto.setNameI18n(createI18nMap("en", "English Name", "ko", "Korean Name"));

        LocaleContextHolder.setLocale(Locale.KOREAN);
        String simpleJsonKo = objectMapper.writeValueAsString(simpleDto);
        System.out.println("Simple mode (ko): " + simpleJsonKo);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String simpleJsonEn = objectMapper.writeValueAsString(simpleDto);
        System.out.println("Simple mode (en): " + simpleJsonEn);

        System.out.println("\n=== Nested Mode Test ===");

        // Nested mode
        TagGroup tagGroup = new TagGroup();
        tagGroup.setId(1L);
        tagGroup.setCode("TAG001");
        tagGroup.setName("Default Tag Group");
        tagGroup.setNameI18n(createI18nMap("en", "English Tag Group", "ko", "Korean Tag Group"));

        NestedDto nestedDto = new NestedDto();
        nestedDto.setTagGroup(tagGroup);

        LocaleContextHolder.setLocale(Locale.KOREAN);
        String nestedJsonKo = objectMapper.writeValueAsString(nestedDto);
        System.out.println("Nested mode (ko): " + nestedJsonKo);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String nestedJsonEn = objectMapper.writeValueAsString(nestedDto);
        System.out.println("Nested mode (en): " + nestedJsonEn);

        // Verify
        assertTrue(simpleJsonKo.contains("Korean Name"), "Simple mode should have Korean translation");
        assertTrue(simpleJsonEn.contains("English Name"), "Simple mode should have English translation");
        assertTrue(nestedJsonKo.contains("Korean Tag Group"), "Nested mode should have Korean translation");
        assertTrue(nestedJsonEn.contains("English Tag Group"), "Nested mode should have English translation");
    }

    @Test
    @DisplayName("Test with @JsonIncludeProperties on field level")
    void testWithJsonIncludePropertiesOnField() throws JsonProcessingException {
        System.out.println("\n=== Nested Mode with @JsonIncludeProperties Test ===");

        TagGroupPlain tagGroup = new TagGroupPlain();
        tagGroup.setId(1L);
        tagGroup.setCode("TAG001");
        tagGroup.setName("Default Tag Group");
        tagGroup.setDescription("Some description");
        tagGroup.setNameI18n(createI18nMap("en", "English Tag Group", "ko", "Korean Tag Group"));

        NestedWithFieldLevelAnnotation dto = new NestedWithFieldLevelAnnotation();
        dto.setTagGroup(tagGroup);

        LocaleContextHolder.setLocale(Locale.KOREAN);
        String jsonKo = objectMapper.writeValueAsString(dto);
        System.out.println("Field-level @JsonIncludeProperties (ko): " + jsonKo);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String jsonEn = objectMapper.writeValueAsString(dto);
        System.out.println("Field-level @JsonIncludeProperties (en): " + jsonEn);

        // Verify translation works
        assertTrue(jsonKo.contains("Korean Tag Group"), "Should have Korean translation");
        assertTrue(jsonEn.contains("English Tag Group"), "Should have English translation");

        // Verify @JsonIncludeProperties works
        assertFalse(jsonKo.contains("description"), "description should be excluded");
        assertFalse(jsonEn.contains("description"), "description should be excluded");
    }

    @Test
    @DisplayName("Test original object is not modified after serialization")
    void testOriginalObjectNotModified() throws JsonProcessingException {
        System.out.println("\n=== Original Object Modification Test ===");

        TagGroup tagGroup = new TagGroup();
        tagGroup.setId(1L);
        tagGroup.setCode("TAG001");
        tagGroup.setName("Default Tag Group");
        tagGroup.setNameI18n(createI18nMap("en", "English Tag Group", "ko", "Korean Tag Group"));

        NestedDto dto = new NestedDto();
        dto.setTagGroup(tagGroup);

        String originalName = tagGroup.getName();
        System.out.println("Before serialization: name = " + originalName);

        LocaleContextHolder.setLocale(Locale.KOREAN);
        objectMapper.writeValueAsString(dto);

        String afterKo = tagGroup.getName();
        System.out.println("After Korean serialization: name = " + afterKo);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        objectMapper.writeValueAsString(dto);

        String afterEn = tagGroup.getName();
        System.out.println("After English serialization: name = " + afterEn);

        assertEquals(originalName, afterKo, "Object should not be modified after Korean serialization");
        assertEquals(originalName, afterEn, "Object should not be modified after English serialization");
    }

    private Map<String, String> createI18nMap(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // Test DTOs
    static class SimpleDto {
        @I18nTrans(source = "nameI18n")
        private String name;

        @JsonIgnore
        private Map<String, String> nameI18n;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, String> getNameI18n() { return nameI18n; }
        public void setNameI18n(Map<String, String> nameI18n) { this.nameI18n = nameI18n; }
    }

    static class TagGroup {
        private Long id;
        private String code;
        private String name;

        @JsonIgnore
        private Map<String, String> nameI18n;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, String> getNameI18n() { return nameI18n; }
        public void setNameI18n(Map<String, String> nameI18n) { this.nameI18n = nameI18n; }
    }

    static class NestedDto {
        @I18nTrans(source = "tagGroup.nameI18n", target = "tagGroup.name")
        private TagGroup tagGroup;

        public TagGroup getTagGroup() { return tagGroup; }
        public void setTagGroup(TagGroup tagGroup) { this.tagGroup = tagGroup; }
    }

    static class TagGroupPlain {
        private Long id;
        private String code;
        private String name;
        private String description;
        private Map<String, String> nameI18n;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, String> getNameI18n() { return nameI18n; }
        public void setNameI18n(Map<String, String> nameI18n) { this.nameI18n = nameI18n; }
    }

    static class NestedWithFieldLevelAnnotation {
        @JsonIncludeProperties({"id", "code", "name"})
        @I18nTrans(source = "tagGroup.nameI18n", target = "tagGroup.name")
        private TagGroupPlain tagGroup;

        public TagGroupPlain getTagGroup() { return tagGroup; }
        public void setTagGroup(TagGroupPlain tagGroup) { this.tagGroup = tagGroup; }
    }
}
