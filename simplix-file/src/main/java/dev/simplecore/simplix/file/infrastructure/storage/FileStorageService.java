package dev.simplecore.simplix.file.infrastructure.storage;

import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.time.Duration;

/**
 * Abstraction layer for file storage operations.
 * <p>
 * Provides a unified interface for storing, retrieving, and deleting files
 * regardless of the underlying storage implementation (local filesystem, S3, etc.).
 * <p>
 * Implementations:
 * <ul>
 *   <li>LocalFileStorageService - Local filesystem storage</li>
 *   <li>S3FileStorageService - AWS S3, MinIO, RustFS and other S3 compatible storage</li>
 * </ul>
 */
public interface FileStorageService {

    /**
     * Store a file in the storage system.
     *
     * @param inputStream  file content stream
     * @param originalName original filename for extension extraction
     * @param directory    target directory path (e.g., "2024/12")
     * @return stored file information including path and checksum
     */
    StoredFileInfo store(InputStream inputStream, String originalName, String directory);

    /**
     * Store a file with pre-calculated metadata.
     *
     * @param inputStream  file content stream
     * @param originalName original filename
     * @param mimeType     MIME type of the file
     * @param directory    target directory path
     * @return stored file information
     */
    StoredFileInfo store(InputStream inputStream, String originalName, String mimeType, String directory);

    /**
     * Retrieve a file from storage.
     *
     * @param storedPath full stored path (relative to base)
     * @return Resource for file streaming
     * @throws StorageException if file not found
     */
    Resource retrieve(String storedPath);

    /**
     * Delete a file from storage.
     *
     * @param storedPath full stored path
     * @return true if deleted, false if not found
     */
    boolean delete(String storedPath);

    /**
     * Check if a file exists in storage.
     *
     * @param storedPath full stored path
     * @return true if exists
     */
    boolean exists(String storedPath);

    /**
     * Get public URL for accessing the file.
     *
     * @param storedPath full stored path
     * @return public accessible URL
     */
    String getPublicUrl(String storedPath);

    /**
     * Get or generate a thumbnail for an image.
     *
     * @param storedPath original image path
     * @param width      target width
     * @param height     target height
     * @return Resource for thumbnail streaming
     */
    Resource getThumbnail(String storedPath, int width, int height);

    /**
     * Get thumbnail URL.
     *
     * @param storedPath original image path
     * @param width      target width
     * @param height     target height
     * @return thumbnail URL
     */
    String getThumbnailUrl(String storedPath, int width, int height);

    /**
     * Whether {@link #createDirectUpload} is available, that is whether a client can send a file to
     * the storage backend without the bytes passing through this application.
     *
     * @return true for S3 compatible storage, false otherwise
     */
    default boolean supportsDirectUpload() {
        return false;
    }

    /**
     * Create a time-limited ticket for uploading one file straight to the storage backend.
     * <p>
     * The object key is built exactly as {@code store} builds it, and the ticket fixes the content
     * length and content type, so the backend refuses any other body. The object does not exist
     * until the client has used the ticket; {@link #describe(String)} reads its size and checksum
     * afterwards.
     *
     * @param directory     target directory path (e.g., "releases"), or null for the date path alone
     * @param originalName  original filename for extension extraction
     * @param mimeType      content type the client must send, {@code application/octet-stream} when null
     * @param contentLength exact number of bytes the client will send
     * @param validFor      how long the ticket works, at most seven days
     * @return the ticket
     * @throws StorageException with {@code UNSUPPORTED_OPERATION} when the implementation does not
     *                          support direct upload, with {@code NOT_CONFIGURED} when the settings are incomplete
     */
    default PresignedUpload createDirectUpload(
        String directory, String originalName, String mimeType, long contentLength, Duration validFor
    ) {
        throw new StorageException(
            StorageException.StorageErrorCode.UNSUPPORTED_OPERATION,
            "Direct upload is not supported by " + getClass().getSimpleName()
        );
    }

    /**
     * Read the size, content type, modification time and SHA-256 of a stored object. The content is
     * read once through a bounded buffer and is not returned.
     *
     * @param storedPath full stored path
     * @return the object's facts, with the checksum
     * @throws StorageException with {@code PATH_NOT_FOUND} when the object does not exist
     */
    default StoredObjectInfo describe(String storedPath) {
        return describe(storedPath, true);
    }

    /**
     * Read the facts of a stored object, optionally without the checksum.
     *
     * @param storedPath   full stored path
     * @param withChecksum true to read the whole object once and compute the SHA-256, false for the
     *                     metadata alone, which costs one request and no transfer
     * @return the object's facts; {@link StoredObjectInfo#checksum()} is null when not requested
     * @throws StorageException with {@code PATH_NOT_FOUND} when the object does not exist,
     *                          with {@code UNSUPPORTED_OPERATION} when the implementation cannot describe objects
     */
    default StoredObjectInfo describe(String storedPath, boolean withChecksum) {
        throw new StorageException(
            StorageException.StorageErrorCode.UNSUPPORTED_OPERATION,
            "describe is not supported by " + getClass().getSimpleName()
        );
    }

    /**
     * Get a URL that downloads the file, with a lifetime and a saved file name chosen by the caller.
     * <p>
     * S3 compatible storage without a {@code public-url-prefix} returns a presigned GET URL that is
     * valid for {@code validFor} and makes the browser save the file as {@code downloadFileName}.
     * With a {@code public-url-prefix} the URL is {@code prefix/storedPath} and is not signed, so the
     * lifetime and the file name do not apply. Local storage returns its public URL for the same
     * reason. The default implementation returns {@link #getPublicUrl(String)}.
     *
     * @param storedPath       full stored path
     * @param validFor         lifetime of a signed URL, at most seven days; null for the configured default
     * @param downloadFileName name the browser saves the file under; null to keep the stored name
     * @return a URL for downloading the file
     */
    default String getDownloadUrl(String storedPath, Duration validFor, String downloadFileName) {
        return getPublicUrl(storedPath);
    }

    /**
     * Whether the storage has the settings it needs. An implementation that is not configured yet
     * starts anyway and throws {@code NOT_CONFIGURED} from every operation.
     *
     * @return true when operations can run
     */
    default boolean isConfigured() {
        return true;
    }
}
