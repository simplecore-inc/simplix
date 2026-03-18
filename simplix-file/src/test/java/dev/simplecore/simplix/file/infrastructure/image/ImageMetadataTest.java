package dev.simplecore.simplix.file.infrastructure.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ImageMetadata Record")
class ImageMetadataTest {

    @Test
    @DisplayName("Should create instance with all fields")
    void shouldCreateInstanceWithAllFields() {
        ImageMetadata metadata = new ImageMetadata(1920, 1080, "JPEG");

        assertThat(metadata.width()).isEqualTo(1920);
        assertThat(metadata.height()).isEqualTo(1080);
        assertThat(metadata.format()).isEqualTo("JPEG");
    }

    @Test
    @DisplayName("Should detect landscape orientation when width is greater than height")
    void shouldDetectLandscapeOrientation() {
        ImageMetadata metadata = new ImageMetadata(1920, 1080, "JPEG");

        assertThat(metadata.isLandscape()).isTrue();
        assertThat(metadata.isPortrait()).isFalse();
        assertThat(metadata.isSquare()).isFalse();
    }

    @Test
    @DisplayName("Should detect portrait orientation when height is greater than width")
    void shouldDetectPortraitOrientation() {
        ImageMetadata metadata = new ImageMetadata(1080, 1920, "PNG");

        assertThat(metadata.isPortrait()).isTrue();
        assertThat(metadata.isLandscape()).isFalse();
        assertThat(metadata.isSquare()).isFalse();
    }

    @Test
    @DisplayName("Should detect square orientation when width equals height")
    void shouldDetectSquareOrientation() {
        ImageMetadata metadata = new ImageMetadata(500, 500, "PNG");

        assertThat(metadata.isSquare()).isTrue();
        assertThat(metadata.isLandscape()).isFalse();
        assertThat(metadata.isPortrait()).isFalse();
    }

    @Test
    @DisplayName("Should calculate correct aspect ratio for landscape image")
    void shouldCalculateCorrectAspectRatioForLandscape() {
        ImageMetadata metadata = new ImageMetadata(1920, 1080, "JPEG");

        assertThat(metadata.getAspectRatio()).isCloseTo(1.7778, within(0.001));
    }

    @Test
    @DisplayName("Should calculate correct aspect ratio for portrait image")
    void shouldCalculateCorrectAspectRatioForPortrait() {
        ImageMetadata metadata = new ImageMetadata(1080, 1920, "JPEG");

        assertThat(metadata.getAspectRatio()).isCloseTo(0.5625, within(0.001));
    }

    @Test
    @DisplayName("Should return aspect ratio of 1.0 for square image")
    void shouldReturnAspectRatioOfOneForSquare() {
        ImageMetadata metadata = new ImageMetadata(500, 500, "PNG");

        assertThat(metadata.getAspectRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should return zero aspect ratio when height is zero")
    void shouldReturnZeroAspectRatioWhenHeightIsZero() {
        ImageMetadata metadata = new ImageMetadata(500, 0, "PNG");

        assertThat(metadata.getAspectRatio()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should support equality based on all fields")
    void shouldSupportEqualityBasedOnAllFields() {
        ImageMetadata metadata1 = new ImageMetadata(800, 600, "JPEG");
        ImageMetadata metadata2 = new ImageMetadata(800, 600, "JPEG");

        assertThat(metadata1).isEqualTo(metadata2);
    }
}
