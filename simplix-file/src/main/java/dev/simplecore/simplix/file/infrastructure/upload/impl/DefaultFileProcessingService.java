package dev.simplecore.simplix.file.infrastructure.upload.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.ImageProperties;
import dev.simplecore.simplix.file.enums.FileCategory;
import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException;
import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException.FileErrorCode;
import dev.simplecore.simplix.file.infrastructure.file.FileValidator;
import dev.simplecore.simplix.file.infrastructure.image.ImageMetadata;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import dev.simplecore.simplix.file.infrastructure.storage.FileStorageService;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import dev.simplecore.simplix.file.infrastructure.upload.FileProcessingRequest;
import dev.simplecore.simplix.file.infrastructure.upload.FileProcessingService;
import dev.simplecore.simplix.file.infrastructure.upload.ProcessedFileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Default implementation of FileProcessingService.
 * <p>
 * Provides domain-agnostic file processing including:
 * <ul>
 *   <li>File validation and category detection</li>
 *   <li>Image processing with automatic WebP optimization</li>
 *   <li>File storage coordination</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultFileProcessingService implements FileProcessingService {

    private final FileStorageService storageService;
    private final ImageProcessingService imageProcessingService;
    private final FileValidator fileValidator;
    private final FileProperties fileProperties;
    private final ImageProperties imageProperties;

    @Override
    public ProcessedFileResult processAndStore(FileProcessingRequest request) {
        MultipartFile file = request.file();

        // Validate file
        fileValidator.validate(file, request.maxFileSize(), request.allowedMimeTypes());

        // Determine file category
        FileCategory category = determineCategory(file);

        // Process based on category
        if (category == FileCategory.IMAGE && imageProcessingService.isSupported(file.getContentType())) {
            return processImage(request);
        } else {
            return processGenericFile(file, request.directory(), category);
        }
    }

    @Override
    public ProcessedFileResult processAndStoreImage(MultipartFile file, Integer maxWidth, Integer maxHeight) {
        FileProcessingRequest request = FileProcessingRequest.builder(file)
            .directory("images")
            .maxWidth(maxWidth)
            .maxHeight(maxHeight)
            .build();
        return processImage(request);
    }

    @Override
    public ProcessedFileResult processAndStoreGenericFile(MultipartFile file, String directory) {
        FileCategory category = determineCategory(file);
        return processGenericFile(file, directory, category);
    }

    @Override
    public FileCategory determineCategory(MultipartFile file) {
        return fileValidator.determineFileCategory(
            file.getContentType(),
            extractExtension(file.getOriginalFilename())
        );
    }

    @Override
    public boolean shouldOptimizeToWebp(MultipartFile file) {
        var optimConfig = imageProperties.getOptimization();

        // Check if conversion is enabled and WebP is supported
        if (!optimConfig.isEnableWebpConversion() || !imageProcessingService.isWebpSupported()) {
            return false;
        }

        // Check if MIME type can be converted
        if (!optimConfig.canConvertToWebp(file.getContentType())) {
            return false;
        }

        // Check minimum file size threshold
        return file.getSize() >= optimConfig.getMinSizeForOptimization();
    }

    @Override
    public String changeExtensionToWebp(String filename) {
        if (filename == null) {
            return "image.webp";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return filename + ".webp";
        }
        return filename.substring(0, lastDot) + ".webp";
    }

    @Override
    public String extractExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot + 1).toLowerCase();
    }

    // ==================== Private Methods ====================

    private ProcessedFileResult processImage(FileProcessingRequest request) {
        MultipartFile file = request.file();
        int maxWidth = request.maxWidth() != null ? request.maxWidth() : imageProperties.getDefaultMaxWidth();
        int maxHeight = request.maxHeight() != null ? request.maxHeight() : imageProperties.getDefaultMaxHeight();
        String originalMimeType = file.getContentType();

        try {
            ImageMetadata metadata = imageProcessingService.extractMetadata(file.getInputStream());
            StoredFileInfo storedInfo;
            int finalWidth = metadata.width();
            int finalHeight = metadata.height();
            String finalMimeType = file.getContentType();
            boolean wasOptimized = false;

            // Determine if WebP optimization should be applied
            boolean shouldOptimize = shouldOptimizeToWebp(file);
            if (request.enableWebpOptimization() != null) {
                shouldOptimize = request.enableWebpOptimization() && shouldOptimize;
            }

            if (shouldOptimize) {
                // Optimize and convert to WebP in one operation
                var optimConfig = imageProperties.getOptimization();
                ProcessedImage optimized = imageProcessingService.optimizeAndConvertToWebp(
                    file.getInputStream(),
                    maxWidth,
                    maxHeight,
                    optimConfig.getWebpQuality()
                );

                String webpFilename = changeExtensionToWebp(file.getOriginalFilename());
                storedInfo = storageService.store(
                    new ByteArrayInputStream(optimized.data()),
                    webpFilename,
                    optimized.mimeType(),
                    "images"
                );
                finalWidth = optimized.width();
                finalHeight = optimized.height();
                finalMimeType = optimized.mimeType();
                wasOptimized = true;

                log.info("Processed and optimized to WebP: {} -> {} ({}x{} -> {}x{}, {} bytes)",
                    file.getOriginalFilename(), webpFilename,
                    metadata.width(), metadata.height(), finalWidth, finalHeight,
                    optimized.data().length);

            } else if (metadata.width() > maxWidth || metadata.height() > maxHeight) {
                // Only resize without WebP conversion
                ProcessedImage resized = imageProcessingService.resizeIfExceeds(
                    file.getInputStream(), maxWidth, maxHeight
                );

                storedInfo = storageService.store(
                    new ByteArrayInputStream(resized.data()),
                    file.getOriginalFilename(),
                    resized.mimeType(),
                    "images"
                );
                finalWidth = resized.width();
                finalHeight = resized.height();
                finalMimeType = resized.mimeType();

                log.info("Processed and resized image: {} ({}x{} -> {}x{})",
                    file.getOriginalFilename(), metadata.width(), metadata.height(), finalWidth, finalHeight);
            } else {
                // Store as-is
                storedInfo = storeMultipartFile(file, "images");
                log.info("Stored image: {} ({}x{})", file.getOriginalFilename(), finalWidth, finalHeight);
            }

            return new ProcessedFileResult(
                storedInfo,
                FileCategory.IMAGE,
                finalMimeType,
                finalWidth,
                finalHeight,
                wasOptimized,
                originalMimeType
            );

        } catch (IOException e) {
            throw new FileUploadException(
                FileErrorCode.IMAGE_PROCESSING_FAILED,
                "Failed to process image: " + file.getOriginalFilename(),
                e
            );
        }
    }

    private ProcessedFileResult processGenericFile(MultipartFile file, String directory, FileCategory category) {
        StoredFileInfo storedInfo = storeMultipartFile(file, directory);
        log.info("Stored file: {} -> {} ({})", storedInfo.originalName(), storedInfo.storedPath(), category);

        return new ProcessedFileResult(
            storedInfo,
            category,
            file.getContentType(),
            null,
            null,
            false,
            file.getContentType()
        );
    }

    private StoredFileInfo storeMultipartFile(MultipartFile file, String directory) {
        try {
            return storageService.store(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                directory
            );
        } catch (IOException e) {
            throw new FileUploadException(
                FileErrorCode.STORAGE_WRITE_FAILED,
                "Failed to store file: " + file.getOriginalFilename(),
                e
            );
        }
    }
}
