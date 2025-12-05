package dev.simplecore.simplix.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.Set;

/**
 * File upload configuration properties.
 * <p>
 * Configuration in application.yml:
 * <pre>{@code
 * simplix:
 *   file:
 *     default-max-size: 10MB
 *     allowed-mime-types:
 *       - image/jpeg
 *       - image/png
 *       - application/pdf
 *     max-files-per-request: 10
 *     checksum-algorithm: SHA-256
 *     thumbnail:
 *       cache-path: ./uploads/.thumbnails
 *       cache-enabled: true
 * }</pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "simplix.file")
public class FileProperties {

    /**
     * Enable or disable file module
     */
    private boolean enabled = true;

    /**
     * Default maximum file size
     */
    private DataSize defaultMaxSize = DataSize.ofMegabytes(10);

    /**
     * Allowed MIME types for file upload
     */
    private Set<String> allowedMimeTypes = new HashSet<>(Set.of(
        // Images
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/svg+xml",
        // Documents
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        // Archives
        "application/zip",
        "application/x-rar-compressed",
        "application/x-7z-compressed",
        // Text
        "text/plain",
        "text/csv"
    ));

    /**
     * Maximum number of files per single request
     */
    private int maxFilesPerRequest = 10;

    /**
     * Checksum algorithm for file integrity
     */
    private String checksumAlgorithm = "SHA-256";

    /**
     * Enable virus scanning (requires external service)
     */
    private boolean virusScanEnabled = false;

    /**
     * Thumbnail configuration
     */
    private ThumbnailConfig thumbnail = new ThumbnailConfig();

    /**
     * Check if a MIME type is allowed
     *
     * @param mimeType MIME type to check
     * @return true if allowed
     */
    public boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return allowedMimeTypes.contains(mimeType.toLowerCase());
    }

    /**
     * Thumbnail generation and caching configuration
     */
    @Getter
    @Setter
    public static class ThumbnailConfig {

        /**
         * Path for thumbnail cache (used by local storage)
         */
        private String cachePath = "./uploads/.thumbnails";

        /**
         * Enable thumbnail caching
         */
        private boolean cacheEnabled = true;

        /**
         * Thumbnail prefix path in bucket (used by S3 storage)
         */
        private String s3Prefix = "thumbnails";
    }
}
