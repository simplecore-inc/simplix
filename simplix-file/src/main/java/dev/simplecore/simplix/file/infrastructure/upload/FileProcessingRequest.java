package dev.simplecore.simplix.file.infrastructure.upload;

import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Request for file processing operation.
 * <p>
 * Contains file data and optional processing parameters.
 * This is a domain-agnostic request that does not include entity information.
 *
 * @param file                   the file to process
 * @param directory              target storage directory (e.g., "images", "files")
 * @param maxFileSize            optional max file size override
 * @param allowedMimeTypes       optional allowed MIME types override
 * @param maxWidth               optional max image width (for images)
 * @param maxHeight              optional max image height (for images)
 * @param enableWebpOptimization override WebP optimization setting (null = use default)
 */
public record FileProcessingRequest(
    MultipartFile file,
    String directory,
    DataSize maxFileSize,
    Set<String> allowedMimeTypes,
    Integer maxWidth,
    Integer maxHeight,
    Boolean enableWebpOptimization
) {

    /**
     * Create a simple request with just file and directory.
     *
     * @param file      the file to process
     * @param directory target storage directory
     * @return new processing request
     */
    public static FileProcessingRequest of(MultipartFile file, String directory) {
        return new FileProcessingRequest(file, directory, null, null, null, null, null);
    }

    /**
     * Create a request for image processing with custom dimensions.
     *
     * @param file      the image file to process
     * @param maxWidth  maximum width
     * @param maxHeight maximum height
     * @return new processing request configured for images
     */
    public static FileProcessingRequest forImage(MultipartFile file, Integer maxWidth, Integer maxHeight) {
        return new FileProcessingRequest(file, "images", null, null, maxWidth, maxHeight, null);
    }

    /**
     * Create a request for image processing with default dimensions.
     *
     * @param file the image file to process
     * @return new processing request configured for images
     */
    public static FileProcessingRequest forImage(MultipartFile file) {
        return forImage(file, null, null);
    }

    /**
     * Create a builder for more complex request configuration.
     *
     * @param file the file to process
     * @return new builder instance
     */
    public static Builder builder(MultipartFile file) {
        return new Builder(file);
    }

    /**
     * Builder for FileProcessingRequest.
     */
    public static class Builder {
        private final MultipartFile file;
        private String directory = "files";
        private DataSize maxFileSize;
        private Set<String> allowedMimeTypes;
        private Integer maxWidth;
        private Integer maxHeight;
        private Boolean enableWebpOptimization;

        private Builder(MultipartFile file) {
            this.file = file;
        }

        public Builder directory(String directory) {
            this.directory = directory;
            return this;
        }

        public Builder maxFileSize(DataSize maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }

        public Builder allowedMimeTypes(Set<String> allowedMimeTypes) {
            this.allowedMimeTypes = allowedMimeTypes;
            return this;
        }

        public Builder maxWidth(Integer maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder maxHeight(Integer maxHeight) {
            this.maxHeight = maxHeight;
            return this;
        }

        public Builder enableWebpOptimization(Boolean enable) {
            this.enableWebpOptimization = enable;
            return this;
        }

        public FileProcessingRequest build() {
            return new FileProcessingRequest(
                file, directory, maxFileSize, allowedMimeTypes,
                maxWidth, maxHeight, enableWebpOptimization
            );
        }
    }
}
