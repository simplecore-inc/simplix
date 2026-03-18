package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.simplecore.simplix.core.config.SimpliXI18nConfigHolder;
import dev.simplecore.simplix.core.jackson.annotation.I18nTrans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXI18nTransSerializer - Extended Coverage")
class SimpliXI18nTransSerializerExtendedTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // DTO for simple mode testing
    static class SimpleDto {
        private Map<String, String> nameI18n;

        @I18nTrans(source = "nameI18n")
        @JsonSerialize(using = SimpliXI18nTransSerializer.class)
        private String name;

        public Map<String, String> getNameI18n() { return nameI18n; }
        public void setNameI18n(Map<String, String> nameI18n) { this.nameI18n = nameI18n; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // DTO for nested mode testing
    static class ParentDto {
        private Map<String, String> titleI18n;

        @I18nTrans(source = "titleI18n", target = "nested.title")
        @JsonSerialize(using = SimpliXI18nTransSerializer.class)
        private NestedDto nested;

        public Map<String, String> getTitleI18n() { return titleI18n; }
        public void setTitleI18n(Map<String, String> titleI18n) { this.titleI18n = titleI18n; }
        public NestedDto getNested() { return nested; }
        public void setNested(NestedDto nested) { this.nested = nested; }
    }

    static class NestedDto {
        private String title;

        public NestedDto() {}
        public NestedDto(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Nested
    @DisplayName("Simple mode - translation from i18n map")
    class SimpleMode {

        @Test
        @DisplayName("should translate using exact locale match")
        void shouldTranslateExactLocale() throws Exception {
            LocaleContextHolder.setLocale(Locale.KOREAN);

            SimpleDto dto = new SimpleDto();
            dto.setName("Default Name");
            dto.setNameI18n(Map.of("ko", "Korean Name", "en", "English Name"));

            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("Korean Name");

            LocaleContextHolder.resetLocaleContext();
        }

        @Test
        @DisplayName("should translate using language code fallback")
        void shouldTranslateLanguageFallback() throws Exception {
            LocaleContextHolder.setLocale(Locale.forLanguageTag("ko-KR"));

            SimpleDto dto = new SimpleDto();
            dto.setName("Default");
            dto.setNameI18n(Map.of("ko", "Korean"));

            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("Korean");

            LocaleContextHolder.resetLocaleContext();
        }

        @Test
        @DisplayName("should return original value when i18n map is empty")
        void shouldReturnOriginalWhenEmpty() throws Exception {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            SimpleDto dto = new SimpleDto();
            dto.setName("Original Name");
            dto.setNameI18n(Map.of());

            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("Original Name");

            LocaleContextHolder.resetLocaleContext();
        }

        @Test
        @DisplayName("should return original value when i18n map is null")
        void shouldReturnOriginalWhenNull() throws Exception {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            SimpleDto dto = new SimpleDto();
            dto.setName("Original");
            dto.setNameI18n(null);

            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("Original");

            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Nested
    @DisplayName("Nested mode - translation with target object")
    class NestedMode {

        @Test
        @DisplayName("should translate nested object field")
        void shouldTranslateNestedField() throws Exception {
            LocaleContextHolder.setLocale(Locale.KOREAN);

            ParentDto dto = new ParentDto();
            dto.setTitleI18n(Map.of("ko", "Korean Title", "en", "English Title"));
            dto.setNested(new NestedDto("Default Title"));

            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("Korean Title");

            LocaleContextHolder.resetLocaleContext();
        }

        @Test
        @DisplayName("should serialize null nested object")
        void shouldSerializeNullNested() throws Exception {
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            ParentDto dto = new ParentDto();
            dto.setTitleI18n(Map.of("en", "Title"));
            dto.setNested(null);

            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("null");

            LocaleContextHolder.resetLocaleContext();
        }

        @Test
        @DisplayName("should restore original value after serialization")
        void shouldRestoreOriginalValue() throws Exception {
            LocaleContextHolder.setLocale(Locale.KOREAN);

            NestedDto nested = new NestedDto("Original");
            ParentDto dto = new ParentDto();
            dto.setTitleI18n(Map.of("ko", "Korean"));
            dto.setNested(nested);

            mapper.writeValueAsString(dto);

            // Original value should be restored
            assertThat(nested.getTitle()).isEqualTo("Original");

            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Nested
    @DisplayName("extractTranslation fallback chain")
    class ExtractTranslation {

        @Test
        @DisplayName("should use annotation default locale as fallback")
        void shouldUseAnnotationDefault() throws Exception {
            LocaleContextHolder.setLocale(Locale.FRENCH);

            SimpleDto dto = new SimpleDto();
            dto.setName("Default");
            dto.setNameI18n(Map.of("en", "English Only"));

            String json = mapper.writeValueAsString(dto);
            // Falls through to "any available translation"
            assertThat(json).contains("English Only");

            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Nested
    @DisplayName("Default constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should create serializer with default constructor")
        void shouldCreateWithDefault() {
            SimpliXI18nTransSerializer serializer = new SimpliXI18nTransSerializer();
            assertThat(serializer).isNotNull();
        }
    }
}
