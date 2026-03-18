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
import dev.simplecore.simplix.file.infrastructure.upload.ProcessedFileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultFileProcessingService")
class DefaultFileProcessingServiceTest {

    @Mock
    private FileStorageService storageService;

    @Mock
    private ImageProcessingService imageProcessingService;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private FileProperties fileProperties;

    @Mock
    private ImageProperties imageProperties;

    @Mock
    private ImageProperties.OptimizationConfig optimizationConfig;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private DefaultFileProcessingService service;

    private StoredFileInfo createStoredFileInfo(String name, String extension, String mimeType) {
        return new StoredFileInfo(
            name, "uuid-" + name, "dir/uuid-" + name,
            mimeType, 1024L, extension, "checksum123"
        );
    }

    @Nested
    @DisplayName("processAndStore()")
    class ProcessAndStore {

        @Test
        @DisplayName("Should process generic file when not an image")
        void shouldProcessGenericFileWhenNotAnImage() throws IOException {
            when(mockFile.getContentType()).thenReturn("application/pdf");
            when(mockFile.getOriginalFilename()).thenReturn("doc.pdf");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

            when(fileValidator.determineFileCategory("application/pdf", "pdf"))
                .thenReturn(FileCategory.DOCUMENT);

            StoredFileInfo storedInfo = createStoredFileInfo("doc.pdf", "pdf", "application/pdf");
            when(storageService.store(any(InputStream.class), eq("doc.pdf"), eq("application/pdf"), eq("files")))
                .thenReturn(storedInfo);

            FileProcessingRequest request = FileProcessingRequest.of(mockFile, "files");
            ProcessedFileResult result = service.processAndStore(request);

            assertThat(result).isNotNull();
            assertThat(result.category()).isEqualTo(FileCategory.DOCUMENT);
            assertThat(result.mimeType()).isEqualTo("application/pdf");
            assertThat(result.width()).isNull();
            assertThat(result.height()).isNull();
            assertThat(result.wasOptimized()).isFalse();

            verify(fileValidator).validate(eq(mockFile), isNull(), isNull());
        }

        @Test
        @DisplayName("Should process image file when supported image type")
        void shouldProcessImageFileWhenSupportedImageType() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("img".getBytes()));
            lenient().when(mockFile.getSize()).thenReturn(1024L);

            when(fileValidator.determineFileCategory("image/jpeg", "jpg"))
                .thenReturn(FileCategory.IMAGE);
            when(imageProcessingService.isSupported("image/jpeg")).thenReturn(true);
            when(imageProperties.getDefaultMaxWidth()).thenReturn(2048);
            when(imageProperties.getDefaultMaxHeight()).thenReturn(2048);
            when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(false);

            ImageMetadata metadata = new ImageMetadata(800, 600, "JPEG");
            when(imageProcessingService.extractMetadata(any(InputStream.class))).thenReturn(metadata);

            StoredFileInfo storedInfo = createStoredFileInfo("photo.jpg", "jpg", "image/jpeg");
            when(storageService.store(any(InputStream.class), eq("photo.jpg"), eq("image/jpeg"), eq("images")))
                .thenReturn(storedInfo);

            FileProcessingRequest request = FileProcessingRequest.of(mockFile, "files");
            ProcessedFileResult result = service.processAndStore(request);

            assertThat(result.category()).isEqualTo(FileCategory.IMAGE);
            assertThat(result.width()).isEqualTo(800);
            assertThat(result.height()).isEqualTo(600);
            assertThat(result.wasOptimized()).isFalse();
        }

        @Test
        @DisplayName("Should resize image when it exceeds max dimensions")
        void shouldResizeImageWhenItExceedsMaxDimensions() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("large.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("img".getBytes()));
            lenient().when(mockFile.getSize()).thenReturn(1024L);

            when(fileValidator.determineFileCategory("image/jpeg", "jpg"))
                .thenReturn(FileCategory.IMAGE);
            when(imageProcessingService.isSupported("image/jpeg")).thenReturn(true);
            when(imageProperties.getDefaultMaxWidth()).thenReturn(1024);
            when(imageProperties.getDefaultMaxHeight()).thenReturn(1024);
            when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(false);

            ImageMetadata metadata = new ImageMetadata(2000, 1500, "JPEG");
            when(imageProcessingService.extractMetadata(any(InputStream.class))).thenReturn(metadata);

            byte[] resizedData = "resized".getBytes();
            ProcessedImage resized = new ProcessedImage(resizedData, "image/jpeg", 1024, 768);
            when(imageProcessingService.resizeIfExceeds(any(InputStream.class), eq(1024), eq(1024)))
                .thenReturn(resized);

            StoredFileInfo storedInfo = createStoredFileInfo("large.jpg", "jpg", "image/jpeg");
            when(storageService.store(any(InputStream.class), eq("large.jpg"), eq("image/jpeg"), eq("images")))
                .thenReturn(storedInfo);

            FileProcessingRequest request = FileProcessingRequest.of(mockFile, "files");
            ProcessedFileResult result = service.processAndStore(request);

            assertThat(result.width()).isEqualTo(1024);
            assertThat(result.height()).isEqualTo(768);
        }

        @Test
        @DisplayName("Should apply WebP optimization when eligible")
        void shouldApplyWebpOptimizationWhenEligible() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("img".getBytes()));
            when(mockFile.getSize()).thenReturn(50000L);

            when(fileValidator.determineFileCategory("image/jpeg", "jpg"))
                .thenReturn(FileCategory.IMAGE);
            when(imageProcessingService.isSupported("image/jpeg")).thenReturn(true);
            when(imageProperties.getDefaultMaxWidth()).thenReturn(2048);
            when(imageProperties.getDefaultMaxHeight()).thenReturn(2048);
            when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(true);
            when(imageProcessingService.isWebpSupported()).thenReturn(true);
            when(optimizationConfig.canConvertToWebp("image/jpeg")).thenReturn(true);
            when(optimizationConfig.getMinSizeForOptimization()).thenReturn(10240L);
            when(optimizationConfig.getWebpQuality()).thenReturn(80);

            ImageMetadata metadata = new ImageMetadata(800, 600, "JPEG");
            when(imageProcessingService.extractMetadata(any(InputStream.class))).thenReturn(metadata);

            byte[] webpData = "webp".getBytes();
            ProcessedImage webpImage = new ProcessedImage(webpData, "image/webp", 800, 600);
            when(imageProcessingService.optimizeAndConvertToWebp(any(InputStream.class), eq(2048), eq(2048), eq(80)))
                .thenReturn(webpImage);

            StoredFileInfo storedInfo = createStoredFileInfo("photo.webp", "webp", "image/webp");
            when(storageService.store(any(InputStream.class), eq("photo.webp"), eq("image/webp"), eq("images")))
                .thenReturn(storedInfo);

            FileProcessingRequest request = FileProcessingRequest.of(mockFile, "files");
            ProcessedFileResult result = service.processAndStore(request);

            assertThat(result.mimeType()).isEqualTo("image/webp");
            assertThat(result.wasOptimized()).isTrue();
            assertThat(result.originalMimeType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should not optimize when request explicitly disables it")
        void shouldNotOptimizeWhenRequestExplicitlyDisablesIt() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("img".getBytes()));
            when(mockFile.getSize()).thenReturn(50000L);

            when(fileValidator.determineFileCategory("image/jpeg", "jpg"))
                .thenReturn(FileCategory.IMAGE);
            when(imageProcessingService.isSupported("image/jpeg")).thenReturn(true);
            when(imageProperties.getDefaultMaxWidth()).thenReturn(2048);
            when(imageProperties.getDefaultMaxHeight()).thenReturn(2048);
            when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(true);
            when(imageProcessingService.isWebpSupported()).thenReturn(true);
            when(optimizationConfig.canConvertToWebp("image/jpeg")).thenReturn(true);
            when(optimizationConfig.getMinSizeForOptimization()).thenReturn(10240L);

            ImageMetadata metadata = new ImageMetadata(800, 600, "JPEG");
            when(imageProcessingService.extractMetadata(any(InputStream.class))).thenReturn(metadata);

            StoredFileInfo storedInfo = createStoredFileInfo("photo.jpg", "jpg", "image/jpeg");
            when(storageService.store(any(InputStream.class), eq("photo.jpg"), eq("image/jpeg"), eq("images")))
                .thenReturn(storedInfo);

            FileProcessingRequest request = FileProcessingRequest.builder(mockFile)
                .enableWebpOptimization(false)
                .build();
            ProcessedFileResult result = service.processAndStore(request);

            assertThat(result.wasOptimized()).isFalse();
            verify(imageProcessingService, never()).optimizeAndConvertToWebp(
                any(InputStream.class), anyInt(), anyInt(), anyInt()
            );
        }

        @Test
        @DisplayName("Should throw FileUploadException when IOException occurs during image processing")
        void shouldThrowFileUploadExceptionWhenIOExceptionOccursDuringImageProcessing() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenThrow(new IOException("Read error"));

            when(fileValidator.determineFileCategory("image/jpeg", "jpg"))
                .thenReturn(FileCategory.IMAGE);
            when(imageProcessingService.isSupported("image/jpeg")).thenReturn(true);
            when(imageProperties.getDefaultMaxWidth()).thenReturn(2048);
            when(imageProperties.getDefaultMaxHeight()).thenReturn(2048);

            FileProcessingRequest request = FileProcessingRequest.of(mockFile, "files");

            assertThatThrownBy(() -> service.processAndStore(request))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.IMAGE_PROCESSING_FAILED));
        }
    }

    @Nested
    @DisplayName("processAndStoreImage()")
    class ProcessAndStoreImage {

        @Test
        @DisplayName("Should process image with custom dimensions")
        void shouldProcessImageWithCustomDimensions() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/png");
            when(mockFile.getOriginalFilename()).thenReturn("icon.png");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("img".getBytes()));
            lenient().when(mockFile.getSize()).thenReturn(512L);

            when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(false);

            ImageMetadata metadata = new ImageMetadata(256, 256, "PNG");
            when(imageProcessingService.extractMetadata(any(InputStream.class))).thenReturn(metadata);

            StoredFileInfo storedInfo = createStoredFileInfo("icon.png", "png", "image/png");
            when(storageService.store(any(InputStream.class), eq("icon.png"), eq("image/png"), eq("images")))
                .thenReturn(storedInfo);

            ProcessedFileResult result = service.processAndStoreImage(mockFile, 512, 512);

            assertThat(result.category()).isEqualTo(FileCategory.IMAGE);
            assertThat(result.width()).isEqualTo(256);
            assertThat(result.height()).isEqualTo(256);
        }

        @Test
        @DisplayName("Should use default dimensions when null")
        void shouldUseDefaultDimensionsWhenNull() throws IOException {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("img".getBytes()));
            lenient().when(mockFile.getSize()).thenReturn(1024L);

            when(imageProperties.getDefaultMaxWidth()).thenReturn(2048);
            when(imageProperties.getDefaultMaxHeight()).thenReturn(2048);
            when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(false);

            ImageMetadata metadata = new ImageMetadata(800, 600, "JPEG");
            when(imageProcessingService.extractMetadata(any(InputStream.class))).thenReturn(metadata);

            StoredFileInfo storedInfo = createStoredFileInfo("photo.jpg", "jpg", "image/jpeg");
            when(storageService.store(any(InputStream.class), eq("photo.jpg"), eq("image/jpeg"), eq("images")))
                .thenReturn(storedInfo);

            ProcessedFileResult result = service.processAndStoreImage(mockFile, null, null);

            assertThat(result.width()).isEqualTo(800);
            assertThat(result.height()).isEqualTo(600);
        }
    }

    @Nested
    @DisplayName("processAndStoreGenericFile()")
    class ProcessAndStoreGenericFile {

        @Test
        @DisplayName("Should store generic file without image processing")
        void shouldStoreGenericFileWithoutImageProcessing() throws IOException {
            when(mockFile.getContentType()).thenReturn("application/pdf");
            when(mockFile.getOriginalFilename()).thenReturn("report.pdf");
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

            when(fileValidator.determineFileCategory("application/pdf", "pdf"))
                .thenReturn(FileCategory.DOCUMENT);

            StoredFileInfo storedInfo = createStoredFileInfo("report.pdf", "pdf", "application/pdf");
            when(storageService.store(any(InputStream.class), eq("report.pdf"), eq("application/pdf"), eq("docs")))
                .thenReturn(storedInfo);

            ProcessedFileResult result = service.processAndStoreGenericFile(mockFile, "docs");

            assertThat(result.category()).isEqualTo(FileCategory.DOCUMENT);
            assertThat(result.width()).isNull();
            assertThat(result.height()).isNull();
            assertThat(result.wasOptimized()).isFalse();

            verifyNoInteractions(imageProcessingService);
        }

        @Test
        @DisplayName("Should throw FileUploadException when storage fails")
        void shouldThrowFileUploadExceptionWhenStorageFails() throws IOException {
            when(mockFile.getContentType()).thenReturn("text/plain");
            when(mockFile.getOriginalFilename()).thenReturn("file.txt");
            when(mockFile.getInputStream()).thenThrow(new IOException("Disk full"));

            when(fileValidator.determineFileCategory("text/plain", "txt"))
                .thenReturn(FileCategory.DOCUMENT);

            assertThatThrownBy(() -> service.processAndStoreGenericFile(mockFile, "docs"))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.STORAGE_WRITE_FAILED));
        }
    }

    @Nested
    @DisplayName("determineCategory()")
    class DetermineCategory {

        @Test
        @DisplayName("Should delegate to FileValidator")
        void shouldDelegateToFileValidator() {
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
            when(fileValidator.determineFileCategory("image/jpeg", "jpg"))
                .thenReturn(FileCategory.IMAGE);

            FileCategory result = service.determineCategory(mockFile);

            assertThat(result).isEqualTo(FileCategory.IMAGE);
            verify(fileValidator).determineFileCategory("image/jpeg", "jpg");
        }
    }

    @Nested
    @DisplayName("shouldOptimizeToWebp()")
    class ShouldOptimizeToWebp {

        @BeforeEach
        void setUp() {
            lenient().when(imageProperties.getOptimization()).thenReturn(optimizationConfig);
        }

        @Test
        @DisplayName("Should return true when all conditions are met")
        void shouldReturnTrueWhenAllConditionsAreMet() {
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(true);
            when(imageProcessingService.isWebpSupported()).thenReturn(true);
            when(optimizationConfig.canConvertToWebp("image/jpeg")).thenReturn(true);
            when(optimizationConfig.getMinSizeForOptimization()).thenReturn(10240L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getSize()).thenReturn(50000L);

            assertThat(service.shouldOptimizeToWebp(mockFile)).isTrue();
        }

        @Test
        @DisplayName("Should return false when WebP conversion is disabled")
        void shouldReturnFalseWhenWebpConversionIsDisabled() {
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(false);

            assertThat(service.shouldOptimizeToWebp(mockFile)).isFalse();
        }

        @Test
        @DisplayName("Should return false when WebP is not supported")
        void shouldReturnFalseWhenWebpIsNotSupported() {
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(true);
            when(imageProcessingService.isWebpSupported()).thenReturn(false);

            assertThat(service.shouldOptimizeToWebp(mockFile)).isFalse();
        }

        @Test
        @DisplayName("Should return false when MIME type is not convertible")
        void shouldReturnFalseWhenMimeTypeIsNotConvertible() {
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(true);
            when(imageProcessingService.isWebpSupported()).thenReturn(true);
            when(mockFile.getContentType()).thenReturn("image/gif");
            when(optimizationConfig.canConvertToWebp("image/gif")).thenReturn(false);

            assertThat(service.shouldOptimizeToWebp(mockFile)).isFalse();
        }

        @Test
        @DisplayName("Should return false when file is below minimum size threshold")
        void shouldReturnFalseWhenFileBelowMinimumSizeThreshold() {
            when(optimizationConfig.isEnableWebpConversion()).thenReturn(true);
            when(imageProcessingService.isWebpSupported()).thenReturn(true);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(optimizationConfig.canConvertToWebp("image/jpeg")).thenReturn(true);
            when(optimizationConfig.getMinSizeForOptimization()).thenReturn(10240L);
            when(mockFile.getSize()).thenReturn(5000L);

            assertThat(service.shouldOptimizeToWebp(mockFile)).isFalse();
        }
    }

    @Nested
    @DisplayName("changeExtensionToWebp()")
    class ChangeExtensionToWebp {

        @ParameterizedTest
        @DisplayName("Should change file extension to .webp")
        @CsvSource({
            "photo.jpg, photo.webp",
            "image.png, image.webp",
            "file.jpeg, file.webp",
            "document.bmp, document.webp"
        })
        void shouldChangeFileExtensionToWebp(String input, String expected) {
            assertThat(service.changeExtensionToWebp(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should append .webp when no extension exists")
        void shouldAppendWebpWhenNoExtensionExists() {
            assertThat(service.changeExtensionToWebp("filename")).isEqualTo("filename.webp");
        }

        @ParameterizedTest
        @DisplayName("Should return image.webp for null filename")
        @NullSource
        void shouldReturnImageWebpForNullFilename(String filename) {
            assertThat(service.changeExtensionToWebp(filename)).isEqualTo("image.webp");
        }
    }

    @Nested
    @DisplayName("extractExtension()")
    class ExtractExtension {

        @ParameterizedTest
        @DisplayName("Should extract extension from filename")
        @CsvSource({
            "photo.jpg, jpg",
            "document.PDF, pdf",
            "archive.tar.gz, gz"
        })
        void shouldExtractExtensionFromFilename(String filename, String expected) {
            assertThat(service.extractExtension(filename)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should return empty string for filename without extension")
        void shouldReturnEmptyStringForFilenameWithoutExtension() {
            assertThat(service.extractExtension("noext")).isEmpty();
        }

        @ParameterizedTest
        @DisplayName("Should return empty string for null filename")
        @NullSource
        void shouldReturnEmptyStringForNullFilename(String filename) {
            assertThat(service.extractExtension(filename)).isEmpty();
        }
    }
}
