package dev.simplecore.simplix.file.infrastructure.exception;

import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException.FileErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileUploadException")
class FileUploadExceptionTest {

    @Test
    @DisplayName("Should create exception with error code and message")
    void shouldCreateExceptionWithErrorCodeAndMessage() {
        FileUploadException exception = new FileUploadException(
            FileErrorCode.EMPTY_FILE, "File is empty"
        );

        assertThat(exception.getErrorCode()).isEqualTo(FileErrorCode.EMPTY_FILE);
        assertThat(exception.getMessage()).isEqualTo("File is empty");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with error code, message, and cause")
    void shouldCreateExceptionWithErrorCodeMessageAndCause() {
        Throwable cause = new RuntimeException("IO error");
        FileUploadException exception = new FileUploadException(
            FileErrorCode.STORAGE_WRITE_FAILED, "Storage write failed", cause
        );

        assertThat(exception.getErrorCode()).isEqualTo(FileErrorCode.STORAGE_WRITE_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Storage write failed");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should be a RuntimeException")
    void shouldBeARuntimeException() {
        FileUploadException exception = new FileUploadException(
            FileErrorCode.FILE_SIZE_EXCEEDED, "Too large"
        );

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should define all file error codes")
    void shouldDefineAllFileErrorCodes() {
        FileErrorCode[] codes = FileErrorCode.values();

        assertThat(codes).containsExactlyInAnyOrder(
            FileErrorCode.EMPTY_FILE,
            FileErrorCode.FILE_SIZE_EXCEEDED,
            FileErrorCode.INVALID_FILE_TYPE,
            FileErrorCode.INVALID_EXTENSION,
            FileErrorCode.VIRUS_DETECTED,
            FileErrorCode.STORAGE_QUOTA_EXCEEDED,
            FileErrorCode.STORAGE_WRITE_FAILED,
            FileErrorCode.CHECKSUM_MISMATCH,
            FileErrorCode.MAX_FILES_EXCEEDED,
            FileErrorCode.INVALID_IMAGE,
            FileErrorCode.IMAGE_PROCESSING_FAILED,
            FileErrorCode.ATTACHMENT_NOT_FOUND
        );
    }
}
