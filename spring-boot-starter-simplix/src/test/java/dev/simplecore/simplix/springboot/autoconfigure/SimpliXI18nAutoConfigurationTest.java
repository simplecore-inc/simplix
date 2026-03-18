package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.core.config.SimpliXI18nConfigHolder;
import dev.simplecore.simplix.core.config.SimpliXI18nProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXI18nAutoConfiguration - I18n translation auto-configuration")
class SimpliXI18nAutoConfigurationTest {

    @Mock
    private SimpliXI18nProperties properties;

    @Test
    @DisplayName("Should be annotated with @AutoConfiguration")
    void hasAutoConfigurationAnnotation() {
        AutoConfiguration annotation =
                SimpliXI18nAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("Should create configuration with properties")
    void createConfiguration() {
        SimpliXI18nAutoConfiguration config = new SimpliXI18nAutoConfiguration(properties);

        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("Should initialize I18n config holder on PostConstruct")
    void initializeConfigHolder() {
        when(properties.getDefaultLocale()).thenReturn("en");
        when(properties.getSupportedLocales()).thenReturn(List.of("en", "ko"));

        SimpliXI18nAutoConfiguration config = new SimpliXI18nAutoConfiguration(properties);
        config.initialize();

        // Verify it initialized without error and the holder has the properties
        assertThat(SimpliXI18nConfigHolder.getDefaultLocale()).isEqualTo("en");
    }
}
