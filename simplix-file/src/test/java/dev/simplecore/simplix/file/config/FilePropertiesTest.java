package dev.simplecore.simplix.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileProperties")
class FilePropertiesTest {

    @Test
    @DisplayName("Should have correct default values")
    void shouldHaveCorrectDefaultValues() {
        FileProperties properties = new FileProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultMaxSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(properties.getMaxFilesPerRequest()).isEqualTo(10);
        assertThat(properties.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(properties.isVirusScanEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should have default allowed MIME types")
    void shouldHaveDefaultAllowedMimeTypes() {
        FileProperties properties = new FileProperties();

        Set<String> allowedTypes = properties.getAllowedMimeTypes();
        assertThat(allowedTypes).contains(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml",
            "application/pdf", "application/msword",
            "application/zip", "text/plain", "text/csv"
        );
    }

    @Test
    @DisplayName("Should return true for allowed MIME type")
    void shouldReturnTrueForAllowedMimeType() {
        FileProperties properties = new FileProperties();

        assertThat(properties.isAllowedMimeType("image/jpeg")).isTrue();
        assertThat(properties.isAllowedMimeType("application/pdf")).isTrue();
    }

    @Test
    @DisplayName("Should return false for disallowed MIME type")
    void shouldReturnFalseForDisallowedMimeType() {
        FileProperties properties = new FileProperties();

        assertThat(properties.isAllowedMimeType("application/octet-stream")).isFalse();
    }

    @Test
    @DisplayName("Should return false for null MIME type")
    void shouldReturnFalseForNullMimeType() {
        FileProperties properties = new FileProperties();

        assertThat(properties.isAllowedMimeType(null)).isFalse();
    }

    @Test
    @DisplayName("Should be case-insensitive for MIME type check")
    void shouldBeCaseInsensitiveForMimeTypeCheck() {
        FileProperties properties = new FileProperties();

        assertThat(properties.isAllowedMimeType("IMAGE/JPEG")).isTrue();
        assertThat(properties.isAllowedMimeType("Image/Jpeg")).isTrue();
    }

    @Test
    @DisplayName("Should have default thumbnail config")
    void shouldHaveDefaultThumbnailConfig() {
        FileProperties properties = new FileProperties();
        FileProperties.ThumbnailConfig thumbnail = properties.getThumbnail();

        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.getCachePath()).isEqualTo("./uploads/.thumbnails");
        assertThat(thumbnail.isCacheEnabled()).isTrue();
        assertThat(thumbnail.getS3Prefix()).isEqualTo("thumbnails");
    }

    @Test
    @DisplayName("Should allow setting custom values")
    void shouldAllowSettingCustomValues() {
        FileProperties properties = new FileProperties();
        properties.setEnabled(false);
        properties.setDefaultMaxSize(DataSize.ofMegabytes(50));
        properties.setMaxFilesPerRequest(20);
        properties.setChecksumAlgorithm("MD5");

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDefaultMaxSize()).isEqualTo(DataSize.ofMegabytes(50));
        assertThat(properties.getMaxFilesPerRequest()).isEqualTo(20);
        assertThat(properties.getChecksumAlgorithm()).isEqualTo("MD5");
    }
}
