package dev.simplecore.simplix.file.infrastructure.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessedImage Record")
class ProcessedImageTest {

    @Test
    @DisplayName("Should create instance with all fields")
    void shouldCreateInstanceWithAllFields() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        ProcessedImage image = new ProcessedImage(data, "image/jpeg", 800, 600);

        assertThat(image.data()).isEqualTo(data);
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
        assertThat(image.width()).isEqualTo(800);
        assertThat(image.height()).isEqualTo(600);
    }

    @Test
    @DisplayName("Should return file size from data length")
    void shouldReturnFileSizeFromDataLength() {
        byte[] data = new byte[1024];
        ProcessedImage image = new ProcessedImage(data, "image/png", 100, 100);

        assertThat(image.getFileSize()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("Should return zero file size when data is null")
    void shouldReturnZeroFileSizeWhenDataIsNull() {
        ProcessedImage image = new ProcessedImage(null, "image/png", 100, 100);

        assertThat(image.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should return zero file size when data is empty")
    void shouldReturnZeroFileSizeWhenDataIsEmpty() {
        ProcessedImage image = new ProcessedImage(new byte[0], "image/png", 100, 100);

        assertThat(image.getFileSize()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should support equality based on all fields")
    void shouldSupportEqualityBasedOnAllFields() {
        byte[] data = new byte[]{1, 2, 3};
        ProcessedImage image1 = new ProcessedImage(data, "image/jpeg", 800, 600);
        ProcessedImage image2 = new ProcessedImage(data, "image/jpeg", 800, 600);

        assertThat(image1).isEqualTo(image2);
    }
}
