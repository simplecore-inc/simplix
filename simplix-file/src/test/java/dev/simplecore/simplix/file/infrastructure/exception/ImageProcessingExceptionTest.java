package dev.simplecore.simplix.file.infrastructure.exception;

import dev.simplecore.simplix.file.infrastructure.exception.ImageProcessingException.ImageErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageProcessingException")
class ImageProcessingExceptionTest {

    @Test
    @DisplayName("Should create exception with error code and message")
    void shouldCreateExceptionWithErrorCodeAndMessage() {
        ImageProcessingException exception = new ImageProcessingException(
            ImageErrorCode.RESIZE_FAILED, "Resize failed"
        );

        assertThat(exception.getErrorCode()).isEqualTo(ImageErrorCode.RESIZE_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Resize failed");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with error code, message, and cause")
    void shouldCreateExceptionWithErrorCodeMessageAndCause() {
        Throwable cause = new RuntimeException("IO error");
        ImageProcessingException exception = new ImageProcessingException(
            ImageErrorCode.THUMBNAIL_GENERATION_FAILED, "Thumbnail failed", cause
        );

        assertThat(exception.getErrorCode()).isEqualTo(ImageErrorCode.THUMBNAIL_GENERATION_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Thumbnail failed");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should be a RuntimeException")
    void shouldBeARuntimeException() {
        ImageProcessingException exception = new ImageProcessingException(
            ImageErrorCode.INVALID_IMAGE_FORMAT, "Bad format"
        );

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should define all image error codes")
    void shouldDefineAllImageErrorCodes() {
        ImageErrorCode[] codes = ImageErrorCode.values();

        assertThat(codes).containsExactlyInAnyOrder(
            ImageErrorCode.INVALID_IMAGE_FORMAT,
            ImageErrorCode.UNSUPPORTED_FORMAT,
            ImageErrorCode.RESIZE_FAILED,
            ImageErrorCode.THUMBNAIL_GENERATION_FAILED,
            ImageErrorCode.METADATA_EXTRACTION_FAILED,
            ImageErrorCode.IMAGE_TOO_LARGE,
            ImageErrorCode.CORRUPTED_IMAGE,
            ImageErrorCode.CONVERSION_FAILED
        );
    }
}
