package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import dev.simplecore.simplix.file.infrastructure.storage.FileStorageService;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Local filesystem implementation of FileStorageService.
 * <p>
 * Stores files in a configurable base directory with date-based subdirectories.
 */
@Service
@ConditionalOnProperty(name = "simplix.file.storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties storageProperties;
    private final FileProperties fileProperties;
    private final ImageProcessingService imageProcessingService;

    private Path basePath;
    private Path thumbnailPath;

    @PostConstruct
    public void init() {
        this.basePath = Paths.get(storageProperties.getLocal().getBasePath()).toAbsolutePath().normalize();
        this.thumbnailPath = Paths.get(fileProperties.getThumbnail().getCachePath()).toAbsolutePath().normalize();

        try {
            Files.createDirectories(basePath);
            Files.createDirectories(thumbnailPath);
            log.info("Initialized local file storage at: {}", basePath);
        } catch (IOException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.WRITE_FAILED,
                "Could not create storage directories",
                e
            );
        }
    }

    @Override
    public StoredFileInfo store(InputStream inputStream, String originalName, String directory) {
        String mimeType = URLConnection.guessContentTypeFromName(originalName);
        return store(inputStream, originalName, mimeType, directory);
    }

    @Override
    public StoredFileInfo store(InputStream inputStream, String originalName, String mimeType, String directory) {
        try {
            // Generate unique filename
            String extension = StoredFileInfo.extractExtension(originalName);
            String storedName = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);

            // Build storage path with date-based directory
            String datePath = buildDatePath();
            String fullDirectory = directory != null ? directory + "/" + datePath : datePath;
            Path targetDir = basePath.resolve(fullDirectory);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(storedName);
            String storedPath = fullDirectory + "/" + storedName;

            // Copy file and calculate checksum
            byte[] fileBytes = inputStream.readAllBytes();
            String checksum = calculateChecksum(fileBytes);

            Files.write(targetPath, fileBytes);

            log.debug("Stored file: {} -> {}", originalName, storedPath);

            return new StoredFileInfo(
                originalName,
                storedName,
                storedPath,
                mimeType,
                (long) fileBytes.length,
                extension,
                checksum
            );
        } catch (IOException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.WRITE_FAILED,
                "Failed to store file: " + originalName,
                e
            );
        }
    }

    @Override
    public Resource retrieve(String storedPath) {
        try {
            Path filePath = basePath.resolve(storedPath).normalize();

            // Security check: ensure path is within base directory
            if (!filePath.startsWith(basePath)) {
                throw new StorageException(
                    StorageException.StorageErrorCode.INVALID_PATH,
                    "Invalid storage path: " + storedPath
                );
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new StorageException(
                    StorageException.StorageErrorCode.PATH_NOT_FOUND,
                    "File not found: " + storedPath
                );
            }
        } catch (MalformedURLException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.READ_FAILED,
                "Failed to read file: " + storedPath,
                e
            );
        }
    }

    @Override
    public boolean delete(String storedPath) {
        try {
            Path filePath = basePath.resolve(storedPath).normalize();

            // Security check
            if (!filePath.startsWith(basePath)) {
                log.warn("Attempted to delete file outside storage: {}", storedPath);
                return false;
            }

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("Deleted file: {}", storedPath);
                // Delete all associated thumbnails
                deleteThumbnails(storedPath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete file: {}", storedPath, e);
            return false;
        }
    }

    /**
     * Delete all cached thumbnails for the given stored path.
     * <p>
     * Scans thumbnail cache directory for all size variants and removes them.
     *
     * @param storedPath the original file's stored path
     */
    private void deleteThumbnails(String storedPath) {
        if (!fileProperties.getThumbnail().isCacheEnabled()) {
            return;
        }

        try {
            // Scan thumbnail cache directory for matching files
            // Thumbnail format: {width}x{height}/{storedPath}
            if (!Files.exists(thumbnailPath)) {
                return;
            }

            // List all size directories (e.g., 64x64, 128x128, 256x256)
            try (var sizeDirs = Files.list(thumbnailPath)) {
                sizeDirs
                    .filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().matches("\\d+x\\d+"))
                    .forEach(sizeDir -> {
                        Path thumbFile = sizeDir.resolve(storedPath);
                        try {
                            if (Files.deleteIfExists(thumbFile)) {
                                log.debug("Deleted thumbnail: {}", thumbFile);
                            }
                        } catch (IOException e) {
                            log.warn("Failed to delete thumbnail: {}", thumbFile, e);
                        }
                    });
            }
        } catch (IOException e) {
            log.warn("Failed to scan thumbnail directory for deletion: {}", storedPath, e);
        }
    }

    @Override
    public boolean exists(String storedPath) {
        Path filePath = basePath.resolve(storedPath).normalize();
        return filePath.startsWith(basePath) && Files.exists(filePath);
    }

    @Override
    public String getPublicUrl(String storedPath) {
        String prefix = storageProperties.getLocal().getPublicUrlPrefix();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        return prefix + storedPath;
    }

    @Override
    public Resource getThumbnail(String storedPath, int width, int height) {
        // Check cache first
        String thumbnailKey = buildThumbnailKey(storedPath, width, height);
        Path cachedPath = thumbnailPath.resolve(thumbnailKey);

        if (fileProperties.getThumbnail().isCacheEnabled() && Files.exists(cachedPath)) {
            try {
                return new UrlResource(cachedPath.toUri());
            } catch (MalformedURLException e) {
                log.warn("Failed to load cached thumbnail: {}", thumbnailKey, e);
            }
        }

        // Generate thumbnail
        try {
            Resource original = retrieve(storedPath);
            ProcessedImage thumbnail = imageProcessingService.generateThumbnail(
                original.getInputStream(), width, height
            );

            // Cache if enabled
            if (fileProperties.getThumbnail().isCacheEnabled()) {
                Files.createDirectories(cachedPath.getParent());
                Files.write(cachedPath, thumbnail.data());
            }

            // Return as in-memory resource
            return new org.springframework.core.io.ByteArrayResource(thumbnail.data());
        } catch (IOException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.READ_FAILED,
                "Failed to generate thumbnail for: " + storedPath,
                e
            );
        }
    }

    @Override
    public String getThumbnailUrl(String storedPath, int width, int height) {
        String prefix = storageProperties.getLocal().getPublicUrlPrefix();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        return prefix + "thumbnails/" + width + "x" + height + "/" + storedPath;
    }

    // ==================== Private Methods ====================

    private String buildDatePath() {
        LocalDate now = LocalDate.now();
        return String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }

    private String buildThumbnailKey(String storedPath, int width, int height) {
        return width + "x" + height + "/" + storedPath;
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.CHECKSUM_CALCULATION_FAILED,
                "Failed to calculate checksum",
                e
            );
        }
    }
}
