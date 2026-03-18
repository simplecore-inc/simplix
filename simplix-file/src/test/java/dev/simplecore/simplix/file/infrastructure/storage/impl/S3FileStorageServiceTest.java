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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileStorageService")
class S3FileStorageServiceTest {

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private StorageProperties.S3StorageConfig s3Config;

    @Mock
    private FileProperties fileProperties;

    @Mock
    private FileProperties.ThumbnailConfig thumbnailConfig;

    @Mock
    private ImageProcessingService imageProcessingService;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3FileStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(storageProperties.getS3()).thenReturn(s3Config);
        lenient().when(s3Config.getBucket()).thenReturn("test-bucket");
        lenient().when(s3Config.getRegion()).thenReturn("us-east-1");
        lenient().when(s3Config.getPresignedUrlExpiration()).thenReturn(60);
        lenient().when(fileProperties.getThumbnail()).thenReturn(thumbnailConfig);
        lenient().when(thumbnailConfig.getS3Prefix()).thenReturn("thumbnails");

        service = new S3FileStorageService(storageProperties, fileProperties, imageProcessingService);

        // Inject mock S3Client via reflection since init() creates the actual client
        Field s3ClientField = S3FileStorageService.class.getDeclaredField("s3Client");
        s3ClientField.setAccessible(true);
        s3ClientField.set(service, s3Client);

        // Inject mock S3Presigner via reflection
        Field s3PresignerField = S3FileStorageService.class.getDeclaredField("s3Presigner");
        s3PresignerField.setAccessible(true);
        s3PresignerField.set(service, s3Presigner);
    }

    @Nested
    @DisplayName("ensureBucketExists()")
    class EnsureBucketExists {

        @Test
        @DisplayName("Should create bucket when it does not exist")
        void shouldCreateBucketWhenItDoesNotExist() throws Exception {
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("No such bucket").build());
            when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

            // Call ensureBucketExists via reflection
            java.lang.reflect.Method method = S3FileStorageService.class.getDeclaredMethod("ensureBucketExists");
            method.setAccessible(true);
            method.invoke(service);

            verify(s3Client).createBucket(any(CreateBucketRequest.class));
        }

        @Test
        @DisplayName("Should not create bucket when it already exists")
        void shouldNotCreateBucketWhenItAlreadyExists() throws Exception {
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

            java.lang.reflect.Method method = S3FileStorageService.class.getDeclaredMethod("ensureBucketExists");
            method.setAccessible(true);
            method.invoke(service);

            verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
        }

        @Test
        @DisplayName("Should handle S3 error when checking bucket existence")
        void shouldHandleS3ErrorWhenCheckingBucketExistence() throws Exception {
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                    .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorMessage("Access Denied")
                        .errorCode("AccessDenied")
                        .sdkHttpResponse(software.amazon.awssdk.http.SdkHttpResponse.builder()
                            .statusCode(403)
                            .build())
                        .build())
                    .message("Access Denied")
                    .statusCode(403)
                    .build());

            java.lang.reflect.Method method = S3FileStorageService.class.getDeclaredMethod("ensureBucketExists");
            method.setAccessible(true);
            // Should not throw, just log warning
            method.invoke(service);

            verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
        }
    }

    @Nested
    @DisplayName("store()")
    class Store {

        @Test
        @DisplayName("Should store file to S3 and return StoredFileInfo")
        void shouldStoreFileToS3AndReturnStoredFileInfo() {
            byte[] content = "test content".getBytes();
            InputStream input = new ByteArrayInputStream(content);

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

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
        @DisplayName("Should create correct S3 PutObjectRequest")
        void shouldCreateCorrectS3PutObjectRequest() {
            byte[] content = "test".getBytes();
            InputStream input = new ByteArrayInputStream(content);

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            service.store(input, "photo.jpg", "image/jpeg", "images");

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

            PutObjectRequest request = captor.getValue();
            assertThat(request.bucket()).isEqualTo("test-bucket");
            assertThat(request.contentType()).isEqualTo("image/jpeg");
            assertThat(request.contentLength()).isEqualTo(content.length);
        }

        @Test
        @DisplayName("Should build date-based storage path")
        void shouldBuildDateBasedStoragePath() {
            InputStream input = new ByteArrayInputStream("data".getBytes());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            StoredFileInfo result = service.store(input, "doc.pdf", "application/pdf", "files");

            assertThat(result.storedPath()).matches("files/\\d{4}/\\d{2}/\\d{2}/[0-9a-f\\-]+\\.pdf");
        }

        @Test
        @DisplayName("Should handle null directory")
        void shouldHandleNullDirectory() {
            InputStream input = new ByteArrayInputStream("data".getBytes());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            StoredFileInfo result = service.store(input, "file.txt", "text/plain", null);

            assertThat(result.storedPath()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f\\-]+\\.txt");
        }

        @Test
        @DisplayName("Should throw StorageException on S3 error")
        void shouldThrowStorageExceptionOnS3Error() {
            InputStream input = new ByteArrayInputStream("data".getBytes());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder()
                    .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorMessage("Access Denied")
                        .errorCode("AccessDenied")
                        .sdkHttpResponse(software.amazon.awssdk.http.SdkHttpResponse.builder()
                            .statusCode(403)
                            .build())
                        .build())
                    .message("Access Denied")
                    .statusCode(403)
                    .build());

            assertThatThrownBy(() -> service.store(input, "file.txt", "text/plain", "docs"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> assertThat(((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.WRITE_FAILED));
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

        @Test
        @DisplayName("Should use two-param store and guess MIME type")
        void shouldUseTwoParamStoreAndGuessMimeType() {
            InputStream input = new ByteArrayInputStream("data".getBytes());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            StoredFileInfo result = service.store(input, "image.png", "images");

            assertThat(result.originalName()).isEqualTo("image.png");
            assertThat(result.extension()).isEqualTo("png");
        }

        @Test
        @DisplayName("Should handle file without extension")
        void shouldHandleFileWithoutExtension() {
            InputStream input = new ByteArrayInputStream("data".getBytes());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            StoredFileInfo result = service.store(input, "noext", "application/octet-stream", "files");

            assertThat(result.extension()).isEmpty();
        }
    }

    @Nested
    @DisplayName("retrieve()")
    class Retrieve {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should retrieve file from S3 as Resource")
        void shouldRetrieveFileFromS3AsResource() throws IOException {
            byte[] content = "file content".getBytes();

            ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
            when(responseBytes.asByteArray()).thenReturn(content);
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

            Resource resource = service.retrieve("path/to/file.txt");

            assertThat(resource).isNotNull();
            assertThat(resource.contentLength()).isEqualTo(content.length);
        }

        @Test
        @DisplayName("Should throw StorageException when file not found")
        void shouldThrowStorageExceptionWhenFileNotFound() {
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            assertThatThrownBy(() -> service.retrieve("nonexistent.txt"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> assertThat(((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.PATH_NOT_FOUND));
        }

        @Test
        @DisplayName("Should throw StorageException on S3 read error")
        void shouldThrowStorageExceptionOnS3ReadError() {
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                    .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorMessage("Internal Error")
                        .errorCode("InternalError")
                        .sdkHttpResponse(software.amazon.awssdk.http.SdkHttpResponse.builder()
                            .statusCode(500)
                            .build())
                        .build())
                    .message("Internal Error")
                    .statusCode(500)
                    .build());

            assertThatThrownBy(() -> service.retrieve("path/file.txt"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> assertThat(((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.READ_FAILED));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("Should delete file from S3 and return true")
        void shouldDeleteFileFromS3AndReturnTrue() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

            boolean result = service.delete("path/to/file.txt");

            assertThat(result).isTrue();
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("Should return false on S3 delete error")
        void shouldReturnFalseOnS3DeleteError() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                    .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorMessage("Delete failed")
                        .errorCode("InternalError")
                        .sdkHttpResponse(software.amazon.awssdk.http.SdkHttpResponse.builder()
                            .statusCode(500)
                            .build())
                        .build())
                    .message("Delete failed")
                    .statusCode(500)
                    .build());

            boolean result = service.delete("path/file.txt");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("exists()")
    class Exists {

        @Test
        @DisplayName("Should return true when file exists in S3")
        void shouldReturnTrueWhenFileExistsInS3() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

            assertThat(service.exists("path/file.txt")).isTrue();
        }

        @Test
        @DisplayName("Should return false when file does not exist")
        void shouldReturnFalseWhenFileDoesNotExist() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            assertThat(service.exists("nonexistent.txt")).isFalse();
        }

        @Test
        @DisplayName("Should return false on S3 error")
        void shouldReturnFalseOnS3Error() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                    .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorMessage("Error")
                        .errorCode("InternalError")
                        .sdkHttpResponse(software.amazon.awssdk.http.SdkHttpResponse.builder()
                            .statusCode(500)
                            .build())
                        .build())
                    .message("Error")
                    .statusCode(500)
                    .build());

            assertThat(service.exists("path/file.txt")).isFalse();
        }
    }

    @Nested
    @DisplayName("getPublicUrl()")
    class GetPublicUrl {

        @Test
        @DisplayName("Should use public URL prefix when set")
        void shouldUsePublicUrlPrefixWhenSet() {
            when(s3Config.getPublicUrlPrefix()).thenReturn("https://cdn.example.com");

            String url = service.getPublicUrl("images/photo.jpg");

            assertThat(url).isEqualTo("https://cdn.example.com/images/photo.jpg");
        }

        @Test
        @DisplayName("Should add trailing slash to prefix if missing")
        void shouldAddTrailingSlashToPrefixIfMissing() {
            when(s3Config.getPublicUrlPrefix()).thenReturn("https://cdn.example.com");

            String url = service.getPublicUrl("path/file.txt");

            assertThat(url).isEqualTo("https://cdn.example.com/path/file.txt");
        }

        @Test
        @DisplayName("Should handle prefix ending with slash")
        void shouldHandlePrefixEndingWithSlash() {
            when(s3Config.getPublicUrlPrefix()).thenReturn("https://cdn.example.com/");

            String url = service.getPublicUrl("path/file.txt");

            assertThat(url).isEqualTo("https://cdn.example.com/path/file.txt");
        }

        @Test
        @DisplayName("Should generate presigned URL when public URL prefix is null")
        void shouldGeneratePresignedUrlWhenPublicUrlPrefixIsNull() throws Exception {
            when(s3Config.getPublicUrlPrefix()).thenReturn(null);

            PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
            when(presignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/path/file.txt?X-Amz-Signature=abc"));
            when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

            String url = service.getPublicUrl("path/file.txt");

            assertThat(url).contains("test-bucket");
            assertThat(url).contains("path/file.txt");
        }

        @Test
        @DisplayName("Should generate presigned URL when public URL prefix is empty")
        void shouldGeneratePresignedUrlWhenPublicUrlPrefixIsEmpty() throws Exception {
            when(s3Config.getPublicUrlPrefix()).thenReturn("");

            PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
            when(presignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/path/file.txt?signed=true"));
            when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

            String url = service.getPublicUrl("path/file.txt");

            assertThat(url).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("getThumbnail()")
    class GetThumbnail {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should return cached thumbnail if it exists in S3")
        void shouldReturnCachedThumbnailIfItExistsInS3() throws IOException {
            String storedPath = "images/photo.jpg";
            byte[] thumbnailData = "thumbnail bytes".getBytes();

            // Thumbnail exists
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

            // Return thumbnail data
            ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
            when(responseBytes.asByteArray()).thenReturn(thumbnailData);
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

            Resource result = service.getThumbnail(storedPath, 64, 64);

            assertThat(result).isNotNull();
            assertThat(result.contentLength()).isEqualTo(thumbnailData.length);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should generate and store thumbnail if not cached")
        void shouldGenerateAndStoreThumbnailIfNotCached() throws IOException {
            String storedPath = "images/photo.jpg";
            byte[] imageData = "image bytes".getBytes();
            byte[] thumbnailData = "thumbnail bytes".getBytes();

            // Thumbnail does not exist
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            // Return original image
            ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
            when(responseBytes.asByteArray()).thenReturn(imageData);
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

            // Mock thumbnail generation
            ProcessedImage thumbnail = new ProcessedImage(thumbnailData, "image/jpeg", 64, 64);
            when(imageProcessingService.generateThumbnail(any(InputStream.class), anyInt(), anyInt()))
                .thenReturn(thumbnail);

            // Mock storing thumbnail
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            Resource result = service.getThumbnail(storedPath, 64, 64);

            assertThat(result).isNotNull();
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should verify correct thumbnail PutObjectRequest is created")
        void shouldVerifyCorrectThumbnailPutObjectRequest() throws IOException {
            String storedPath = "images/photo.jpg";
            byte[] imageData = "image bytes".getBytes();
            byte[] thumbnailData = "thumbnail bytes".getBytes();

            // Thumbnail does not exist
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            // Return original image
            ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
            when(responseBytes.asByteArray()).thenReturn(imageData);
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

            // Mock thumbnail generation
            ProcessedImage thumbnail = new ProcessedImage(thumbnailData, "image/jpeg", 128, 128);
            when(imageProcessingService.generateThumbnail(any(InputStream.class), anyInt(), anyInt()))
                .thenReturn(thumbnail);

            // Mock storing thumbnail
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            service.getThumbnail(storedPath, 128, 128);

            // Verify the thumbnail was stored with the correct key
            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
            PutObjectRequest putRequest = captor.getValue();
            assertThat(putRequest.key()).isEqualTo("thumbnails/128x128/images/photo.jpg");
            assertThat(putRequest.contentType()).isEqualTo("image/jpeg");
            assertThat(putRequest.contentLength()).isEqualTo(thumbnailData.length);
        }
    }

    @Nested
    @DisplayName("getThumbnailUrl()")
    class GetThumbnailUrl {

        @Test
        @DisplayName("Should build thumbnail URL using public URL prefix")
        void shouldBuildThumbnailUrlUsingPublicUrlPrefix() {
            when(s3Config.getPublicUrlPrefix()).thenReturn("https://cdn.example.com");

            String url = service.getThumbnailUrl("images/photo.jpg", 128, 128);

            assertThat(url).isEqualTo("https://cdn.example.com/thumbnails/128x128/images/photo.jpg");
        }
    }

    @Nested
    @DisplayName("cleanup()")
    class Cleanup {

        @Test
        @DisplayName("Should close S3 client and presigner on cleanup")
        void shouldCloseS3ClientAndPresignerOnCleanup() {
            service.cleanup();

            verify(s3Client).close();
            verify(s3Presigner).close();
        }

        @Test
        @DisplayName("Should handle null S3 client on cleanup")
        void shouldHandleNullS3ClientOnCleanup() throws Exception {
            Field s3ClientField = S3FileStorageService.class.getDeclaredField("s3Client");
            s3ClientField.setAccessible(true);
            s3ClientField.set(service, null);

            Field s3PresignerField = S3FileStorageService.class.getDeclaredField("s3Presigner");
            s3PresignerField.setAccessible(true);
            s3PresignerField.set(service, null);

            // Should not throw
            service.cleanup();
        }
    }
}
