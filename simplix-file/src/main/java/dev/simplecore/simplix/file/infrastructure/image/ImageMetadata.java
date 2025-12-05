package dev.simplecore.simplix.file.infrastructure.image;

/**
 * Record containing image metadata extracted from file.
 *
 * @param width  image width in pixels
 * @param height image height in pixels
 * @param format image format name (e.g., "JPEG", "PNG")
 */
public record ImageMetadata(
    int width,
    int height,
    String format
) {

    /**
     * Check if image is landscape orientation.
     *
     * @return true if width > height
     */
    public boolean isLandscape() {
        return width > height;
    }

    /**
     * Check if image is portrait orientation.
     *
     * @return true if height > width
     */
    public boolean isPortrait() {
        return height > width;
    }

    /**
     * Check if image is square.
     *
     * @return true if width equals height
     */
    public boolean isSquare() {
        return width == height;
    }

    /**
     * Get aspect ratio (width / height).
     *
     * @return aspect ratio
     */
    public double getAspectRatio() {
        if (height == 0) {
            return 0;
        }
        return (double) width / height;
    }
}
