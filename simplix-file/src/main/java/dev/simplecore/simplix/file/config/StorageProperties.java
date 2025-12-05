package dev.simplecore.simplix.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
    }
}
