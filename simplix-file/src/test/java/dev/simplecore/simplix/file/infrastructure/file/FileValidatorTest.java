package dev.simplecore.simplix.file.infrastructure.file;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.ImageProperties;
import dev.simplecore.simplix.file.enums.FileCategory;
import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException;
import dev.simplecore.simplix.file.infrastructure.exception.FileUploadException.FileErrorCode;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
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
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileValidator")
class FileValidatorTest {

    @Mock
    private FileProperties fileProperties;

    @Mock
    private ImageProperties imageProperties;

    @Mock
    private ImageProcessingService imageProcessingService;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private FileValidator fileValidator;

    @Nested
    @DisplayName("validate()")
    class Validate {

        @BeforeEach
        void setUp() {
            lenient().when(fileProperties.getDefaultMaxSize()).thenReturn(DataSize.ofMegabytes(10));
            lenient().when(fileProperties.getAllowedMimeTypes()).thenReturn(Set.of(
                "image/jpeg", "image/png", "application/pdf"
            ));
        }

        @Test
        @DisplayName("Should throw EMPTY_FILE when file is null")
        void shouldThrowEmptyFileWhenFileIsNull() {
            assertThatThrownBy(() -> fileValidator.validate(null, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.EMPTY_FILE));
        }

        @Test
        @DisplayName("Should throw EMPTY_FILE when file is empty")
        void shouldThrowEmptyFileWhenFileIsEmpty() {
            when(mockFile.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> fileValidator.validate(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.EMPTY_FILE));
        }

        @Test
        @DisplayName("Should throw FILE_SIZE_EXCEEDED when file exceeds max size")
        void shouldThrowFileSizeExceededWhenFileExceedsMaxSize() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(11 * 1024 * 1024L); // 11MB

            assertThatThrownBy(() -> fileValidator.validate(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.FILE_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("Should use custom max size when provided")
        void shouldUseCustomMaxSizeWhenProvided() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(3 * 1024 * 1024L); // 3MB

            assertThatThrownBy(() -> fileValidator.validate(mockFile, DataSize.ofMegabytes(2), null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.FILE_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("Should throw INVALID_FILE_TYPE when MIME type is null")
        void shouldThrowInvalidFileTypeWhenMimeTypeIsNull() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn(null);

            assertThatThrownBy(() -> fileValidator.validate(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.INVALID_FILE_TYPE));
        }

        @Test
        @DisplayName("Should throw INVALID_FILE_TYPE when MIME type is not allowed")
        void shouldThrowInvalidFileTypeWhenMimeTypeIsNotAllowed() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("application/exe");

            assertThatThrownBy(() -> fileValidator.validate(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.INVALID_FILE_TYPE));
        }

        @Test
        @DisplayName("Should use custom allowed MIME types when provided")
        void shouldUseCustomAllowedMimeTypesWhenProvided() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");

            Set<String> customTypes = Set.of("application/pdf");

            assertThatThrownBy(() -> fileValidator.validate(mockFile, null, customTypes))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.INVALID_FILE_TYPE));
        }

        @Test
        @DisplayName("Should pass validation for valid file")
        void shouldPassValidationForValidFile() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");

            // Should not throw
            fileValidator.validate(mockFile, null, null);
        }

        @Test
        @DisplayName("Should pass validation when filename is null")
        void shouldPassValidationWhenFilenameIsNull() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn(null);

            // Should not throw
            fileValidator.validate(mockFile, null, null);
        }

        @Test
        @DisplayName("Should pass validation when filename is empty")
        void shouldPassValidationWhenFilenameIsEmpty() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("");

            // Should not throw
            fileValidator.validate(mockFile, null, null);
        }

        @Test
        @DisplayName("Should pass validation even when extension does not match MIME type")
        void shouldPassValidationEvenWhenExtensionDoesNotMatchMimeType() {
            // Extension mismatch only logs a warning, does not fail
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(mockFile.getOriginalFilename()).thenReturn("photo.png");

            // Should not throw
            fileValidator.validate(mockFile, null, null);
        }
    }

    @Nested
    @DisplayName("validateImage()")
    class ValidateImage {

        @BeforeEach
        void setUp() {
            lenient().when(imageProperties.getMaxFileSize()).thenReturn(DataSize.ofMegabytes(10));
        }

        @Test
        @DisplayName("Should throw EMPTY_FILE when file is null")
        void shouldThrowEmptyFileWhenFileIsNull() {
            assertThatThrownBy(() -> fileValidator.validateImage(null, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.EMPTY_FILE));
        }

        @Test
        @DisplayName("Should throw EMPTY_FILE when file is empty")
        void shouldThrowEmptyFileWhenFileIsEmpty() {
            when(mockFile.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> fileValidator.validateImage(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.EMPTY_FILE));
        }

        @Test
        @DisplayName("Should throw FILE_SIZE_EXCEEDED when image exceeds max size")
        void shouldThrowFileSizeExceededWhenImageExceedsMaxSize() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(11 * 1024 * 1024L);

            assertThatThrownBy(() -> fileValidator.validateImage(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.FILE_SIZE_EXCEEDED));
        }

        @Test
        @DisplayName("Should throw INVALID_FILE_TYPE when MIME type is null")
        void shouldThrowInvalidFileTypeWhenMimeTypeIsNull() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn(null);

            assertThatThrownBy(() -> fileValidator.validateImage(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.INVALID_FILE_TYPE));
        }

        @Test
        @DisplayName("Should throw INVALID_FILE_TYPE when format is not allowed")
        void shouldThrowInvalidFileTypeWhenFormatIsNotAllowed() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/tiff");
            when(imageProperties.isAllowedFormat("image/tiff")).thenReturn(false);

            assertThatThrownBy(() -> fileValidator.validateImage(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.INVALID_FILE_TYPE));
        }

        @Test
        @DisplayName("Should throw INVALID_IMAGE when not a valid image")
        void shouldThrowInvalidImageWhenNotAValidImage() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(imageProperties.isAllowedFormat("image/jpeg")).thenReturn(true);
            when(imageProcessingService.isImage("image/jpeg")).thenReturn(false);

            assertThatThrownBy(() -> fileValidator.validateImage(mockFile, null, null))
                .isInstanceOf(FileUploadException.class)
                .satisfies(ex -> assertThat(((FileUploadException) ex).getErrorCode())
                    .isEqualTo(FileErrorCode.INVALID_IMAGE));
        }

        @Test
        @DisplayName("Should pass validation for valid image")
        void shouldPassValidationForValidImage() {
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(imageProperties.isAllowedFormat("image/jpeg")).thenReturn(true);
            when(imageProcessingService.isImage("image/jpeg")).thenReturn(true);

            // Should not throw
            fileValidator.validateImage(mockFile, null, null);
        }
    }

    @Nested
    @DisplayName("determineFileCategory()")
    class DetermineFileCategory {

        @ParameterizedTest
        @DisplayName("Should categorize image MIME types")
        @ValueSource(strings = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"})
        void shouldCategorizeImageMimeTypes(String mimeType) {
            assertThat(fileValidator.determineFileCategory(mimeType, "jpg")).isEqualTo(FileCategory.IMAGE);
        }

        @ParameterizedTest
        @DisplayName("Should categorize video MIME types")
        @ValueSource(strings = {"video/mp4", "video/webm", "video/quicktime"})
        void shouldCategorizeVideoMimeTypes(String mimeType) {
            assertThat(fileValidator.determineFileCategory(mimeType, "mp4")).isEqualTo(FileCategory.VIDEO);
        }

        @ParameterizedTest
        @DisplayName("Should categorize audio MIME types")
        @ValueSource(strings = {"audio/mpeg", "audio/wav", "audio/ogg"})
        void shouldCategorizeAudioMimeTypes(String mimeType) {
            assertThat(fileValidator.determineFileCategory(mimeType, "mp3")).isEqualTo(FileCategory.AUDIO);
        }

        @ParameterizedTest
        @DisplayName("Should categorize document MIME types")
        @CsvSource({
            "application/pdf, pdf",
            "application/msword, doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document, docx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, xlsx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation, pptx",
            "application/vnd.ms-excel, xls",
            "application/vnd.ms-powerpoint, ppt",
            "text/plain, txt",
            "text/csv, csv",
            "text/rtf, rtf"
        })
        void shouldCategorizeDocumentMimeTypes(String mimeType, String extension) {
            assertThat(fileValidator.determineFileCategory(mimeType, extension)).isEqualTo(FileCategory.DOCUMENT);
        }

        @ParameterizedTest
        @DisplayName("Should categorize archive MIME types")
        @CsvSource({
            "application/zip, zip",
            "application/x-zip-compressed, zip",
            "application/x-rar-compressed, rar",
            "application/x-7z-compressed, 7z",
            "application/gzip, gz",
            "application/x-tar, tar"
        })
        void shouldCategorizeArchiveMimeTypes(String mimeType, String extension) {
            assertThat(fileValidator.determineFileCategory(mimeType, extension)).isEqualTo(FileCategory.ARCHIVE);
        }

        @Test
        @DisplayName("Should return OTHER for unknown MIME type")
        void shouldReturnOtherForUnknownMimeType() {
            assertThat(fileValidator.determineFileCategory("application/octet-stream", "bin"))
                .isEqualTo(FileCategory.OTHER);
        }

        @ParameterizedTest
        @DisplayName("Should return OTHER for null MIME type")
        @NullSource
        void shouldReturnOtherForNullMimeType(String mimeType) {
            assertThat(fileValidator.determineFileCategory(mimeType, "txt")).isEqualTo(FileCategory.OTHER);
        }
    }
}
