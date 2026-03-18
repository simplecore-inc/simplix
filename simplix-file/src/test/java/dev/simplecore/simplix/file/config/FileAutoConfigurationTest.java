package dev.simplecore.simplix.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileAutoConfiguration")
class FileAutoConfigurationTest {

    @Test
    @DisplayName("Should be annotated with @AutoConfiguration")
    void shouldBeAnnotatedWithAutoConfiguration() {
        assertThat(FileAutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class)).isTrue();
    }

    @Test
    @DisplayName("Should be conditional on simplix.file.enabled property")
    void shouldBeConditionalOnSimplixFileEnabledProperty() {
        ConditionalOnProperty annotation =
            FileAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("simplix.file");
        assertThat(annotation.name()).containsExactly("enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
        assertThat(annotation.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("Should enable configuration properties for all property classes")
    void shouldEnableConfigurationPropertiesForAllPropertyClasses() {
        EnableConfigurationProperties annotation =
            FileAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactlyInAnyOrder(
            StorageProperties.class,
            FileProperties.class,
            ImageProperties.class
        );
    }

    @Test
    @DisplayName("Should scan correct base packages")
    void shouldScanCorrectBasePackages() {
        ComponentScan annotation =
            FileAutoConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages()).containsExactlyInAnyOrder(
            "dev.simplecore.simplix.file.config",
            "dev.simplecore.simplix.file.infrastructure"
        );
    }

    @Test
    @DisplayName("Should be instantiable")
    void shouldBeInstantiable() {
        FileAutoConfiguration config = new FileAutoConfiguration();
        assertThat(config).isNotNull();
    }
}
