package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXTreeRepositoryAutoConfiguration - tree repository auto-configuration")
class SimpliXTreeRepositoryAutoConfigurationTest {

    @Test
    @DisplayName("Should create TreeRepositoryProperties bean with defaults")
    void createTreeRepositoryProperties() {
        SimpliXTreeRepositoryAutoConfiguration config = new SimpliXTreeRepositoryAutoConfiguration();

        SimpliXTreeRepositoryAutoConfiguration.TreeRepositoryProperties props =
                config.treeRepositoryProperties();

        assertThat(props).isNotNull();
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("TreeRepositoryProperties should allow toggling enabled flag")
    void toggleEnabled() {
        SimpliXTreeRepositoryAutoConfiguration.TreeRepositoryProperties props =
                new SimpliXTreeRepositoryAutoConfiguration.TreeRepositoryProperties();

        props.setEnabled(false);

        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("EnableSimplixTreeRepositories annotation should be present")
    void enableAnnotationExists() {
        assertThat(SimpliXTreeRepositoryAutoConfiguration.EnableSimplixTreeRepositories.class)
                .isAnnotation();
    }
}
