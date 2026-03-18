package dev.simplecore.simplix.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageProperties")
class ImagePropertiesTest {

    @Test
    @DisplayName("Should have correct default values")
    void shouldHaveCorrectDefaultValues() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.getDefaultMaxWidth()).isEqualTo(2048);
        assertThat(properties.getDefaultMaxHeight()).isEqualTo(2048);
        assertThat(properties.getDefaultQuality()).isEqualTo(85);
        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
    }

    @Test
    @DisplayName("Should have default allowed formats")
    void shouldHaveDefaultAllowedFormats() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.getAllowedFormats()).containsExactlyInAnyOrder(
            "image/jpeg", "image/png", "image/gif", "image/webp"
        );
    }

    @Test
    @DisplayName("Should return true for allowed image format")
    void shouldReturnTrueForAllowedImageFormat() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.isAllowedFormat("image/jpeg")).isTrue();
        assertThat(properties.isAllowedFormat("image/png")).isTrue();
        assertThat(properties.isAllowedFormat("image/gif")).isTrue();
        assertThat(properties.isAllowedFormat("image/webp")).isTrue();
    }

    @Test
    @DisplayName("Should return false for disallowed image format")
    void shouldReturnFalseForDisallowedImageFormat() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.isAllowedFormat("image/tiff")).isFalse();
        assertThat(properties.isAllowedFormat("image/bmp")).isFalse();
        assertThat(properties.isAllowedFormat("application/pdf")).isFalse();
    }

    @Test
    @DisplayName("Should return false for null format")
    void shouldReturnFalseForNullFormat() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.isAllowedFormat(null)).isFalse();
    }

    @Test
    @DisplayName("Should be case-insensitive for format check")
    void shouldBeCaseInsensitiveForFormatCheck() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.isAllowedFormat("IMAGE/JPEG")).isTrue();
        assertThat(properties.isAllowedFormat("Image/Png")).isTrue();
    }

    @Test
    @DisplayName("Should detect when resize is needed")
    void shouldDetectWhenResizeIsNeeded() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.needsResize(3000, 2000)).isTrue();
        assertThat(properties.needsResize(2000, 3000)).isTrue();
        assertThat(properties.needsResize(3000, 3000)).isTrue();
    }

    @Test
    @DisplayName("Should detect when resize is not needed")
    void shouldDetectWhenResizeIsNotNeeded() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.needsResize(2048, 2048)).isFalse();
        assertThat(properties.needsResize(1024, 768)).isFalse();
        assertThat(properties.needsResize(100, 100)).isFalse();
    }

    @Test
    @DisplayName("Should have default thumbnail config")
    void shouldHaveDefaultThumbnailConfig() {
        ImageProperties properties = new ImageProperties();
        ImageProperties.ThumbnailConfig thumbnail = properties.getThumbnail();

        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.getDefaultSizes()).isEqualTo(Arrays.asList(64, 128, 256));
        assertThat(thumbnail.getDefaultQuality()).isEqualTo(80);
        assertThat(thumbnail.getMaxDimension()).isEqualTo(512);
    }

    @Test
    @DisplayName("Should have default optimization config")
    void shouldHaveDefaultOptimizationConfig() {
        ImageProperties properties = new ImageProperties();
        ImageProperties.OptimizationConfig optim = properties.getOptimization();

        assertThat(optim).isNotNull();
        assertThat(optim.isEnableWebpConversion()).isTrue();
        assertThat(optim.getWebpQuality()).isEqualTo(80);
        assertThat(optim.isWebpLossless()).isFalse();
        assertThat(optim.getMinSizeForOptimization()).isEqualTo(10240L);
    }

    @Test
    @DisplayName("Should have default convertible formats for WebP")
    void shouldHaveDefaultConvertibleFormatsForWebp() {
        ImageProperties properties = new ImageProperties();

        assertThat(properties.getOptimization().getConvertibleFormats()).containsExactlyInAnyOrder(
            "image/jpeg", "image/png", "image/bmp"
        );
    }

    @Test
    @DisplayName("OptimizationConfig should correctly identify convertible formats")
    void optimizationConfigShouldCorrectlyIdentifyConvertibleFormats() {
        ImageProperties.OptimizationConfig optim = new ImageProperties.OptimizationConfig();

        assertThat(optim.canConvertToWebp("image/jpeg")).isTrue();
        assertThat(optim.canConvertToWebp("image/png")).isTrue();
        assertThat(optim.canConvertToWebp("image/bmp")).isTrue();
        assertThat(optim.canConvertToWebp("image/gif")).isFalse();
        assertThat(optim.canConvertToWebp("image/webp")).isFalse();
    }

    @Test
    @DisplayName("OptimizationConfig should return false for null MIME type")
    void optimizationConfigShouldReturnFalseForNullMimeType() {
        ImageProperties.OptimizationConfig optim = new ImageProperties.OptimizationConfig();

        assertThat(optim.canConvertToWebp(null)).isFalse();
    }

    @Test
    @DisplayName("OptimizationConfig should return false when conversion is disabled")
    void optimizationConfigShouldReturnFalseWhenConversionIsDisabled() {
        ImageProperties.OptimizationConfig optim = new ImageProperties.OptimizationConfig();
        optim.setEnableWebpConversion(false);

        assertThat(optim.canConvertToWebp("image/jpeg")).isFalse();
    }

    @Test
    @DisplayName("Should allow setting custom values")
    void shouldAllowSettingCustomValues() {
        ImageProperties properties = new ImageProperties();
        properties.setDefaultMaxWidth(4096);
        properties.setDefaultMaxHeight(4096);
        properties.setDefaultQuality(90);
        properties.setMaxFileSize(DataSize.ofMegabytes(50));

        assertThat(properties.getDefaultMaxWidth()).isEqualTo(4096);
        assertThat(properties.getDefaultMaxHeight()).isEqualTo(4096);
        assertThat(properties.getDefaultQuality()).isEqualTo(90);
        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(50));
    }
}
