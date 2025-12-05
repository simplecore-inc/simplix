package dev.simplecore.simplix.file.infrastructure.exception;

import lombok.Getter;

/**
 * Exception thrown when image processing operations fail.
 */
@Getter
public class ImageProcessingException extends RuntimeException {

    private final ImageErrorCode errorCode;

    public ImageProcessingException(ImageErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ImageProcessingException(ImageErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Image processing error codes
     */
    public enum ImageErrorCode {
        INVALID_IMAGE_FORMAT,
        UNSUPPORTED_FORMAT,
        RESIZE_FAILED,
        THUMBNAIL_GENERATION_FAILED,
        METADATA_EXTRACTION_FAILED,
        IMAGE_TOO_LARGE,
        CORRUPTED_IMAGE,
        CONVERSION_FAILED
    }
}
