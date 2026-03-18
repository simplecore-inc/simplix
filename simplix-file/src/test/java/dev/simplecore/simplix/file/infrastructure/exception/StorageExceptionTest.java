package dev.simplecore.simplix.file.infrastructure.exception;

import dev.simplecore.simplix.file.infrastructure.exception.StorageException.StorageErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StorageException")
class StorageExceptionTest {

    @Test
    @DisplayName("Should create exception with error code and message")
    void shouldCreateExceptionWithErrorCodeAndMessage() {
        StorageException exception = new StorageException(
            StorageErrorCode.WRITE_FAILED, "Write failed"
        );

        assertThat(exception.getErrorCode()).isEqualTo(StorageErrorCode.WRITE_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Write failed");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Should create exception with error code, message, and cause")
    void shouldCreateExceptionWithErrorCodeMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        StorageException exception = new StorageException(
            StorageErrorCode.READ_FAILED, "Read failed", cause
        );

        assertThat(exception.getErrorCode()).isEqualTo(StorageErrorCode.READ_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Read failed");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Should be a RuntimeException")
    void shouldBeARuntimeException() {
        StorageException exception = new StorageException(
            StorageErrorCode.PATH_NOT_FOUND, "Not found"
        );

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should define all storage error codes")
    void shouldDefineAllStorageErrorCodes() {
        StorageErrorCode[] codes = StorageErrorCode.values();

        assertThat(codes).containsExactlyInAnyOrder(
            StorageErrorCode.WRITE_FAILED,
            StorageErrorCode.READ_FAILED,
            StorageErrorCode.DELETE_FAILED,
            StorageErrorCode.PATH_NOT_FOUND,
            StorageErrorCode.PERMISSION_DENIED,
            StorageErrorCode.CHECKSUM_CALCULATION_FAILED,
            StorageErrorCode.INVALID_PATH,
            StorageErrorCode.STORAGE_FULL
        );
    }
}
