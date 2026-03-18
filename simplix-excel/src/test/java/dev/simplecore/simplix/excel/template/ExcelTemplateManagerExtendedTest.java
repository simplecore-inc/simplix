package dev.simplecore.simplix.excel.template;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExcelTemplateManager - Extended Coverage")
class ExcelTemplateManagerExtendedTest {

    @AfterEach
    void tearDown() {
        ExcelTemplateManager.clearCache();
    }

    @Nested
    @DisplayName("getTemplate")
    class GetTemplateTests {

        @Test
        @DisplayName("should generate default template when null path is provided")
        void shouldGenerateForNullPath() throws IOException {
            Resource resource = ExcelTemplateManager.getTemplate(null);
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
        }

        @Test
        @DisplayName("should generate template when path does not exist")
        void shouldGenerateWhenNotFound() throws IOException {
            Resource resource = ExcelTemplateManager.getTemplate("nonexistent/template.xlsx");
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
        }

        @Test
        @DisplayName("should return existing file system template")
        void shouldReturnExistingFileSystemTemplate() throws IOException {
            // First generate a template
            Path tempDir = Files.createTempDirectory("template-mgr-test");
            Path templatePath = tempDir.resolve("test-template.xlsx");
            ExcelTemplateManager.generateDefaultTemplate(templatePath.toString());

            // Now get it via getTemplate
            Resource resource = ExcelTemplateManager.getTemplate(templatePath.toString());
            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();

            Files.deleteIfExists(templatePath);
            Files.deleteIfExists(tempDir);
        }
    }

    @Nested
    @DisplayName("loadTemplateData")
    class LoadTemplateDataTests {

        @Test
        @DisplayName("should load and cache template data")
        void shouldLoadAndCacheData() throws IOException {
            Path tempDir = Files.createTempDirectory("template-data-test");
            Path templatePath = tempDir.resolve("cached-template.xlsx");
            ExcelTemplateManager.generateDefaultTemplate(templatePath.toString());

            byte[] data1 = ExcelTemplateManager.loadTemplateData(templatePath.toString());
            assertThat(data1).isNotNull();
            assertThat(data1.length).isGreaterThan(0);

            // Second call should return cached data
            byte[] data2 = ExcelTemplateManager.loadTemplateData(templatePath.toString());
            assertThat(data2).isSameAs(data1);

            Files.deleteIfExists(templatePath);
            Files.deleteIfExists(tempDir);
        }
    }

    @Nested
    @DisplayName("getTemplateStream")
    class GetTemplateStreamTests {

        @Test
        @DisplayName("should return input stream for valid template")
        void shouldReturnInputStream() throws IOException {
            Path tempDir = Files.createTempDirectory("template-stream-test");
            Path templatePath = tempDir.resolve("stream-template.xlsx");
            ExcelTemplateManager.generateDefaultTemplate(templatePath.toString());

            InputStream stream = ExcelTemplateManager.getTemplateStream(templatePath.toString());
            assertThat(stream).isNotNull();
            assertThat(stream.available()).isGreaterThan(0);
            stream.close();

            Files.deleteIfExists(templatePath);
            Files.deleteIfExists(tempDir);
        }
    }

    @Nested
    @DisplayName("templateExists")
    class TemplateExistsTests {

        @Test
        @DisplayName("should return true for existing file system template")
        void shouldReturnTrueForExistingFile() throws IOException {
            Path tempDir = Files.createTempDirectory("template-exists-test");
            Path templatePath = tempDir.resolve("exists-template.xlsx");
            ExcelTemplateManager.generateDefaultTemplate(templatePath.toString());

            assertThat(ExcelTemplateManager.templateExists(templatePath.toString())).isTrue();

            Files.deleteIfExists(templatePath);
            Files.deleteIfExists(tempDir);
        }
    }

    @Nested
    @DisplayName("removeFromCache")
    class RemoveFromCacheTests {

        @Test
        @DisplayName("should remove cached template")
        void shouldRemoveCached() throws IOException {
            Path tempDir = Files.createTempDirectory("template-remove-test");
            Path templatePath = tempDir.resolve("remove-template.xlsx");
            ExcelTemplateManager.generateDefaultTemplate(templatePath.toString());

            // Load to cache
            ExcelTemplateManager.loadTemplateData(templatePath.toString());

            // Remove from cache
            ExcelTemplateManager.removeFromCache(templatePath.toString());

            // Should still load (regenerates/reloads from file)
            byte[] data = ExcelTemplateManager.loadTemplateData(templatePath.toString());
            assertThat(data).isNotNull();

            Files.deleteIfExists(templatePath);
            Files.deleteIfExists(tempDir);
        }
    }
}
