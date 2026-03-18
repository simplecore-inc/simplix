package dev.simplecore.simplix.file.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StoredFileInfo Record")
class StoredFileInfoTest {

    @Test
    @DisplayName("Should create instance with all fields")
    void shouldCreateInstanceWithAllFields() {
        StoredFileInfo info = new StoredFileInfo(
            "photo.jpg", "abc-123.jpg", "images/2024/01/01/abc-123.jpg",
            "image/jpeg", 1024L, "jpg", "sha256checksum"
        );

        assertThat(info.originalName()).isEqualTo("photo.jpg");
        assertThat(info.storedName()).isEqualTo("abc-123.jpg");
        assertThat(info.storedPath()).isEqualTo("images/2024/01/01/abc-123.jpg");
        assertThat(info.mimeType()).isEqualTo("image/jpeg");
        assertThat(info.fileSize()).isEqualTo(1024L);
        assertThat(info.extension()).isEqualTo("jpg");
        assertThat(info.checksum()).isEqualTo("sha256checksum");
    }

    @ParameterizedTest
    @DisplayName("Should extract extension correctly from various filenames")
    @CsvSource({
        "photo.jpg, jpg",
        "document.PDF, pdf",
        "archive.tar.gz, gz",
        "file.JPEG, jpeg",
        "report.docx, docx"
    })
    void shouldExtractExtensionCorrectly(String filename, String expectedExtension) {
        assertThat(StoredFileInfo.extractExtension(filename)).isEqualTo(expectedExtension);
    }

    @ParameterizedTest
    @DisplayName("Should return empty string for filenames without extension")
    @NullAndEmptySource
    void shouldReturnEmptyStringForNullOrEmptyFilename(String filename) {
        assertThat(StoredFileInfo.extractExtension(filename)).isEmpty();
    }

    @Test
    @DisplayName("Should return empty string for filename without dot")
    void shouldReturnEmptyStringForFilenameWithoutDot() {
        assertThat(StoredFileInfo.extractExtension("noextension")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty string for filename ending with dot")
    void shouldReturnEmptyStringForFilenameEndingWithDot() {
        assertThat(StoredFileInfo.extractExtension("file.")).isEmpty();
    }

    @Test
    @DisplayName("Should support equality based on all fields")
    void shouldSupportEqualityBasedOnAllFields() {
        StoredFileInfo info1 = new StoredFileInfo(
            "a.jpg", "b.jpg", "path", "image/jpeg", 100L, "jpg", "chk"
        );
        StoredFileInfo info2 = new StoredFileInfo(
            "a.jpg", "b.jpg", "path", "image/jpeg", 100L, "jpg", "chk"
        );

        assertThat(info1).isEqualTo(info2);
    }
}
