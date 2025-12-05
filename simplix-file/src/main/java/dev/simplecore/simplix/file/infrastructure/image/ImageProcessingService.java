package dev.simplecore.simplix.file.infrastructure.image;

import java.io.InputStream;

/**
 * Service for image processing operations.
 * <p>
 * Provides methods for resizing images, generating thumbnails, and extracting metadata.
 */
public interface ImageProcessingService {

    /**
     * Resize image if it exceeds maximum dimensions.
     * <p>
     * Preserves aspect ratio by default.
     *
     * @param input     image input stream
     * @param maxWidth  maximum width
     * @param maxHeight maximum height
     * @return processed image with resized data, or original if no resize needed
     */
    ProcessedImage resizeIfExceeds(InputStream input, int maxWidth, int maxHeight);

    /**
     * Resize image to specified dimensions.
     *
     * @param input               image input stream
     * @param maxWidth            target max width
     * @param maxHeight           target max height
     * @param preserveAspectRatio whether to preserve aspect ratio
     * @return processed image
     */
    ProcessedImage resize(InputStream input, int maxWidth, int maxHeight, boolean preserveAspectRatio);

    /**
     * Generate thumbnail from image.
     * <p>
     * Creates a square thumbnail centered on the image.
     *
     * @param input  image input stream
     * @param width  target width
     * @param height target height
     * @return thumbnail image
     */
    ProcessedImage generateThumbnail(InputStream input, int width, int height);

    /**
     * Extract metadata from image without fully loading it.
     *
     * @param input image input stream
     * @return image metadata (dimensions, format)
     */
    ImageMetadata extractMetadata(InputStream input);

    /**
     * Check if a MIME type represents an image.
     *
     * @param mimeType MIME type to check
     * @return true if it's an image type
     */
    boolean isImage(String mimeType);

    /**
     * Check if the image format is supported for processing.
     *
     * @param mimeType MIME type to check
     * @return true if format is supported
     */
    boolean isSupported(String mimeType);

    /**
     * Convert image to WebP format for optimization.
     * <p>
     * WebP provides superior compression compared to JPEG/PNG while maintaining quality.
     *
     * @param input    image input stream
     * @param quality  WebP quality (1-100)
     * @param lossless whether to use lossless compression
     * @return processed image in WebP format
     */
    ProcessedImage convertToWebp(InputStream input, int quality, boolean lossless);

    /**
     * Optimize image by resizing (if needed) and converting to WebP.
     * <p>
     * Combines resize and WebP conversion in one operation for efficiency.
     *
     * @param input     image input stream
     * @param maxWidth  maximum width
     * @param maxHeight maximum height
     * @param quality   WebP quality (1-100)
     * @return optimized image in WebP format
     */
    ProcessedImage optimizeAndConvertToWebp(InputStream input, int maxWidth, int maxHeight, int quality);

    /**
     * Check if WebP conversion is supported on this platform.
     *
     * @return true if WebP encoding is available
     */
    boolean isWebpSupported();
}
