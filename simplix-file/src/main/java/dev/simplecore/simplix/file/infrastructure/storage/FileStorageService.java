package dev.simplecore.simplix.file.infrastructure.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Abstraction layer for file storage operations.
 * <p>
 * Provides a unified interface for storing, retrieving, and deleting files
 * regardless of the underlying storage implementation (local filesystem, S3, etc.).
 * <p>
 * Implementations:
 * <ul>
 *   <li>LocalFileStorageService - Local filesystem storage</li>
 *   <li>S3FileStorageService - AWS S3 storage (future)</li>
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
     * @throws dev.simplecore.simplix.file.infrastructure.exception.StorageException if file not found
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
}
