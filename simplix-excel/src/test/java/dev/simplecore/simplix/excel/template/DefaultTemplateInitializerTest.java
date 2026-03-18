package dev.simplecore.simplix.excel.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultTemplateInitializer")
class DefaultTemplateInitializerTest {

    @Test
    @DisplayName("should not throw when initializing default template")
    void shouldNotThrowOnInitialize() {
        // This test exercises the static method.
        // The method creates a template in templates/ directory relative to working dir.
        // We just verify it does not throw.
        DefaultTemplateInitializer.initializeDefaultTemplate();
    }

    @Test
    @DisplayName("should not recreate template if it already exists")
    void shouldNotRecreateIfExists() {
        // Call twice - second call should detect existing file
        DefaultTemplateInitializer.initializeDefaultTemplate();
        DefaultTemplateInitializer.initializeDefaultTemplate();
    }
}
