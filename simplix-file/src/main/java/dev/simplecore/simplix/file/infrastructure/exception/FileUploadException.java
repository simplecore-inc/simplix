package dev.simplecore.simplix.file.infrastructure.exception;

import lombok.Getter;

/**
 * Exception thrown when file upload operations fail.
 */
@Getter
public class FileUploadException extends RuntimeException {

    private final FileErrorCode errorCode;

    public FileUploadException(FileErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileUploadException(FileErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * File upload error codes
     */
    public enum FileErrorCode {
        EMPTY_FILE,
        FILE_SIZE_EXCEEDED,
        INVALID_FILE_TYPE,
        INVALID_EXTENSION,
        VIRUS_DETECTED,
        STORAGE_QUOTA_EXCEEDED,
        STORAGE_WRITE_FAILED,
        CHECKSUM_MISMATCH,
        MAX_FILES_EXCEEDED,
        INVALID_IMAGE,
        IMAGE_PROCESSING_FAILED,
        ATTACHMENT_NOT_FOUND
    }
}
