package dev.simplecore.simplix.file.infrastructure.upload;

import dev.simplecore.simplix.file.enums.FileCategory;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;

/**
 * Result of file processing and storage operation.
 * <p>
 * Contains all information needed by the application layer to create
 * domain entities without exposing domain dependencies to the file module.
 *
 * @param storedInfo       storage information (path, checksum, etc.)
 * @param category         detected file category (IMAGE, VIDEO, DOCUMENT, etc.)
 * @param mimeType         final MIME type (may differ from original if converted)
 * @param width            image width in pixels (null for non-images)
 * @param height           image height in pixels (null for non-images)
 * @param wasOptimized     true if WebP optimization was applied
 * @param originalMimeType original MIME type before any conversion
 */
public record ProcessedFileResult(
    StoredFileInfo storedInfo,
    FileCategory category,
    String mimeType,
    Integer width,
    Integer height,
    boolean wasOptimized,
    String originalMimeType
) {

    /**
     * Check if this result represents an image file.
     *
     * @return true if the processed file is an image
     */
    public boolean isImage() {
        return category == FileCategory.IMAGE;
    }

    /**
     * Check if this result represents a video file.
     *
     * @return true if the processed file is a video
     */
    public boolean isVideo() {
        return category == FileCategory.VIDEO;
    }

    /**
     * Check if this result represents an audio file.
     *
     * @return true if the processed file is an audio file
     */
    public boolean isAudio() {
        return category == FileCategory.AUDIO;
    }

    /**
     * Check if this result represents a document.
     *
     * @return true if the processed file is a document
     */
    public boolean isDocument() {
        return category == FileCategory.DOCUMENT;
    }

    /**
     * Get the file size in bytes.
     *
     * @return file size in bytes
     */
    public long getFileSize() {
        return storedInfo.fileSize();
    }

    /**
     * Get the stored file path.
     *
     * @return stored file path
     */
    public String getStoredPath() {
        return storedInfo.storedPath();
    }

    /**
     * Get the original filename.
     *
     * @return original filename
     */
    public String getOriginalName() {
        return storedInfo.originalName();
    }

    /**
     * Get the file extension without dot.
     *
     * @return file extension (e.g., "jpg", "pdf")
     */
    public String getExtension() {
        return storedInfo.extension();
    }

    /**
     * Get the file checksum.
     *
     * @return SHA-256 checksum of the stored file
     */
    public String getChecksum() {
        return storedInfo.checksum();
    }

    /**
     * Check if the MIME type was changed during processing.
     * <p>
     * This typically happens when WebP optimization is applied.
     *
     * @return true if MIME type changed
     */
    public boolean wasMimeTypeChanged() {
        return !mimeType.equals(originalMimeType);
    }
}
