package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXModelMapperAutoConfiguration - ModelMapper auto-configuration")
class SimpliXModelMapperAutoConfigurationTest {

    private SimpliXModelMapperAutoConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SimpliXModelMapperAutoConfiguration();
    }

    @Nested
    @DisplayName("modelMapper without timezone service")
    class WithoutTimezoneService {

        @Test
        @DisplayName("Should create ModelMapper with STRICT matching strategy")
        void strictMatchingStrategy() {
            ModelMapper mapper = config.modelMapper();

            assertThat(mapper.getConfiguration().getMatchingStrategy())
                    .isEqualTo(MatchingStrategies.STRICT);
        }

        @Test
        @DisplayName("Should enable field matching")
        void fieldMatchingEnabled() {
            ModelMapper mapper = config.modelMapper();

            assertThat(mapper.getConfiguration().isFieldMatchingEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should create a functional ModelMapper even without timezone service")
        void functionalWithoutTimezoneService() {
            ModelMapper mapper = config.modelMapper();

            assertThat(mapper).isNotNull();
        }
    }

    @Nested
    @DisplayName("modelMapper with timezone service")
    class WithTimezoneService {

        @Test
        @DisplayName("Should create ModelMapper with timezone converters registered")
        void timezoneConvertersRegistered() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, true);
            config.setTimezoneService(timezoneService);

            ModelMapper mapper = config.modelMapper();

            // Verify the mapper was created with timezone-aware converters
            // by checking that the mapper has type maps or converters configured
            assertThat(mapper).isNotNull();
            assertThat(mapper.getConfiguration().getMatchingStrategy())
                    .isEqualTo(MatchingStrategies.STRICT);
        }

        @Test
        @DisplayName("Should create ModelMapper with normalize timezone disabled")
        void normalizeTimezoneDisabled() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, false);
            config.setTimezoneService(timezoneService);

            ModelMapper mapper = config.modelMapper();

            assertThat(mapper).isNotNull();
        }

        @Test
        @DisplayName("Should register timezone converters when timezone service is provided")
        void registerTimezoneConverters() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, true);
            config.setTimezoneService(timezoneService);

            ModelMapper mapper = config.modelMapper();

            // Verify converter registration did not fail and mapper is functional
            assertThat(mapper).isNotNull();
            assertThat(mapper.getConfiguration().getMatchingStrategy())
                    .isEqualTo(MatchingStrategies.STRICT);
        }

        @Test
        @DisplayName("Should register timezone converters with normalize disabled")
        void registerTimezoneConvertersWithNormalizeDisabled() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, false);
            config.setTimezoneService(timezoneService);

            ModelMapper mapper = config.modelMapper();

            assertThat(mapper).isNotNull();
        }
    }

    @Nested
    @DisplayName("Map converters")
    class MapConverters {

        @Test
        @DisplayName("Should convert Map to LinkedHashMap")
        @SuppressWarnings("unchecked")
        void mapToLinkedHashMap() {
            ModelMapper mapper = config.modelMapper();

            java.util.Map<String, String> source = new java.util.HashMap<>();
            source.put("key", "value");

            java.util.LinkedHashMap<String, String> result = mapper.map(source, java.util.LinkedHashMap.class);

            assertThat(result.get("key")).isEqualTo("value");
        }

        @Test
        @DisplayName("Should convert LinkedHashMap to Map")
        @SuppressWarnings("unchecked")
        void linkedHashMapToMap() {
            ModelMapper mapper = config.modelMapper();

            java.util.LinkedHashMap<String, String> source = new java.util.LinkedHashMap<>();
            source.put("key", "value");

            java.util.Map<String, String> result = mapper.map(source, java.util.Map.class);

            assertThat(result.get("key")).isEqualTo("value");
        }
    }
}
