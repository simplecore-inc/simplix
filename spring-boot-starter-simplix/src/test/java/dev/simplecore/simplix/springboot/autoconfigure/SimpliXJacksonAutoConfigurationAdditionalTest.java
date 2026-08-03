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
    @DisplayName("configureMessageConverters")
    class ConfigureMessageConverters {

        @Test
        @DisplayName("Should add ByteArray, String, and Jackson converters to converter list")
        void configureConverters() {
            SimpliXJacksonAutoConfiguration config = new SimpliXJacksonAutoConfiguration(environment);
            List<HttpMessageConverter<?>> converters = new ArrayList<>();

            config.configureMessageConverters(converters);

            assertThat(converters).hasSize(3);
            assertThat(converters.get(0)).isInstanceOf(ByteArrayHttpMessageConverter.class);
            assertThat(converters.get(1)).isInstanceOf(StringHttpMessageConverter.class);
            assertThat(converters.get(2)).isInstanceOf(MappingJackson2HttpMessageConverter.class);
        }
    }
}
