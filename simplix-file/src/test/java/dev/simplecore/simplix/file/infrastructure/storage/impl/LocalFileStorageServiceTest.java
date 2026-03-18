package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalFileStorageService")
class LocalFileStorageServiceTest {

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private StorageProperties.LocalStorageConfig localConfig;

    @Mock
    private FileProperties fileProperties;

    @Mock
    private FileProperties.ThumbnailConfig thumbnailConfig;

    @Mock
    private ImageProcessingService imageProcessingService;

    @TempDir
    Path tempDir;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        lenient().when(storageProperties.getLocal()).thenReturn(localConfig);
        lenient().when(localConfig.getBasePath()).thenReturn(tempDir.toString());
        lenient().when(localConfig.getPublicUrlPrefix()).thenReturn("/files");
        lenient().when(fileProperties.getThumbnail()).thenReturn(thumbnailConfig);
        lenient().when(thumbnailConfig.getCachePath()).thenReturn(".thumbnails");
        lenient().when(thumbnailConfig.isCacheEnabled()).thenReturn(true);

        service = new LocalFileStorageService(storageProperties, fileProperties, imageProcessingService);
        service.init();
    }

    @Nested
    @DisplayName("init()")
    class Init {

        @Test
        @DisplayName("Should create base directory on init")
        void shouldCreateBaseDirectoryOnInit() {
            assertThat(Files.exists(tempDir)).isTrue();
        }

        @Test
        @DisplayName("Should create thumbnail directory on init")
        void shouldCreateThumbnailDirectoryOnInit() {
            Path thumbDir = tempDir.resolve(".thumbnails");
            assertThat(Files.exists(thumbDir)).isTrue();
        }

        @Test
        @DisplayName("Should handle absolute thumbnail path on init")
        void shouldHandleAbsoluteThumbnailPathOnInit(@TempDir Path anotherDir) {
            Path absoluteThumbnailPath = anotherDir.resolve("thumbnails");
            when(thumbnailConfig.getCachePath()).thenReturn(absoluteThumbnailPath.toAbsolutePath().toString());

            LocalFileStorageService absoluteService = new LocalFileStorageService(
                storageProperties, fileProperties, imageProcessingService);
            absoluteService.init();

            assertThat(Files.exists(absoluteThumbnailPath)).isTrue();
        }
    }

    @Nested
    @DisplayName("store()")
    class Store {

        @Test
        @DisplayName("Should store file and return StoredFileInfo")
        void shouldStoreFileAndReturnStoredFileInfo() {
            byte[] content = "test file content".getBytes();
            InputStream input = new ByteArrayInputStream(content);

            StoredFileInfo result = service.store(input, "test.txt", "text/plain", "docs");

            assertThat(result).isNotNull();
            assertThat(result.originalName()).isEqualTo("test.txt");
            assertThat(result.storedName()).endsWith(".txt");
            assertThat(result.mimeType()).isEqualTo("text/plain");
            assertThat(result.fileSize()).isEqualTo(content.length);
            assertThat(result.extension()).isEqualTo("txt");
            assertThat(result.checksum()).isNotEmpty();
        }

        @Test
        @DisplayName("Should generate UUID-based stored name")
        void shouldGenerateUuidBasedStoredName() {
            InputStream input = new ByteArrayInputStream("data".getBytes());

            StoredFileInfo result = service.store(input, "photo.jpg", "image/jpeg", "images");

            assertThat(result.storedName()).matches("[0-9a-f\\-]+\\.jpg");
        }

        @Test
        @DisplayName("Should create date-based subdirectory structure")
        void shouldCreateDateBasedSubdirectoryStructure() {
            InputStream input = new ByteArrayInputStream("data".getBytes());

            StoredFileInfo result = service.store(input, "doc.pdf", "application/pdf", "files");

            assertThat(result.storedPath()).matches("files/\\d{4}/\\d{2}/\\d{2}/[0-9a-f\\-]+\\.pdf");
        }

        @Test
        @DisplayName("Should store file without directory prefix")
        void shouldStoreFileWithoutDirectoryPrefix() {
            InputStream input = new ByteArrayInputStream("data".getBytes());

            StoredFileInfo result = service.store(input, "file.txt", "text/plain", null);

            assertThat(result.storedPath()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f\\-]+\\.txt");
        }

        @Test
        @DisplayName("Should handle file without extension")
        void shouldHandleFileWithoutExtension() {
            InputStream input = new ByteArrayInputStream("data".getBytes());

            StoredFileInfo result = service.store(input, "noext", "application/octet-stream", "files");

            assertThat(result.extension()).isEmpty();
            assertThat(result.storedName()).doesNotContain(".");
        }

        @Test
        @DisplayName("Should store using two-param method and guess MIME type")
        void shouldStoreUsingTwoParamMethodAndGuessMimeType() {
            InputStream input = new ByteArrayInputStream("data".getBytes());

            StoredFileInfo result = service.store(input, "image.png", "images");

            assertThat(result.originalName()).isEqualTo("image.png");
            assertThat(result.extension()).isEqualTo("png");
        }

        @Test
        @DisplayName("Should calculate SHA-256 checksum")
        void shouldCalculateSha256Checksum() {
            byte[] content = "known content".getBytes();
            InputStream input = new ByteArrayInputStream(content);

            StoredFileInfo result = service.store(input, "file.txt", "text/plain", "docs");

            assertThat(result.checksum()).isNotEmpty();
            assertThat(result.checksum()).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("Should write file bytes to disk")
        void shouldWriteFileBytesToDisk() throws IOException {
            byte[] content = "file content for verification".getBytes();
            InputStream input = new ByteArrayInputStream(content);

            StoredFileInfo result = service.store(input, "verify.txt", "text/plain", "test");

            Path storedFile = tempDir.resolve(result.storedPath());
            assertThat(Files.exists(storedFile)).isTrue();
            assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);
        }

        @Test
        @DisplayName("Should throw StorageException on IOException when reading input stream")
        void shouldThrowStorageExceptionOnIOException() {
            InputStream input = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Stream read failure");
                }

                @Override
                public byte[] readAllBytes() throws IOException {
                    throw new IOException("Stream read failure");
                }
            };

            assertThatThrownBy(() -> service.store(input, "file.txt", "text/plain", "docs"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> assertThat(((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.WRITE_FAILED));
        }
    }

    @Nested
    @DisplayName("retrieve()")
    class Retrieve {

        @Test
        @DisplayName("Should retrieve stored file as Resource")
        void shouldRetrieveStoredFileAsResource() throws IOException {
            byte[] content = "retrieve test".getBytes();
            InputStream input = new ByteArrayInputStream(content);
            StoredFileInfo info = service.store(input, "test.txt", "text/plain", "docs");

            Resource resource = service.retrieve(info.storedPath());

            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
            assertThat(resource.isReadable()).isTrue();
        }

        @Test
        @DisplayName("Should throw StorageException for non-existent file")
        void shouldThrowStorageExceptionForNonExistentFile() {
            assertThatThrownBy(() -> service.retrieve("nonexistent/path.txt"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> assertThat(((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.PATH_NOT_FOUND));
        }

        @Test
        @DisplayName("Should prevent path traversal attacks")
        void shouldPreventPathTraversalAttacks() {
            assertThatThrownBy(() -> service.retrieve("../../etc/passwd"))
                .isInstanceOf(StorageException.class);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("Should delete existing file and return true")
        void shouldDeleteExistingFileAndReturnTrue() {
            byte[] content = "delete test".getBytes();
            InputStream input = new ByteArrayInputStream(content);
            StoredFileInfo info = service.store(input, "delete-me.txt", "text/plain", "docs");

            boolean result = service.delete(info.storedPath());

            assertThat(result).isTrue();
            assertThat(Files.exists(tempDir.resolve(info.storedPath()))).isFalse();
        }

        @Test
        @DisplayName("Should return false for non-existent file")
        void shouldReturnFalseForNonExistentFile() {
            boolean result = service.delete("nonexistent/file.txt");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should prevent deletion outside base directory")
        void shouldPreventDeletionOutsideBaseDirectory() {
            boolean result = service.delete("../../etc/passwd");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should delete associated thumbnails when deleting a file")
        void shouldDeleteAssociatedThumbnailsWhenDeletingAFile() throws IOException {
            // Store a test file
            byte[] content = "image data".getBytes();
            InputStream input = new ByteArrayInputStream(content);
            StoredFileInfo info = service.store(input, "photo.jpg", "image/jpeg", "images");

            // Create fake thumbnail directories and files
            Path thumbBase = tempDir.resolve(".thumbnails");
            Path thumbDir64 = thumbBase.resolve("64x64").resolve("images");
            Path thumbDir128 = thumbBase.resolve("128x128").resolve("images");
            Files.createDirectories(thumbDir64);
            Files.createDirectories(thumbDir128);

            // Create thumbnail files matching the storedPath structure
            String storedPathFileName = Path.of(info.storedPath()).getFileName().toString();
            String storedPathDir = Path.of(info.storedPath()).getParent().toString();
            Path thumbDir64Full = thumbBase.resolve("64x64").resolve(storedPathDir);
            Path thumbDir128Full = thumbBase.resolve("128x128").resolve(storedPathDir);
            Files.createDirectories(thumbDir64Full);
            Files.createDirectories(thumbDir128Full);
            Files.write(thumbDir64Full.resolve(storedPathFileName), "thumb64".getBytes());
            Files.write(thumbDir128Full.resolve(storedPathFileName), "thumb128".getBytes());

            // Delete the original file
            boolean result = service.delete(info.storedPath());

            assertThat(result).isTrue();
            // Verify thumbnails were deleted
            assertThat(Files.exists(thumbDir64Full.resolve(storedPathFileName))).isFalse();
            assertThat(Files.exists(thumbDir128Full.resolve(storedPathFileName))).isFalse();
        }

        @Test
        @DisplayName("Should skip thumbnail deletion when cache is disabled")
        void shouldSkipThumbnailDeletionWhenCacheIsDisabled() {
            when(thumbnailConfig.isCacheEnabled()).thenReturn(false);

            byte[] content = "image data".getBytes();
            InputStream input = new ByteArrayInputStream(content);
            StoredFileInfo info = service.store(input, "photo.jpg", "image/jpeg", "images");

            boolean result = service.delete(info.storedPath());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should handle non-existent thumbnail directory gracefully")
        void shouldHandleNonExistentThumbnailDirectoryGracefully(@TempDir Path anotherDir) throws IOException {
            // Point thumbnail path to a non-existent location
            Path nonExistentThumbPath = anotherDir.resolve("nonexistent-thumbnails");
            when(thumbnailConfig.getCachePath()).thenReturn(nonExistentThumbPath.toAbsolutePath().toString());

            LocalFileStorageService serviceWithBadThumb = new LocalFileStorageService(
                storageProperties, fileProperties, imageProcessingService);
            serviceWithBadThumb.init();

            // Store a file
            byte[] content = "data".getBytes();
            InputStream input = new ByteArrayInputStream(content);
            StoredFileInfo info = serviceWithBadThumb.store(input, "file.txt", "text/plain", "docs");

            // Delete the thumbnail directory manually after init
            Files.deleteIfExists(nonExistentThumbPath);

            // Delete should succeed even if thumbnail directory does not exist
            boolean result = serviceWithBadThumb.delete(info.storedPath());
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("exists()")
    class Exists {

        @Test
        @DisplayName("Should return true for existing file")
        void shouldReturnTrueForExistingFile() {
            byte[] content = "exists test".getBytes();
            InputStream input = new ByteArrayInputStream(content);
            StoredFileInfo info = service.store(input, "exists.txt", "text/plain", "docs");

            assertThat(service.exists(info.storedPath())).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-existent file")
        void shouldReturnFalseForNonExistentFile() {
            assertThat(service.exists("nonexistent.txt")).isFalse();
        }

        @Test
        @DisplayName("Should return false for path outside base directory")
        void shouldReturnFalseForPathOutsideBaseDirectory() {
            assertThat(service.exists("../../etc/passwd")).isFalse();
        }
    }

    @Nested
    @DisplayName("getPublicUrl()")
    class GetPublicUrl {

        @Test
        @DisplayName("Should build public URL with prefix and stored path")
        void shouldBuildPublicUrlWithPrefixAndStoredPath() {
            String url = service.getPublicUrl("images/2024/01/photo.jpg");

            assertThat(url).isEqualTo("/files/images/2024/01/photo.jpg");
        }

        @Test
        @DisplayName("Should add trailing slash to prefix if missing")
        void shouldAddTrailingSlashToPrefixIfMissing() {
            when(localConfig.getPublicUrlPrefix()).thenReturn("/files");

            String url = service.getPublicUrl("path/file.txt");

            assertThat(url).isEqualTo("/files/path/file.txt");
        }

        @Test
        @DisplayName("Should not add double slash when prefix ends with slash")
        void shouldNotAddDoubleSlashWhenPrefixEndsWithSlash() {
            when(localConfig.getPublicUrlPrefix()).thenReturn("/files/");

            String url = service.getPublicUrl("path/file.txt");

            assertThat(url).isEqualTo("/files/path/file.txt");
        }
    }

    @Nested
    @DisplayName("getThumbnail()")
    class GetThumbnail {

        @Test
        @DisplayName("Should generate and cache thumbnail for stored image")
        void shouldGenerateAndCacheThumbnailForStoredImage() throws IOException {
            // Store a test file
            byte[] imageContent = "fake image content".getBytes();
            InputStream input = new ByteArrayInputStream(imageContent);
            StoredFileInfo info = service.store(input, "image.jpg", "image/jpeg", "images");

            // Mock thumbnail generation
            byte[] thumbnailData = "thumbnail data".getBytes();
            ProcessedImage thumbnail = new ProcessedImage(thumbnailData, "image/jpeg", 64, 64);
            when(imageProcessingService.generateThumbnail(any(InputStream.class), anyInt(), anyInt()))
                .thenReturn(thumbnail);

            Resource result = service.getThumbnail(info.storedPath(), 64, 64);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return cached thumbnail if already generated")
        void shouldReturnCachedThumbnailIfAlreadyGenerated() throws IOException {
            // Store a test file
            byte[] imageContent = "fake image content".getBytes();
            InputStream input = new ByteArrayInputStream(imageContent);
            StoredFileInfo info = service.store(input, "image.jpg", "image/jpeg", "images");

            // Pre-create a cached thumbnail file
            String thumbnailKey = "64x64/" + info.storedPath();
            Path cachedPath = tempDir.resolve(".thumbnails").resolve(thumbnailKey);
            Files.createDirectories(cachedPath.getParent());
            Files.write(cachedPath, "cached thumbnail data".getBytes());

            // Should not call imageProcessingService since cache exists
            Resource result = service.getThumbnail(info.storedPath(), 64, 64);

            assertThat(result).isNotNull();
            assertThat(result.exists()).isTrue();
        }

        @Test
        @DisplayName("Should generate thumbnail without caching when cache is disabled")
        void shouldGenerateThumbnailWithoutCachingWhenCacheIsDisabled() throws IOException {
            when(thumbnailConfig.isCacheEnabled()).thenReturn(false);

            // Store a test file
            byte[] imageContent = "fake image content".getBytes();
            InputStream input = new ByteArrayInputStream(imageContent);
            StoredFileInfo info = service.store(input, "image.jpg", "image/jpeg", "images");

            // Mock thumbnail generation
            byte[] thumbnailData = "thumbnail data".getBytes();
            ProcessedImage thumbnail = new ProcessedImage(thumbnailData, "image/jpeg", 64, 64);
            when(imageProcessingService.generateThumbnail(any(InputStream.class), anyInt(), anyInt()))
                .thenReturn(thumbnail);

            Resource result = service.getThumbnail(info.storedPath(), 64, 64);

            assertThat(result).isNotNull();

            // Verify the thumbnail was NOT cached on disk
            String thumbnailKey = "64x64/" + info.storedPath();
            Path cachedPath = tempDir.resolve(".thumbnails").resolve(thumbnailKey);
            assertThat(Files.exists(cachedPath)).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when image processing fails during thumbnail generation")
        void shouldThrowExceptionWhenImageProcessingFailsDuringThumbnailGeneration() throws IOException {
            // Store a test file
            byte[] imageContent = "fake image content".getBytes();
            InputStream input = new ByteArrayInputStream(imageContent);
            StoredFileInfo info = service.store(input, "image.jpg", "image/jpeg", "images");

            // Mock thumbnail generation to throw ImageProcessingException
            when(imageProcessingService.generateThumbnail(any(InputStream.class), anyInt(), anyInt()))
                .thenThrow(new dev.simplecore.simplix.file.infrastructure.exception.ImageProcessingException(
                    dev.simplecore.simplix.file.infrastructure.exception.ImageProcessingException.ImageErrorCode.THUMBNAIL_GENERATION_FAILED,
                    "Failed to generate thumbnail"
                ));

            assertThatThrownBy(() -> service.getThumbnail(info.storedPath(), 64, 64))
                .isInstanceOf(dev.simplecore.simplix.file.infrastructure.exception.ImageProcessingException.class);
        }
    }

    @Nested
    @DisplayName("getThumbnailUrl()")
    class GetThumbnailUrl {

        @Test
        @DisplayName("Should build thumbnail URL with dimensions")
        void shouldBuildThumbnailUrlWithDimensions() {
            String url = service.getThumbnailUrl("images/photo.jpg", 128, 128);

            assertThat(url).isEqualTo("/files/thumbnails/128x128/images/photo.jpg");
        }

        @Test
        @DisplayName("Should add trailing slash to prefix if missing in thumbnail URL")
        void shouldAddTrailingSlashToPrefixIfMissingInThumbnailUrl() {
            when(localConfig.getPublicUrlPrefix()).thenReturn("/assets");

            String url = service.getThumbnailUrl("images/photo.jpg", 256, 256);

            assertThat(url).isEqualTo("/assets/thumbnails/256x256/images/photo.jpg");
        }

        @Test
        @DisplayName("Should handle prefix ending with slash in thumbnail URL")
        void shouldHandlePrefixEndingWithSlashInThumbnailUrl() {
            when(localConfig.getPublicUrlPrefix()).thenReturn("/assets/");

            String url = service.getThumbnailUrl("images/photo.jpg", 256, 256);

            assertThat(url).isEqualTo("/assets/thumbnails/256x256/images/photo.jpg");
        }
    }
}
