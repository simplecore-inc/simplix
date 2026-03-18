package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXModelMapperAutoConfiguration - timezone converter execution tests")
class SimpliXModelMapperAutoConfigurationAdditionalTest {

    @Nested
    @DisplayName("Timezone converters registered")
    class TimezoneConverters {

        @Test
        @DisplayName("Should register converters when timezone service is available with normalize enabled")
        void convertersRegisteredWithNormalize() {
            SimpliXModelMapperAutoConfiguration config = new SimpliXModelMapperAutoConfiguration();
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, true);
            config.setTimezoneService(timezoneService);
            ModelMapper mapper = config.modelMapper();

            assertThat(mapper).isNotNull();
            assertThat(mapper.getConfiguration().getConverters()).isNotEmpty();
        }

        @Test
        @DisplayName("Should register converters when timezone service is available with normalize disabled")
        void convertersRegisteredWithoutNormalize() {
            SimpliXModelMapperAutoConfiguration config = new SimpliXModelMapperAutoConfiguration();
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, false);
            config.setTimezoneService(timezoneService);
            ModelMapper mapper = config.modelMapper();

            assertThat(mapper).isNotNull();
            assertThat(mapper.getConfiguration().getConverters()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Map converters")
    class MapConverters {

        @Test
        @DisplayName("Should convert empty Map to empty LinkedHashMap")
        @SuppressWarnings("unchecked")
        void emptyMapToLinkedHashMap() {
            SimpliXModelMapperAutoConfiguration config = new SimpliXModelMapperAutoConfiguration();
            ModelMapper mapper = config.modelMapper();

            java.util.Map<String, String> source = new java.util.HashMap<>();
            java.util.LinkedHashMap<String, String> result = mapper.map(source, java.util.LinkedHashMap.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should convert empty LinkedHashMap to empty Map")
        @SuppressWarnings("unchecked")
        void emptyLinkedHashMapToMap() {
            SimpliXModelMapperAutoConfiguration config = new SimpliXModelMapperAutoConfiguration();
            ModelMapper mapper = config.modelMapper();

            java.util.LinkedHashMap<String, String> source = new java.util.LinkedHashMap<>();
            java.util.Map<String, String> result = mapper.map(source, java.util.Map.class);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Without timezone service")
    class WithoutTimezoneService {

        @Test
        @DisplayName("Should skip timezone converters and still create functional mapper")
        void noTimezoneService() {
            SimpliXModelMapperAutoConfiguration config = new SimpliXModelMapperAutoConfiguration();
            ModelMapper mapper = config.modelMapper();
            assertThat(mapper).isNotNull();
        }
    }
}
