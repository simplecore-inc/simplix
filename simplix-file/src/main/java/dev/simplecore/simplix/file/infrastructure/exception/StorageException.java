package dev.simplecore.simplix.file.infrastructure.exception;

import lombok.Getter;

/**
 * Exception thrown when storage operations fail.
 */
@Getter
public class StorageException extends RuntimeException {

    private final StorageErrorCode errorCode;

    public StorageException(StorageErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public StorageException(StorageErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Storage operation error codes
     */
    public enum StorageErrorCode {
        WRITE_FAILED,
        READ_FAILED,
        DELETE_FAILED,
        PATH_NOT_FOUND,
        PERMISSION_DENIED,
        CHECKSUM_CALCULATION_FAILED,
        INVALID_PATH,
        STORAGE_FULL
    }
}
