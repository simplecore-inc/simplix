package dev.simplecore.simplix.excel.template;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExcelTemplateManager")
class ExcelTemplateManagerTest {

    @AfterEach
    void tearDown() {
        ExcelTemplateManager.clearCache();
    }

    @Nested
    @DisplayName("templateExists")
    class TemplateExistsTests {

        @Test
        @DisplayName("should return false for null path")
        void shouldReturnFalseForNull() {
            assertThat(ExcelTemplateManager.templateExists(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for nonexistent template")
        void shouldReturnFalseForNonexistent() {
            assertThat(ExcelTemplateManager.templateExists("nonexistent/template.xlsx")).isFalse();
        }
    }

    @Nested
    @DisplayName("generateDefaultTemplate")
    class GenerateDefaultTemplateTests {

        @Test
        @DisplayName("should generate template at specified path")
        void shouldGenerateAtPath() throws IOException {
            Path tempDir = Files.createTempDirectory("template-test");
            Path outputPath = tempDir.resolve("test-template.xlsx");

            ExcelTemplateManager.generateDefaultTemplate(outputPath.toString());

            assertThat(Files.exists(outputPath)).isTrue();
            assertThat(Files.size(outputPath)).isGreaterThan(0);

            // Cleanup
            Files.deleteIfExists(outputPath);
            Files.deleteIfExists(tempDir);
        }

        @Test
        @DisplayName("should create parent directories if needed")
        void shouldCreateParentDirectories() throws IOException {
            Path tempDir = Files.createTempDirectory("template-test");
            Path nestedPath = tempDir.resolve("nested/dir/template.xlsx");

            ExcelTemplateManager.generateDefaultTemplate(nestedPath.toString());

            assertThat(Files.exists(nestedPath)).isTrue();

            // Cleanup
            Files.deleteIfExists(nestedPath);
            Files.deleteIfExists(nestedPath.getParent());
            Files.deleteIfExists(nestedPath.getParent().getParent());
            Files.deleteIfExists(tempDir);
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTests {

        @Test
        @DisplayName("should not throw on clearing empty cache")
        void shouldNotThrowOnEmptyCache() {
            ExcelTemplateManager.clearCache();
            // Should complete without error
        }
    }

    @Nested
    @DisplayName("removeFromCache")
    class RemoveFromCacheTests {

        @Test
        @DisplayName("should not throw when removing nonexistent template")
        void shouldNotThrowForNonexistent() {
            ExcelTemplateManager.removeFromCache("nonexistent.xlsx");
            // Should complete without error
        }
    }

    @Nested
    @DisplayName("getTemplateStream")
    class GetTemplateStreamTests {

        @Test
        @DisplayName("should return stream for generated template")
        void shouldReturnStream() throws IOException {
            Path tempDir = Files.createTempDirectory("template-test");
            Path outputPath = tempDir.resolve("stream-test.xlsx");
            ExcelTemplateManager.generateDefaultTemplate(outputPath.toString());

            var stream = ExcelTemplateManager.getTemplateStream(outputPath.toString());
            assertThat(stream).isNotNull();
            assertThat(stream.available()).isGreaterThan(0);
            stream.close();

            // Cleanup
            Files.deleteIfExists(outputPath);
            Files.deleteIfExists(tempDir);
        }
    }
}
