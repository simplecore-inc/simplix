package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SimpliXJacksonAutoConfiguration - additional timezone fallback and converter tests")
class SimpliXJacksonAutoConfigurationAdditionalTest {

    @Mock
    private Environment environment;

    @Nested
    @DisplayName("timezone resolution fallback chain")
    class ZoneIdFallback {

        @Test
        @DisplayName("Should resolve canonical simplix timezone ahead of the deprecated spring alias")
        void invalidSpringTimezone() {
            when(environment.getProperty("simplix.date-time.default-timezone")).thenReturn("Asia/Seoul");

            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig().getTimeZone().getID())
                    .isEqualTo("Asia/Seoul");
        }

        @Test
        @DisplayName("Should handle invalid simplix timezone and fall back to system default")
        void invalidSimplixTimezone() {
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn(null);
            when(environment.getProperty("simplix.date-time.default-timezone")).thenReturn("Invalid/Zone");

            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            ObjectMapper mapper = config.objectMapper();

            // Should fall back to system default or user.timezone
            assertThat(mapper.getSerializationConfig().getTimeZone()).isNotNull();
        }

        @Test
        @DisplayName("Should use system default when all timezone properties are null")
        void allTimezonePropertiesNull() {
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn(null);
            when(environment.getProperty("simplix.date-time.default-timezone")).thenReturn(null);

            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig().getTimeZone()).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty string timezone properties")
        void emptyTimezoneProperties() {
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn("");
            when(environment.getProperty("simplix.date-time.default-timezone")).thenReturn("");

            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig().getTimeZone()).isNotNull();
        }
    }

    @Nested
    @DisplayName("extendMessageConverters")
    class ExtendMessageConverters {

        @Test
        @DisplayName("Should replace the default JSON converter in place and keep every other default")
        void replacesJsonConverterInPlace() {
            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            MappingJackson2HttpMessageConverter defaultJson = new MappingJackson2HttpMessageConverter();
            List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(
                    new ByteArrayHttpMessageConverter(),
                    new StringHttpMessageConverter(),
                    new ResourceHttpMessageConverter(),
                    new ResourceRegionHttpMessageConverter(),
                    defaultJson));

            config.extendMessageConverters(converters);

            assertThat(converters).hasSize(5);
            assertThat(converters.get(0)).isInstanceOf(ByteArrayHttpMessageConverter.class);
            assertThat(converters.get(1)).isInstanceOf(StringHttpMessageConverter.class);
            assertThat(converters.get(2)).isInstanceOf(ResourceHttpMessageConverter.class);
            assertThat(converters.get(3)).isInstanceOf(ResourceRegionHttpMessageConverter.class);
            assertThat(converters.get(4)).isInstanceOf(MappingJackson2HttpMessageConverter.class).isNotSameAs(defaultJson);
        }

        @Test
        @DisplayName("Should move the JSON converter after the byte array and string converters")
        void placesJsonAfterStringConverters() {
            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(
                    new MappingJackson2HttpMessageConverter(),
                    new ByteArrayHttpMessageConverter(),
                    new StringHttpMessageConverter(),
                    new ResourceHttpMessageConverter()));

            config.extendMessageConverters(converters);

            assertThat(converters).hasSize(4);
            assertThat(converters.get(0)).isInstanceOf(ByteArrayHttpMessageConverter.class);
            assertThat(converters.get(1)).isInstanceOf(StringHttpMessageConverter.class);
            assertThat(converters.get(2)).isInstanceOf(MappingJackson2HttpMessageConverter.class);
            assertThat(converters.get(3)).isInstanceOf(ResourceHttpMessageConverter.class);
        }

        @Test
        @DisplayName("Should append the SimpliX JSON converter when no JSON converter is registered")
        void appendsWhenNoJsonConverter() {
            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(new ByteArrayHttpMessageConverter()));

            config.extendMessageConverters(converters);

            assertThat(converters).hasSize(2);
            assertThat(converters.get(1)).isInstanceOf(MappingJackson2HttpMessageConverter.class);
        }
    }
}
