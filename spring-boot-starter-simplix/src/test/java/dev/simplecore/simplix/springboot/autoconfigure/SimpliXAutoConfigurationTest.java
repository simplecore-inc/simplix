package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAutoConfiguration - root auto-configuration")
class SimpliXAutoConfigurationTest {

    @Test
    @DisplayName("Should be annotated with @AutoConfiguration")
    void hasAutoConfigurationAnnotation() {
        AutoConfiguration annotation = SimpliXAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("Should enable SimpliXProperties configuration")
    void enablesConfigurationProperties() {
        EnableConfigurationProperties annotation =
                SimpliXAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("Should scan expected base packages excluding autoconfigure package")
    void componentScanConfig() {
        ComponentScan annotation = SimpliXAutoConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages())
                .contains("dev.simplecore.simplix.web", "dev.simplecore.simplix.springboot");

        // Should have exclude filter for autoconfigure package
        assertThat(annotation.excludeFilters()).hasSize(1);
    }

    @Test
    @DisplayName("Should create instance without error (constructor logging)")
    void constructorWorks() {
        SimpliXAutoConfiguration config = new SimpliXAutoConfiguration();

        assertThat(config).isNotNull();
    }
}
