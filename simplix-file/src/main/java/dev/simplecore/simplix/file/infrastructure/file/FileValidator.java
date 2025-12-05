package dev.simplecore.simplix.file.infrastructure.file;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.ImageProperties;
import dev.simplecore.simplix.file.enums.FileCategory;
import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException;
import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException.FileErrorCode;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

/**
 * File validation component for upload operations.
 * <p>
 * Validates file size, MIME type, and extension before storage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileValidator {

    private final FileProperties fileProperties;
    private final ImageProperties imageProperties;
    private final ImageProcessingService imageProcessingService;

    // MIME type to extension mapping for validation
    private static final Map<String, Set<String>> MIME_TO_EXTENSIONS = Map.ofEntries(
        // Images
        Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
        Map.entry("image/png", Set.of("png")),
        Map.entry("image/gif", Set.of("gif")),
        Map.entry("image/webp", Set.of("webp")),
        Map.entry("image/svg+xml", Set.of("svg")),
        Map.entry("image/bmp", Set.of("bmp")),
        // Documents
        Map.entry("application/pdf", Set.of("pdf")),
        Map.entry("application/msword", Set.of("doc")),
        Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
        Map.entry("application/vnd.ms-excel", Set.of("xls")),
        Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
        Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
        Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx")),
        // Archives
        Map.entry("application/zip", Set.of("zip")),
        Map.entry("application/x-rar-compressed", Set.of("rar")),
        Map.entry("application/x-7z-compressed", Set.of("7z")),
        // Text
        Map.entry("text/plain", Set.of("txt")),
        Map.entry("text/csv", Set.of("csv"))
    );

    /**
     * Validate file for general upload.
     *
     * @param file              file to validate
     * @param maxSize           maximum file size (null to use default)
     * @param allowedMimeTypes  allowed MIME types (null to use default)
     * @throws FileUploadException if validation fails
     */
    public void validate(MultipartFile file, DataSize maxSize, Set<String> allowedMimeTypes) {
        // Check empty file
        if (file == null || file.isEmpty()) {
            throw new FileUploadException(FileErrorCode.EMPTY_FILE, "File is empty");
        }

        // Check file size
        DataSize effectiveMaxSize = maxSize != null ? maxSize : fileProperties.getDefaultMaxSize();
        if (file.getSize() > effectiveMaxSize.toBytes()) {
            throw new FileUploadException(
                FileErrorCode.FILE_SIZE_EXCEEDED,
                String.format("File size %d bytes exceeds maximum %d bytes",
                    file.getSize(), effectiveMaxSize.toBytes())
            );
        }

        // Check MIME type
        String mimeType = file.getContentType();
        Set<String> effectiveAllowedTypes = allowedMimeTypes != null ?
            allowedMimeTypes : fileProperties.getAllowedMimeTypes();

        if (mimeType == null || !effectiveAllowedTypes.contains(mimeType.toLowerCase())) {
            throw new FileUploadException(
                FileErrorCode.INVALID_FILE_TYPE,
                String.format("File type '%s' is not allowed", mimeType)
            );
        }

        // Validate extension matches MIME type
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.isEmpty()) {
            String extension = extractExtension(filename);
            validateMimeTypeExtension(mimeType, extension);
        }

        log.debug("Validated file: {}, size: {}, type: {}",
            file.getOriginalFilename(), file.getSize(), mimeType);
    }

    /**
     * Validate file for image upload.
     *
     * @param file      file to validate
     * @param maxWidth  maximum allowed width (null to use default)
     * @param maxHeight maximum allowed height (null to use default)
     * @throws FileUploadException if validation fails
     */
    public void validateImage(MultipartFile file, Integer maxWidth, Integer maxHeight) {
        // Check empty file
        if (file == null || file.isEmpty()) {
            throw new FileUploadException(FileErrorCode.EMPTY_FILE, "File is empty");
        }

        // Check file size
        if (file.getSize() > imageProperties.getMaxFileSize().toBytes()) {
            throw new FileUploadException(
                FileErrorCode.FILE_SIZE_EXCEEDED,
                String.format("Image size %d bytes exceeds maximum %d bytes",
                    file.getSize(), imageProperties.getMaxFileSize().toBytes())
            );
        }

        // Check MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !imageProperties.isAllowedFormat(mimeType)) {
            throw new FileUploadException(
                FileErrorCode.INVALID_FILE_TYPE,
                String.format("Image type '%s' is not allowed", mimeType)
            );
        }

        // Verify it's actually an image
        if (!imageProcessingService.isImage(mimeType)) {
            throw new FileUploadException(
                FileErrorCode.INVALID_IMAGE,
                "File is not a valid image"
            );
        }

        log.debug("Validated image: {}, size: {}, type: {}",
            file.getOriginalFilename(), file.getSize(), mimeType);
    }

    /**
     * Determine file category from MIME type and extension.
     *
     * @param mimeType  MIME type
     * @param extension file extension
     * @return FileCategory enum value
     */
    public FileCategory determineFileCategory(String mimeType, String extension) {
        if (mimeType == null) {
            return FileCategory.OTHER;
        }

        String lowerMime = mimeType.toLowerCase();

        if (lowerMime.startsWith("image/")) {
            return FileCategory.IMAGE;
        }
        if (lowerMime.startsWith("video/")) {
            return FileCategory.VIDEO;
        }
        if (lowerMime.startsWith("audio/")) {
            return FileCategory.AUDIO;
        }
        if (isDocumentMimeType(lowerMime)) {
            return FileCategory.DOCUMENT;
        }
        if (isArchiveMimeType(lowerMime)) {
            return FileCategory.ARCHIVE;
        }

        return FileCategory.OTHER;
    }

    // ==================== Private Methods ====================

    private void validateMimeTypeExtension(String mimeType, String extension) {
        if (mimeType == null || extension == null || extension.isEmpty()) {
            return; // Skip validation if we can't determine
        }

        Set<String> validExtensions = MIME_TO_EXTENSIONS.get(mimeType.toLowerCase());
        if (validExtensions != null && !validExtensions.contains(extension.toLowerCase())) {
            log.warn("MIME type '{}' does not match extension '{}'", mimeType, extension);
            // Just warn, don't fail - some systems have inconsistent MIME types
        }
    }

    private String extractExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    private boolean isDocumentMimeType(String mimeType) {
        return mimeType.equals("application/pdf") ||
               mimeType.equals("application/msword") ||
               mimeType.contains("wordprocessingml") ||
               mimeType.contains("spreadsheetml") ||
               mimeType.contains("presentationml") ||
               mimeType.equals("application/vnd.ms-excel") ||
               mimeType.equals("application/vnd.ms-powerpoint") ||
               mimeType.equals("text/plain") ||
               mimeType.equals("text/csv") ||
               mimeType.equals("text/rtf");
    }

    private boolean isArchiveMimeType(String mimeType) {
        return mimeType.equals("application/zip") ||
               mimeType.equals("application/x-zip-compressed") ||
               mimeType.equals("application/x-rar-compressed") ||
               mimeType.equals("application/x-7z-compressed") ||
               mimeType.equals("application/gzip") ||
               mimeType.equals("application/x-tar");
    }
}
