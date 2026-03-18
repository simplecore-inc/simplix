package dev.simplecore.simplix.file.infrastructure.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileProcessingRequest Record")
class FileProcessingRequestTest {

    @Mock
    private MultipartFile mockFile;

    @Test
    @DisplayName("Should create simple request with of() factory method")
    void shouldCreateSimpleRequestWithOfFactory() {
        FileProcessingRequest request = FileProcessingRequest.of(mockFile, "uploads");

        assertThat(request.file()).isSameAs(mockFile);
        assertThat(request.directory()).isEqualTo("uploads");
        assertThat(request.maxFileSize()).isNull();
        assertThat(request.allowedMimeTypes()).isNull();
        assertThat(request.maxWidth()).isNull();
        assertThat(request.maxHeight()).isNull();
        assertThat(request.enableWebpOptimization()).isNull();
    }

    @Test
    @DisplayName("Should create image request with forImage() with dimensions")
    void shouldCreateImageRequestWithForImageWithDimensions() {
        FileProcessingRequest request = FileProcessingRequest.forImage(mockFile, 1024, 768);

        assertThat(request.file()).isSameAs(mockFile);
        assertThat(request.directory()).isEqualTo("images");
        assertThat(request.maxWidth()).isEqualTo(1024);
        assertThat(request.maxHeight()).isEqualTo(768);
    }

    @Test
    @DisplayName("Should create image request with forImage() without dimensions")
    void shouldCreateImageRequestWithForImageWithoutDimensions() {
        FileProcessingRequest request = FileProcessingRequest.forImage(mockFile);

        assertThat(request.file()).isSameAs(mockFile);
        assertThat(request.directory()).isEqualTo("images");
        assertThat(request.maxWidth()).isNull();
        assertThat(request.maxHeight()).isNull();
    }

    @Test
    @DisplayName("Should build request with all options via builder")
    void shouldBuildRequestWithAllOptionsViaBuilder() {
        Set<String> allowedTypes = Set.of("image/jpeg", "image/png");
        DataSize maxSize = DataSize.ofMegabytes(5);

        FileProcessingRequest request = FileProcessingRequest.builder(mockFile)
            .directory("custom-dir")
            .maxFileSize(maxSize)
            .allowedMimeTypes(allowedTypes)
            .maxWidth(2048)
            .maxHeight(2048)
            .enableWebpOptimization(true)
            .build();

        assertThat(request.file()).isSameAs(mockFile);
        assertThat(request.directory()).isEqualTo("custom-dir");
        assertThat(request.maxFileSize()).isEqualTo(maxSize);
        assertThat(request.allowedMimeTypes()).isEqualTo(allowedTypes);
        assertThat(request.maxWidth()).isEqualTo(2048);
        assertThat(request.maxHeight()).isEqualTo(2048);
        assertThat(request.enableWebpOptimization()).isTrue();
    }

    @Test
    @DisplayName("Should use default directory in builder when not specified")
    void shouldUseDefaultDirectoryInBuilderWhenNotSpecified() {
        FileProcessingRequest request = FileProcessingRequest.builder(mockFile).build();

        assertThat(request.directory()).isEqualTo("files");
    }

    @Test
    @DisplayName("Should create full record with all parameters")
    void shouldCreateFullRecordWithAllParameters() {
        Set<String> types = Set.of("image/jpeg");
        DataSize size = DataSize.ofMegabytes(1);

        FileProcessingRequest request = new FileProcessingRequest(
            mockFile, "dir", size, types, 800, 600, false
        );

        assertThat(request.file()).isSameAs(mockFile);
        assertThat(request.directory()).isEqualTo("dir");
        assertThat(request.maxFileSize()).isEqualTo(size);
        assertThat(request.allowedMimeTypes()).isEqualTo(types);
        assertThat(request.maxWidth()).isEqualTo(800);
        assertThat(request.maxHeight()).isEqualTo(600);
        assertThat(request.enableWebpOptimization()).isFalse();
    }
}
