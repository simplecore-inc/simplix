package dev.simplecore.simplix.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Image processing configuration properties.
 * <p>
 * Configuration in application.yml:
 * <pre>{@code
 * simplix:
 *   file:
 *     image:
 *       default-max-width: 2048
 *       default-max-height: 2048
 *       default-quality: 85
 *       allowed-formats:
 *         - image/jpeg
 *         - image/png
 *         - image/webp
 *       thumbnail:
 *         default-sizes: [64, 128, 256]
 *         default-quality: 80
 * }</pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "simplix.file.image")
public class ImageProperties {

    /**
     * Default maximum image width
     */
    private int defaultMaxWidth = 2048;

    /**
     * Default maximum image height
     */
    private int defaultMaxHeight = 2048;

    /**
     * Default JPEG quality (1-100)
     */
    private int defaultQuality = 85;

    /**
     * Maximum file size for images
     */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    /**
     * Allowed image MIME types
     */
    private Set<String> allowedFormats = new HashSet<>(Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    ));

    /**
     * Thumbnail configuration
     */
    private ThumbnailConfig thumbnail = new ThumbnailConfig();

    /**
     * Image optimization configuration
     */
    private OptimizationConfig optimization = new OptimizationConfig();

    /**
     * Image optimization configuration
     */
    @Getter
    @Setter
    public static class OptimizationConfig {

        /**
         * Whether to enable automatic WebP conversion
         */
        private boolean enableWebpConversion = true;

        /**
         * WebP quality (1-100, where 100 is lossless)
         */
        private int webpQuality = 80;

        /**
         * Whether to use lossless WebP compression
         */
        private boolean webpLossless = false;

        /**
         * Minimum file size in bytes to apply optimization (skip small images)
         */
        private long minSizeForOptimization = 10240; // 10KB

        /**
         * MIME types eligible for WebP conversion
         */
        private Set<String> convertibleFormats = new HashSet<>(Set.of(
            "image/jpeg",
            "image/png",
            "image/bmp"
        ));

        /**
         * Check if a MIME type can be converted to WebP
         */
        public boolean canConvertToWebp(String mimeType) {
            if (mimeType == null || !enableWebpConversion) {
                return false;
            }
            return convertibleFormats.contains(mimeType.toLowerCase());
        }
    }

    /**
     * Thumbnail generation configuration
     */
    @Getter
    @Setter
    public static class ThumbnailConfig {

        /**
         * Default thumbnail sizes (square dimensions)
         */
        private List<Integer> defaultSizes = Arrays.asList(64, 128, 256);

        /**
         * Thumbnail JPEG quality (1-100)
         */
        private int defaultQuality = 80;

        /**
         * Maximum allowed thumbnail dimension
         */
        private int maxDimension = 512;
    }

    /**
     * Check if a MIME type is allowed for image upload
     *
     * @param mimeType MIME type to check
     * @return true if allowed
     */
    public boolean isAllowedFormat(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return allowedFormats.contains(mimeType.toLowerCase());
    }

    /**
     * Check if given dimensions exceed maximum allowed
     *
     * @param width  image width
     * @param height image height
     * @return true if resize is needed
     */
    public boolean needsResize(int width, int height) {
        return width > defaultMaxWidth || height > defaultMaxHeight;
    }
}
