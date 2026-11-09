package dev.simplecore.simplix.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage configuration properties for file upload system.
 * <p>
 * Configuration in application.yml:
 * <pre>{@code
 * simplix:
 *   file:
 *     storage:
 *       provider: local  # local or s3
 *       local:
 *         base-path: ./uploads
 *         public-url-prefix: /files
 *       s3:
 *         endpoint: http://localhost:9000  # MinIO/RustFS endpoint
 *         access-key: minioadmin
 *         secret-key: minioadmin
 *         bucket: my-bucket
 *         region: us-east-1
 *         path-style-access: true  # Required for MinIO/RustFS
 *         multipart-part-size: 8MB  # Upload part size, between 5MB and 512MB
 * }</pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "simplix.file.storage")
public class StorageProperties {

    /**
     * Storage provider type: local, s3
     */
    private String provider = "local";

    /**
     * Local storage configuration
     */
    private LocalStorageConfig local = new LocalStorageConfig();

    /**
     * S3 compatible storage configuration (AWS S3, MinIO, RustFS)
     */
    private S3StorageConfig s3 = new S3StorageConfig();

    /**
     * Local file system storage configuration
     */
    @Getter
    @Setter
    public static class LocalStorageConfig {

        /**
         * Base path for file storage
         */
        private String basePath = "./uploads";

        /**
         * Public URL prefix for serving files
         */
        private String publicUrlPrefix = "/files";
    }

    /**
     * S3 compatible storage configuration (AWS S3, MinIO, RustFS)
     */
    @Getter
    @Setter
    public static class S3StorageConfig {

        /**
         * S3 endpoint URL (required for MinIO/RustFS, optional for AWS S3)
         */
        private String endpoint;

        /**
         * AWS access key ID
         */
        private String accessKey;

        /**
         * AWS secret access key
         */
        private String secretKey;

        /**
         * S3 bucket name
         */
        private String bucket;

        /**
         * AWS region (default: us-east-1)
         */
        private String region = "us-east-1";

        /**
         * Use path-style access instead of virtual-hosted style.
         * Required for MinIO and RustFS.
         */
        private boolean pathStyleAccess = true;

        /**
         * Public URL prefix for serving files.
         * If not set, generates presigned URLs.
         */
        private String publicUrlPrefix;

        /**
         * Presigned URL expiration time in minutes (default: 60)
         */
        private int presignedUrlExpiration = 60;

        /**
         * Size of one part of a multipart upload, and so the most memory one {@code store} call
         * holds. A stream smaller than one part is sent in a single request. Values outside
         * {@link #MIN_PART_SIZE} to {@link #MAX_PART_SIZE} are moved to the nearest bound (S3
         * refuses parts under 5 MB). Default: 8 MB.
         */
        private DataSize multipartPartSize = DataSize.ofMegabytes(8);

        /** Smallest part S3 accepts for any part but the last. */
        public static final DataSize MIN_PART_SIZE = DataSize.ofMegabytes(5);

        /** Largest part size this module buffers in memory. */
        public static final DataSize MAX_PART_SIZE = DataSize.ofMegabytes(512);

        /**
         * Names of the settings that must be present before the storage can be used.
         * <p>
         * The module authenticates with a static access key and secret key, so all three of bucket,
         * access key and secret key are required. Only the names are returned, never a value.
         *
         * @return the missing setting names in property notation, empty when the configuration is complete
         */
        public List<String> missingSettings() {
            List<String> missing = new ArrayList<>();
            if (!StringUtils.hasText(bucket)) {
                missing.add("bucket");
            }
            if (!StringUtils.hasText(accessKey)) {
                missing.add("access-key");
            }
            if (!StringUtils.hasText(secretKey)) {
                missing.add("secret-key");
            }
            return missing;
        }

        /**
         * The part size in bytes after it is moved into the supported range.
         *
         * @return the effective part size in bytes
         */
        public int effectivePartSizeBytes() {
            long bytes = multipartPartSize == null ? MIN_PART_SIZE.toBytes() : multipartPartSize.toBytes();
            return (int) Math.max(MIN_PART_SIZE.toBytes(), Math.min(MAX_PART_SIZE.toBytes(), bytes));
        }

        /**
         * An independent copy, so a caller can keep the settings it built a client for while the
         * source goes on editing its own object.
         *
         * @return a copy holding the same values
         */
        public S3StorageConfig copy() {
            S3StorageConfig copy = new S3StorageConfig();
            copy.endpoint = endpoint;
            copy.accessKey = accessKey;
            copy.secretKey = secretKey;
            copy.bucket = bucket;
            copy.region = region;
            copy.pathStyleAccess = pathStyleAccess;
            copy.publicUrlPrefix = publicUrlPrefix;
            copy.presignedUrlExpiration = presignedUrlExpiration;
            copy.multipartPartSize = multipartPartSize;
            return copy;
        }
    }
}
