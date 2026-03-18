package dev.simplecore.simplix.file.infrastructure.upload;

import dev.simplecore.simplix.file.enums.FileCategory;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessedFileResult Record")
class ProcessedFileResultTest {

    private StoredFileInfo createStoredInfo() {
        return new StoredFileInfo(
            "photo.jpg", "uuid-123.jpg", "images/2024/01/uuid-123.jpg",
            "image/jpeg", 2048L, "jpg", "checksum123"
        );
    }

    @Test
    @DisplayName("Should create instance with all fields")
    void shouldCreateInstanceWithAllFields() {
        StoredFileInfo storedInfo = createStoredInfo();
        ProcessedFileResult result = new ProcessedFileResult(
            storedInfo, FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.storedInfo()).isEqualTo(storedInfo);
        assertThat(result.category()).isEqualTo(FileCategory.IMAGE);
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(800);
        assertThat(result.height()).isEqualTo(600);
        assertThat(result.wasOptimized()).isFalse();
        assertThat(result.originalMimeType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Should correctly identify image category")
    void shouldCorrectlyIdentifyImageCategory() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.isImage()).isTrue();
        assertThat(result.isVideo()).isFalse();
        assertThat(result.isAudio()).isFalse();
        assertThat(result.isDocument()).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify video category")
    void shouldCorrectlyIdentifyVideoCategory() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.VIDEO, "video/mp4", null, null, false, "video/mp4"
        );

        assertThat(result.isVideo()).isTrue();
        assertThat(result.isImage()).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify audio category")
    void shouldCorrectlyIdentifyAudioCategory() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.AUDIO, "audio/mpeg", null, null, false, "audio/mpeg"
        );

        assertThat(result.isAudio()).isTrue();
        assertThat(result.isImage()).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify document category")
    void shouldCorrectlyIdentifyDocumentCategory() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.DOCUMENT, "application/pdf", null, null, false, "application/pdf"
        );

        assertThat(result.isDocument()).isTrue();
        assertThat(result.isImage()).isFalse();
    }

    @Test
    @DisplayName("Should return file size from stored info")
    void shouldReturnFileSizeFromStoredInfo() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.getFileSize()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("Should return stored path from stored info")
    void shouldReturnStoredPathFromStoredInfo() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.getStoredPath()).isEqualTo("images/2024/01/uuid-123.jpg");
    }

    @Test
    @DisplayName("Should return original name from stored info")
    void shouldReturnOriginalNameFromStoredInfo() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.getOriginalName()).isEqualTo("photo.jpg");
    }

    @Test
    @DisplayName("Should return extension from stored info")
    void shouldReturnExtensionFromStoredInfo() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.getExtension()).isEqualTo("jpg");
    }

    @Test
    @DisplayName("Should return checksum from stored info")
    void shouldReturnChecksumFromStoredInfo() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.getChecksum()).isEqualTo("checksum123");
    }

    @Test
    @DisplayName("Should detect MIME type change when optimized to WebP")
    void shouldDetectMimeTypeChangeWhenOptimizedToWebp() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/webp", 800, 600, true, "image/jpeg"
        );

        assertThat(result.wasMimeTypeChanged()).isTrue();
    }

    @Test
    @DisplayName("Should not report MIME type change when same type")
    void shouldNotReportMimeTypeChangeWhenSameType() {
        ProcessedFileResult result = new ProcessedFileResult(
            createStoredInfo(), FileCategory.IMAGE, "image/jpeg", 800, 600, false, "image/jpeg"
        );

        assertThat(result.wasMimeTypeChanged()).isFalse();
    }
}
