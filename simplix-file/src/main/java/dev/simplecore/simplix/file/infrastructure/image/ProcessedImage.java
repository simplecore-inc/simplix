package dev.simplecore.simplix.file.infrastructure.image;

/**
 * Record containing processed image data and metadata.
 *
 * @param data     processed image bytes
 * @param mimeType MIME type of the processed image
 * @param width    image width in pixels
 * @param height   image height in pixels
 */
public record ProcessedImage(
    byte[] data,
    String mimeType,
    int width,
    int height
) {

    /**
     * Get file size in bytes.
     *
     * @return size of image data
     */
    public long getFileSize() {
        return data != null ? data.length : 0;
    }
}
