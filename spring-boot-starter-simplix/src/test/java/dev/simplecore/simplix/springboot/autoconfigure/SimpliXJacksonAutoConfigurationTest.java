package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXJacksonAutoConfiguration - ObjectMapper auto-configuration")
class SimpliXJacksonAutoConfigurationTest {

    @Mock
    private Environment environment;

    private SimpliXJacksonAutoConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SimpliXJacksonAutoConfiguration(environment);
    }

    @Nested
    @DisplayName("objectMapper")
    class ObjectMapperBean {

        @Test
        @DisplayName("Should create ObjectMapper with NON_NULL inclusion")
        void nonNullInclusion() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion())
                    .isEqualTo(JsonInclude.Include.NON_NULL);
        }

        @Test
        @DisplayName("Should disable WRITE_DATES_AS_TIMESTAMPS")
        void disableDatesAsTimestamps() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig()
                    .isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
        }

        @Test
        @DisplayName("Should enable ADJUST_DATES_TO_CONTEXT_TIME_ZONE")
        void enableAdjustDates() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getDeserializationConfig()
                    .isEnabled(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)).isTrue();
        }

        @Test
        @DisplayName("Should enable INDENT_OUTPUT")
        void enableIndentOutput() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig()
                    .isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
        }

        @Test
        @DisplayName("Should disable FAIL_ON_EMPTY_BEANS")
        void disableFailOnEmptyBeans() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig()
                    .isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isFalse();
        }

        @Test
        @DisplayName("Should enable ACCEPT_SINGLE_VALUE_AS_ARRAY")
        void enableAcceptSingleValueAsArray() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getDeserializationConfig()
                    .isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)).isTrue();
        }

        @Test
        @DisplayName("Should disable FAIL_ON_UNKNOWN_PROPERTIES")
        void disableFailOnUnknownProperties() {
            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getDeserializationConfig()
                    .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        }

        @Test
        @DisplayName("Should use spring.jackson.time-zone when configured")
        void useSpringTimezone() {
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn("Asia/Seoul");

            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig().getTimeZone().getID())
                    .isEqualTo("Asia/Seoul");
        }

        @Test
        @DisplayName("Should fall back to simplix.date-time.default-timezone")
        void fallbackToSimplixTimezone() {
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn(null);
            when(environment.getProperty("simplix.date-time.default-timezone")).thenReturn("Europe/London");

            ObjectMapper mapper = config.objectMapper();

            assertThat(mapper.getSerializationConfig().getTimeZone().getID())
                    .isEqualTo("Europe/London");
        }
    }

    @Nested
    @DisplayName("mappingJackson2HttpMessageConverter")
    class MessageConverter {

        @Test
        @DisplayName("Should create converter with pretty print enabled")
        void prettyPrint() {
            ObjectMapper mapper = config.objectMapper();

            MappingJackson2HttpMessageConverter converter =
                    config.mappingJackson2HttpMessageConverter(mapper);

            assertThat(converter).isNotNull();
            assertThat(converter.getObjectMapper()).isSameAs(mapper);
        }
    }
}
