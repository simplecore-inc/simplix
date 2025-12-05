package dev.simplecore.simplix.file.infrastructure.storage;

/**
 * Immutable record containing information about a stored file.
 * <p>
 * Returned by FileStorageService after successful file storage operation.
 *
 * @param originalName Original filename as uploaded by user
 * @param storedName   Generated storage filename (UUID-based)
 * @param storedPath   Full path in storage system (relative to base path)
 * @param mimeType     MIME type of the file
 * @param fileSize     File size in bytes
 * @param extension    File extension without dot
 * @param checksum     SHA-256 checksum for integrity verification
 */
public record StoredFileInfo(
    String originalName,
    String storedName,
    String storedPath,
    String mimeType,
    Long fileSize,
    String extension,
    String checksum
) {

    /**
     * Get file extension from original filename.
     *
     * @param filename original filename
     * @return extension without dot, or empty string if no extension
     */
    public static String extractExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
}
